<#
.SYNOPSIS
    End-to-end smoke test for a running Monotron backend.

.DESCRIPTION
    Registers a throwaway tenant, uploads a generated PDF, waits for ingestion,
    checks semantic search and grounded AI answers, drives the public widget and
    the agent inbox, then proves that a second tenant cannot read the first
    tenant's data.

.PARAMETER BaseUrl
    API base URL, e.g. http://localhost:8080/api

.PARAMETER Pdf
    Optional path to an existing PDF. When omitted a small handbook PDF is
    generated in the temp directory.

.EXAMPLE
    .\scripts\smoke.ps1
    .\scripts\smoke.ps1 -BaseUrl http://localhost:8080/api -Pdf .\sample.pdf
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080/api',
    [string]$Pdf
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
    param([string]$Method, [string]$Path, [hashtable]$Headers = @{})
    try {
        Invoke-WebRequest -Uri "$BaseUrl$Path" -Method $Method -Headers $Headers -TimeoutSec 60 -UseBasicParsing | Out-Null
        return 200
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        return [int]$_.Exception.Response.StatusCode
    }
}

function New-HandbookPdf([string]$Path) {
    $enc = [System.Text.Encoding]::ASCII
    $objects = @()
    $objects += '<< /Type /Catalog /Pages 2 0 R >>'
    $objects += '<< /Type /Pages /Kids [3 0 R] /Count 1 >>'
    $objects += '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>'
    $lines = @(
        'Monotron Customer Handbook',
        'Account access: open Settings and choose Security to reset your password.',
        'Password reset: open Settings, choose Security, then click Reset Password.',
        'Shipping: standard delivery takes 3 to 5 business days.',
        'Support hours: Monday to Friday, 9am to 6pm CET.',
        'Team collaboration: invite teammates from the workspace settings.'
    )
    $stream = "BT`n/F1 12 Tf`n16 TL`n72 700 Td`n" + (($lines | ForEach-Object { "($_) Tj T*`n" }) -join '') + 'ET'
    $objects += "<< /Length $($enc.GetByteCount($stream)) >>`nstream`n$stream`nendstream"
    $objects += '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'

    $body = "%PDF-1.4`n"
    $offsets = @()
    for ($i = 0; $i -lt $objects.Count; $i++) {
        $offsets += $enc.GetByteCount($body)
        $body += "$($i + 1) 0 obj`n$($objects[$i])`nendobj`n"
    }
    $xref = $enc.GetByteCount($body)
    $body += "xref`n0 $($objects.Count + 1)`n0000000000 65535 f `n"
    foreach ($o in $offsets) { $body += ('{0:D10} 00000 n ' -f $o) + "`n" }
    $body += "trailer`n<< /Size $($objects.Count + 1) /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n"
    [System.IO.File]::WriteAllBytes($Path, $enc.GetBytes($body))
}

# --------------------------------------------------------------------------
# Preconditions
# --------------------------------------------------------------------------

$healthUrl = ($BaseUrl -replace '/api$', '') + '/actuator/health'
try {
    $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 15
    Assert-True ($health.status -eq 'UP') 'health endpoint did not report UP'
    Write-Host "Backend is up: $BaseUrl"
} catch {
    Write-Host "Backend is not reachable at $BaseUrl (start it with .\gradlew.bat bootRun)." -ForegroundColor Red
    exit 1
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = 'Passw0rd!2345'
$email = "smoke$stamp@monotron.test"

if (-not $Pdf) {
    $Pdf = Join-Path ([System.IO.Path]::GetTempPath()) "monotron-smoke-$stamp.pdf"
    New-HandbookPdf $Pdf
}
if (-not (Test-Path $Pdf)) { throw "PDF not found: $Pdf" }

# --------------------------------------------------------------------------
# 1. Auth + tenant bootstrap
# --------------------------------------------------------------------------

$session = $null
Check 'register creates user, workspace, tokens' {
    $script:session = Invoke-Api -Method Post -Path '/auth/register' -Body @{
        email         = $email
        password      = $password
        name          = 'Smoke Tester'
        workspaceName = "Smoke Co $stamp"
    }
    Assert-True ($script:session.accessToken.Length -gt 20) 'missing access token'
    Assert-True ($script:session.activeWorkspace.appId.Length -eq 32) 'missing 32-hex app id'
}

$auth = @{ Authorization = "Bearer $($session.accessToken)" }

Check 'GET /auth/me returns the active workspace' {
    $me = Invoke-Api -Method Get -Path '/auth/me' -Headers $auth
    Assert-True ($me.activeWorkspace.id -eq $session.activeWorkspace.id) 'workspace mismatch'
}

Check 'Google auth config is public' {
    $google = Invoke-Api -Method Get -Path '/auth/google/config'
    Assert-True ($null -ne $google.enabled) 'google config is missing enabled'
    Assert-True (-not $google.enabled -or $google.clientId) 'enabled google config is missing clientId'
}

Check 'dashboard, widget settings and workspace are readable' {
    $dash = Invoke-Api -Method Get -Path '/dashboard' -Headers $auth
    Assert-True ($null -ne $dash.openConversations) 'dashboard payload incomplete'
    $settings = Invoke-Api -Method Get -Path '/widget-settings' -Headers $auth
    Assert-True ($settings.appId -eq $session.activeWorkspace.appId) 'widget settings app mismatch'
    $ws = Invoke-Api -Method Get -Path '/workspace' -Headers $auth
    Assert-True ($null -ne $ws.app.hmacSecret) 'workspace app secret missing'
}

Check 'refresh rotates the token' {
    $refreshed = Invoke-Api -Method Post -Path '/auth/refresh' -Body @{ refreshToken = $session.refreshToken }
    Assert-True ($refreshed.refreshToken -ne $session.refreshToken) 'refresh token was not rotated'
    Assert-True ($refreshed.accessToken.Length -gt 20) 'no new access token'
}

# --------------------------------------------------------------------------
# 2. PDF upload -> ingest -> retrieval -> grounded answer
# --------------------------------------------------------------------------

$document = $null
Check 'PDF upload is accepted' {
    $json = & curl.exe -s -X POST "$BaseUrl/documents" `
        -H "Authorization: Bearer $($session.accessToken)" `
        -F "file=@$Pdf;type=application/pdf" `
        -F 'title=Employee Handbook'
    $script:document = $json | ConvertFrom-Json
    Assert-True ($null -ne $script:document.id) 'no document id returned'
}

Check 'document becomes READY with chunks' {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        $doc = Invoke-Api -Method Get -Path "/documents/$($script:document.id)" -Headers $auth
    } while ($doc.status -in @('PENDING', 'PROCESSING') -and (Get-Date) -lt $deadline)
    Assert-True ($doc.status -eq 'READY') "document ended as $($doc.status): $($doc.error)"
    Assert-True ($doc.chunkCount -ge 1) 'no chunks were produced'
}

Check 'semantic search finds the PDF chunk' {
    $hits = Invoke-Api -Method Post -Path '/search' -Headers $auth -Body @{ query = 'how long does shipping take'; limit = 5 }
    Assert-True ($hits.items.Count -ge 1) 'no search hits'
    Assert-True ($hits.items[0].sourceId -eq $script:document.id) 'unexpected top hit'
}

Check 'AI answer is grounded and cited' {
    $answer = Invoke-Api -Method Post -Path '/ai/answer' -Headers $auth -Body @{ question = 'How do I reset my password?' }
    Assert-True ($answer.answer.Length -gt 20) 'answer too short'
    Assert-True ($answer.citations.Count -ge 1) 'no citations'
    Assert-True ($answer.citations[0].sourceId -eq $script:document.id) 'citation points at the wrong source'
}

# --------------------------------------------------------------------------
# 3. Public widget
# --------------------------------------------------------------------------

$appId = $session.activeWorkspace.appId
$visitor = $null
Check 'widget config and session are public' {
    $config = Invoke-Api -Method Get -Path "/widget/$appId/config"
    Assert-True ($config.appId -eq $appId) 'config app mismatch'
    $script:widgetSession = Invoke-Api -Method Post -Path "/widget/$appId/session" -Body @{ name = 'Ada Lovelace'; email = "ada$stamp@example.com" }
    $script:visitor = $script:widgetSession.visitorId
    Assert-True ($script:visitor.Length -gt 10) 'no visitor id'
}

$conversationId = $null
Check 'widget message gets an AI auto-reply with citations' {
    $reply = Invoke-Api -Method Post -Path "/widget/$appId/messages" -Body @{
        visitorId = $script:visitor
        body      = 'How do I reset my password?'
        name      = 'Ada Lovelace'
    }
    $script:conversationId = $reply.conversationId
    Assert-True ($null -ne $reply.aiReply) 'AI auto-reply was not produced'
    Assert-True ($reply.aiReply.citations.Count -ge 1) 'AI reply has no citations'
}

Check 'widget session returns previous conversations' {
    $again = Invoke-Api -Method Post -Path "/widget/$appId/session" -Body @{ visitorId = $script:visitor }
    Assert-True ($again.conversations.Count -ge 1) 'conversation history is empty'
}

# --------------------------------------------------------------------------
# 4. Agent inbox
# --------------------------------------------------------------------------

Check 'agent sees the conversation and can reply' {
    $list = Invoke-Api -Method Get -Path '/conversations' -Headers $auth
    Assert-True ($list.items.Count -ge 1) 'conversation list is empty'
    $detail = Invoke-Api -Method Get -Path "/conversations/$($script:conversationId)" -Headers $auth
    Assert-True ($detail.messages.Count -ge 2) 'CONTACT + AI messages missing'
    $msg = Invoke-Api -Method Post -Path "/conversations/$($script:conversationId)/reply" -Headers $auth -Body @{ body = 'An agent is here if you need more.' }
    Assert-True ($msg.authorType -eq 'AGENT') 'agent reply not attributed'
}

Check 'agent can ask the AI for a reply' {
    $ai = Invoke-Api -Method Post -Path "/conversations/$($script:conversationId)/ai-reply" -Headers $auth -Body @{ question = 'What are the support hours?' }
    Assert-True ($ai.authorType -eq 'AI') 'AI reply not attributed'
    Assert-True ($ai.citations.Count -ge 1) 'AI reply has no citations'
}

Check 'conversation stats reflect the inbox' {
    $stats = Invoke-Api -Method Get -Path '/conversations/stats' -Headers $auth
    Assert-True ($stats.total -ge 1 -and $stats.open -ge 1) 'stats look empty'
}

Check 'inbox contact can be promoted to a CRM lead' {
    $detail = Invoke-Api -Method Get -Path "/conversations/$($script:conversationId)" -Headers $auth
    Assert-True ($null -eq $detail.contact.crmLeadId) 'contact unexpectedly already linked'

    $created = Invoke-Api -Method Post -Path "/crm/leads/from-contact/$($detail.contact.id)" -Headers $auth
    Assert-True ($created.created -eq $true) 'lead was not created'
    Assert-True ($created.lead.name -eq 'Ada Lovelace') 'lead name was not copied'
    Assert-True ($created.lead.sourceContactId -eq $detail.contact.id) 'lead is not linked to the contact'

    $again = Invoke-Api -Method Post -Path "/crm/leads/from-contact/$($detail.contact.id)" -Headers $auth
    Assert-True ($again.created -eq $false) 'second click created another lead'
    Assert-True ($again.lead.id -eq $created.lead.id) 'second click returned a different lead'

    $linked = Invoke-Api -Method Get -Path "/conversations/$($script:conversationId)" -Headers $auth
    Assert-True ($linked.contact.crmLeadId -eq $created.lead.id) 'conversation does not show the linked lead'
}

# --------------------------------------------------------------------------
# 5. Cross-tenant isolation
# --------------------------------------------------------------------------

Check 'a second tenant cannot read the first tenant data' {
    $other = Invoke-Api -Method Post -Path '/auth/register' -Body @{
        email         = "iso$stamp@monotron.test"
        password      = $password
        name          = 'Isolation Tester'
        workspaceName = "Iso Co $stamp"
    }
    $otherAuth = @{ Authorization = "Bearer $($other.accessToken)" }

    Assert-True ((Get-StatusCode -Method Get -Path "/documents/$($script:document.id)" -Headers $otherAuth) -eq 404) 'foreign document readable'
    Assert-True ((Get-StatusCode -Method Get -Path "/conversations/$($script:conversationId)" -Headers $otherAuth) -eq 404) 'foreign conversation readable'
    Assert-True ((Get-StatusCode -Method Delete -Path "/documents/$($script:document.id)" -Headers $otherAuth) -eq 404) 'foreign document deletable'

    $documents = Invoke-Api -Method Get -Path '/documents' -Headers $otherAuth
    Assert-True ($documents.items.Count -eq 0) 'foreign documents leaked into the list'
}

# --------------------------------------------------------------------------

Write-Host ''
if ($script:failed -gt 0) {
    Write-Host ("{0} passed, {1} failed" -f $script:passed, $script:failed) -ForegroundColor Red
    exit 1
}
Write-Host ("All {0} checks passed. Tenant: {1} (appId {2})" -f $script:passed, $email, $appId) -ForegroundColor Green
exit 0
