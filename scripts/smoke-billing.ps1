<#
.SYNOPSIS
    End-to-end smoke test for Monotron subscription billing and the trial paywall.

.DESCRIPTION
    Registers a throwaway tenant, checks the 3-day trial and ₹399 / ₹3,990
    plans, proves a protected API returns 402 after the trial is expired, and
    confirms auth + billing stay reachable so the workspace can pay. The trial
    is restored in a finally block, even when a check fails.

    The paywall check updates the local billing row through Docker. Pass a
    different container/user/database when your PostgreSQL instance is not the
    standard monotron-db container.

.EXAMPLE
    .\scripts\smoke-billing.ps1

.EXAMPLE
    .\scripts\smoke-billing.ps1 -BaseUrl http://localhost:8080/api -DbContainer monotron-db
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080/api',
    [string]$DbContainer = 'monotron-db',
    [string]$DbUser = 'monotron',
    [string]$DbName = 'monotron'
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

$script:passed = 0
$script:failed = 0

function Check([string]$name, [scriptblock]$body) {
    try {
        & $body
        $script:passed++
        Write-Host ("  PASS  {0}" -f $name) -ForegroundColor Green
    } catch {
        $script:failed++
        Write-Host ("  FAIL  {0}: {1}" -f $name, $_.Exception.Message) -ForegroundColor Red
    }
}

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        $Body
    )
    $request = @{
        Uri        = "$BaseUrl$Path"
        Method     = $Method
        Headers    = $Headers
        TimeoutSec = 60
    }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json'
        $request.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod @request
}

function Invoke-ApiResult {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        $Body
    )
    # Invoke-RestMethod consumes the error stream in PowerShell 5.1, so use
    # HttpWebRequest when the response body matters for an assertion.
    $request = [System.Net.HttpWebRequest]::Create("$BaseUrl$Path")
    $request.Method = $Method
    $request.Timeout = 60000
    $request.ReadWriteTimeout = 60000
    foreach ($key in $Headers.Keys) {
        $request.Headers.Add($key, $Headers[$key])
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 8
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        $request.ContentType = 'application/json'
        $request.ContentLength = $bytes.Length
        $stream = $request.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
    }

    try {
        $response = $request.GetResponse()
        $status = [int]$response.StatusCode
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        $text = $reader.ReadToEnd()
        $reader.Close()
        $response.Close()
    } catch [System.Net.WebException] {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw }
        $status = [int]$response.StatusCode
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        $text = $reader.ReadToEnd()
        $reader.Close()
        $response.Close()
    }

    $parsed = $null
    try {
        if ($text) { $parsed = $text | ConvertFrom-Json }
    } catch {
        $parsed = $text
    }
    return @{ Status = $status; Body = $parsed }
}

function Set-TrialEnd {
    param([string]$WorkspaceId, [string]$Expression)
    $sql = "update billing_subscriptions set trial_ends_at = $Expression where workspace_id = '$WorkspaceId';"
    & docker exec $DbContainer psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c $sql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not update the trial for workspace $WorkspaceId through $DbContainer"
    }
}

# --------------------------------------------------------------------------
# Preconditions
# --------------------------------------------------------------------------

$healthUrl = ($BaseUrl -replace '/api$', '') + '/actuator/health'
$health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 15
Assert-True ($health.status -eq 'UP') 'health endpoint did not report UP'
Write-Host "Backend is up: $BaseUrl"

# --------------------------------------------------------------------------
# Trial and plans
# --------------------------------------------------------------------------

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = 'Passw0rd!2345'

$session = Invoke-Api -Method Post -Path '/auth/register' -Body @{
    email         = "billing$stamp@monotron.test"
    password      = $password
    name          = 'Billing Tester'
    workspaceName = "Billing Co $stamp"
}
$auth = @{ Authorization = "Bearer $($session.accessToken)" }
$workspaceId = $session.activeWorkspace.id
$appId = $session.activeWorkspace.appId

$script:billing = Invoke-Api -Method Get -Path '/billing' -Headers $auth

Write-Host "`nTrial and plans" -ForegroundColor Cyan

Check 'a new workspace starts on a trial' {
    Assert-True ($script:billing.status -eq 'TRIALING') "status was $($script:billing.status)"
    Assert-True ($script:billing.access -eq $true) 'trial did not have access'
}

Check 'the trial is three days' {
    Assert-True ($script:billing.trialDays -eq 3) "trialDays was $($script:billing.trialDays)"
    Assert-True ($script:billing.daysLeft -ge 1) "daysLeft was $($script:billing.daysLeft)"
}

Check 'monthly and yearly prices are correct' {
    $monthly = $script:billing.plans | Where-Object { $_.interval -eq 'MONTHLY' }
    $yearly = $script:billing.plans | Where-Object { $_.interval -eq 'YEARLY' }
    Assert-True ($null -ne $monthly) 'monthly plan is missing'
    Assert-True ($null -ne $yearly) 'yearly plan is missing'
    Assert-True ($monthly.amount -eq 39900) "monthly amount was $($monthly.amount)"
    Assert-True ($yearly.amount -eq 399000) "yearly amount was $($yearly.amount)"
    Assert-True ($monthly.currency -eq 'INR') "currency was $($monthly.currency)"
}

Check 'the workspace admin can manage billing' {
    Assert-True ($script:billing.canManage -eq $true) 'canManage was false'
}

Check 'Razorpay configuration status is reported' {
    Assert-True ($null -ne $script:billing.PSObject.Properties['configured']) 'configured is missing'
    Assert-True ($null -ne $script:billing.PSObject.Properties['webhookConfigured']) 'webhookConfigured is missing'
}

Check 'the trial can use a protected API' {
    $overview = Invoke-Api -Method Get -Path '/crm/overview' -Headers $auth
    Assert-True ($null -ne $overview) 'CRM overview returned no payload'
}

if ($script:billing.configured -eq $false) {
    Check 'unconfigured Razorpay returns a clear setup error' {
        $result = Invoke-ApiResult -Method Post -Path '/billing/checkout' -Headers $auth -Body @{
            interval = 'MONTHLY'
        }
        Assert-True ($result.Status -eq 503) "expected 503, got $($result.Status)"
        Assert-True ($result.Body.message -match 'Razorpay is not configured') 'setup message was missing'
    }
} else {
    Write-Host '  SKIP  live checkout is configured; smoke test does not create a real subscription' -ForegroundColor Yellow
}

# --------------------------------------------------------------------------
# Paywall
# --------------------------------------------------------------------------

$script:trialExpired = $false
try {
    Set-TrialEnd -WorkspaceId $workspaceId -Expression "now() - interval '1 hour'"
    $script:trialExpired = $true

    Write-Host "`nPaywall" -ForegroundColor Cyan

    Check 'an expired trial loses API access with 402' {
        $result = Invoke-ApiResult -Method Get -Path '/crm/overview' -Headers $auth
        Assert-True ($result.Status -eq 402) "expected 402, got $($result.Status)"
        Assert-True ($result.Body.message -match 'trial has ended') 'paywall message was missing'
    }

    Check 'workspace widget settings are paywalled too' {
        $result = Invoke-ApiResult -Method Get -Path '/widget-settings' -Headers $auth
        Assert-True ($result.Status -eq 402) "expected 402, got $($result.Status)"
    }

    Check 'widget help-center content is paywalled too' {
        $result = Invoke-ApiResult -Method Get -Path '/widget-content' -Headers $auth
        Assert-True ($result.Status -eq 402) "expected 402, got $($result.Status)"
    }

    Check 'the public widget stays reachable after the trial expires' {
        $config = Invoke-Api -Method Get -Path "/widget/$appId/config"
        Assert-True ($config.appId -eq $appId) 'public widget config was not returned'
    }

    Check 'billing stays reachable after the trial expires' {
        $expired = Invoke-Api -Method Get -Path '/billing' -Headers $auth
        Assert-True ($expired.status -eq 'EXPIRED') "status was $($expired.status)"
        Assert-True ($expired.access -eq $false) 'expired billing still had access'
    }

    Check 'authentication stays reachable after the trial expires' {
        $me = Invoke-Api -Method Get -Path '/auth/me' -Headers $auth
        Assert-True ($me.user.email -eq $session.user.email) 'auth/me did not return the signed-in user'
    }
} finally {
    if ($script:trialExpired) {
        Set-TrialEnd -WorkspaceId $workspaceId -Expression "now() + interval '3 days'"
    }
}

Check 'restoring the trial restores API access' {
    $restored = Invoke-Api -Method Get -Path '/billing' -Headers $auth
    Assert-True ($restored.status -eq 'TRIALING') "status was $($restored.status)"
    Assert-True ($restored.access -eq $true) 'restored trial did not have access'
    $overview = Invoke-Api -Method Get -Path '/crm/overview' -Headers $auth
    Assert-True ($null -ne $overview) 'CRM overview did not recover'
}

# --------------------------------------------------------------------------
# Result
# --------------------------------------------------------------------------

Write-Host ""
if ($script:failed -eq 0) {
    Write-Host ("All {0} billing checks passed." -f $script:passed) -ForegroundColor Green
    exit 0
}
Write-Host ("{0} passed, {1} failed." -f $script:passed, $script:failed) -ForegroundColor Red
exit 1
