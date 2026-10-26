<#
.SYNOPSIS
    End-to-end smoke test for widget help-center content.

.DESCRIPTION
    Registers a throwaway tenant, creates blogs, FAQs and announcements through
    the settings API, verifies the public widget only sees published items, and
    proves that a second tenant cannot read or modify the first tenant's content.

.EXAMPLE
    .\scripts\smoke-widget-content.ps1
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080/api'
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

function Get-StatusCode {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        $Body
    )
    try {
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
        Invoke-WebRequest @request -UseBasicParsing | Out-Null
        return 200
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        return [int]$_.Exception.Response.StatusCode
    }
}

$healthUrl = ($BaseUrl -replace '/api$', '') + '/actuator/health'
try {
    $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 15
    Assert-True ($health.status -eq 'UP') 'health endpoint did not report UP'
    Write-Host "Backend is up: $BaseUrl"
} catch {
    Write-Host "Backend is not reachable at $BaseUrl" -ForegroundColor Red
    exit 1
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = 'Passw0rd!2345'
$session = Invoke-Api -Method Post -Path '/auth/register' -Body @{
    email         = "content$stamp@monotron.test"
    password      = $password
    name          = 'Content Tester'
    workspaceName = "Content Co $stamp"
}
$auth = @{ Authorization = "Bearer $($session.accessToken)" }
$appId = $session.activeWorkspace.appId

$script:blog = $null
$script:faq = $null
$script:news = $null

Check 'create a blog, FAQ and announcement' {
    $script:blog = Invoke-Api -Method Post -Path '/widget-content' -Headers $auth -Body @{
        kind      = 'BLOG'
        title     = 'Faster support guide'
        summary   = 'A short guide to getting answers quickly.'
        body      = "Start with the help center.`nThen send us a message."
        published = $true
    }
    $script:faq = Invoke-Api -Method Post -Path '/widget-content' -Headers $auth -Body @{
        kind      = 'FAQ'
        title     = 'How do I reset my password?'
        summary   = 'Use the security settings.'
        body      = 'Open Settings, then Security, and choose Reset password.'
        published = $true
    }
    $script:news = Invoke-Api -Method Post -Path '/widget-content' -Headers $auth -Body @{
        kind      = 'ANNOUNCEMENT'
        title     = 'Our new help center is live'
        summary   = 'Browse blogs, FAQs and news from the widget.'
        body      = 'Click the help menu in the widget header.'
        published = $true
    }
    Assert-True ($script:blog.kind -eq 'BLOG') 'blog kind mismatch'
    Assert-True ($script:faq.kind -eq 'FAQ') 'FAQ kind mismatch'
    Assert-True ($script:news.kind -eq 'ANNOUNCEMENT') 'announcement kind mismatch'
}

Check 'settings lists all three items' {
    $items = Invoke-Api -Method Get -Path '/widget-content' -Headers $auth
    Assert-True (@($items).Count -eq 3) "expected 3 items, got $(@($items).Count)"
}

Check 'public widget lists published items in help-center order' {
    $items = Invoke-Api -Method Get -Path "/widget/$appId/content"
    Assert-True (@($items).Count -eq 3) "expected 3 public items, got $(@($items).Count)"
    Assert-True ($items[0].kind -eq 'ANNOUNCEMENT') 'announcement should come first'
    Assert-True ($items[1].kind -eq 'FAQ') 'FAQ should come second'
    Assert-True ($items[2].kind -eq 'BLOG') 'blog should come last'
}

Check 'drafting an item hides it from the public widget' {
    $updated = Invoke-Api -Method Put -Path "/widget-content/$($script:blog.id)" -Headers $auth -Body @{
        kind      = 'BLOG'
        title     = $script:blog.title
        summary   = $script:blog.summary
        body      = $script:blog.body
        published = $false
    }
    Assert-True ($updated.published -eq $false) 'blog was not drafted'
    $public = Invoke-Api -Method Get -Path "/widget/$appId/content"
    Assert-True (@($public).Count -eq 2) "expected 2 public items, got $(@($public).Count)"
    $admin = Invoke-Api -Method Get -Path '/widget-content' -Headers $auth
    Assert-True (@($admin).Count -eq 3) 'draft should remain visible in settings'
}

Check 'deleting an item removes it everywhere' {
    Invoke-Api -Method Delete -Path "/widget-content/$($script:faq.id)" -Headers $auth | Out-Null
    $admin = Invoke-Api -Method Get -Path '/widget-content' -Headers $auth
    Assert-True (@($admin).Count -eq 2) "expected 2 items after delete, got $(@($admin).Count)"
    $public = Invoke-Api -Method Get -Path "/widget/$appId/content"
    Assert-True (@($public).Count -eq 1) "expected 1 public item after delete, got $(@($public).Count)"
}

Check 'invalid content types are rejected' {
    $status = Get-StatusCode -Method Post -Path '/widget-content' -Headers $auth -Body @{
        kind      = 'PAGE'
        title     = 'Nope'
        body      = 'Not a supported type.'
        published = $true
    }
    Assert-True ($status -eq 400) "expected 400, got $status"
}

Check 'unauthenticated settings access is rejected' {
    $status = Get-StatusCode -Method Get -Path '/widget-content'
    Assert-True ($status -eq 401) "expected 401, got $status"
}

Check 'a second tenant cannot manage the first tenant content' {
    $other = Invoke-Api -Method Post -Path '/auth/register' -Body @{
        email         = "content-iso$stamp@monotron.test"
        password      = $password
        name          = 'Content Isolation Tester'
        workspaceName = "Content Iso Co $stamp"
    }
    $otherAuth = @{ Authorization = "Bearer $($other.accessToken)" }

    $otherItems = Invoke-Api -Method Get -Path '/widget-content' -Headers $otherAuth
    Assert-True (@($otherItems).Count -eq 0) 'second tenant can see first tenant content'

    $status = Get-StatusCode -Method Put -Path "/widget-content/$($script:blog.id)" -Headers $otherAuth -Body @{
        kind      = 'BLOG'
        title     = 'Hijacked'
        body      = 'This should never land.'
        published = $true
    }
    Assert-True ($status -eq 404) "expected 404 for foreign content, got $status"
}

Write-Host ""
if ($script:failed -eq 0) {
    Write-Host ("All {0} checks passed." -f $script:passed) -ForegroundColor Green
    exit 0
}

Write-Host ("{0} passed, {1} failed." -f $script:passed, $script:failed) -ForegroundColor Red
exit 1
