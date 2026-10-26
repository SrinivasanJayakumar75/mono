<#
.SYNOPSIS
    End-to-end smoke test for the human-handoff (escalation) flow.

.DESCRIPTION
    Registers a throwaway tenant, opens a widget conversation, and proves the
    rule that keeps the assistant and a live agent from talking over each other:

      * escalating marks the conversation and claims it for the agent;
      * a visitor message sent while escalated is stored but gets no AI reply;
      * the agent can reply during escalation and the visitor's next session
        shows both messages;
      * resuming AI clears the flag and automatic replies start again.

.EXAMPLE
    .\scripts\smoke-handoff.ps1
    .\scripts\smoke-handoff.ps1 -BaseUrl http://localhost:8080/api
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

# --------------------------------------------------------------------------
# Preconditions
# --------------------------------------------------------------------------

$healthUrl = ($BaseUrl -replace '/api$', '') + '/actuator/health'
try {
    $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 15
    Assert-True ($health.status -eq 'UP') 'health endpoint did not report UP'
    Write-Host "Backend is up: $BaseUrl"
} catch {
    Write-Host "Backend is not reachable at $BaseUrl." -ForegroundColor Red
    exit 1
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = 'Passw0rd!2345'

$session = Invoke-Api -Method Post -Path '/auth/register' -Body @{
    email         = "handoff$stamp@monotron.test"
    password      = $password
    name          = 'Handoff Tester'
    workspaceName = "Handoff Co $stamp"
}
$auth = @{ Authorization = "Bearer $($session.accessToken)" }
$appId = $session.activeWorkspace.appId

$widget = Invoke-Api -Method Post -Path "/widget/$appId/session" -Body @{ name = 'Visitor' }
$visitorId = $widget.visitorId

$first = Invoke-Api -Method Post -Path "/widget/$appId/messages" -Body @{
    visitorId = $visitorId
    body      = 'Hello, can a human help me with a refund?'
}
$conversationId = $first.conversationId

Write-Host "`nHuman handoff" -ForegroundColor Cyan

Check 'escalating a conversation marks it and claims the thread' {
    $escalated = Invoke-Api -Method Post -Path "/conversations/$conversationId/escalate" -Headers $auth -Body @{}
    Assert-True ($escalated.escalated -eq $true) 'conversation did not report escalated'
    Assert-True (-not [string]::IsNullOrWhiteSpace($escalated.assigneeId)) 'escalation did not claim the thread'
}

Check 'an escalated conversation skips the AI auto-reply' {
    $second = Invoke-Api -Method Post -Path "/widget/$appId/messages" -Body @{
        visitorId      = $visitorId
        conversationId = $conversationId
        body           = 'Are you there?'
    }
    Assert-True ($null -eq $second.aiReply) 'widget still produced an AI reply while escalated'
}

Check 'a human agent can reply while escalated' {
    $reply = Invoke-Api -Method Post -Path "/conversations/$conversationId/reply" -Headers $auth -Body @{
        body = 'Yes - I am taking this over and will sort the refund.'
    }
    Assert-True ($reply.authorType -eq 'AGENT') 'agent reply was not attributed to the agent'

    $detail = Invoke-Api -Method Get -Path "/conversations/$conversationId" -Headers $auth
    Assert-True ($detail.conversation.escalated -eq $true) 'conversation lost its escalated flag'
    $bodies = $detail.messages | ForEach-Object { $_.body }
    Assert-True ($bodies -contains 'Yes - I am taking this over and will sort the refund.') 'agent message missing'
    Assert-True ($bodies -contains 'Are you there?') 'visitor message sent during escalation missing'
}

Check 'resuming AI clears the flag and restores automatic replies' {
    $resumed = Invoke-Api -Method Post -Path "/conversations/$conversationId/escalate" -Headers $auth -Body @{
        escalated = $false
    }
    Assert-True ($resumed.escalated -eq $false) 'conversation did not resume'

    $third = Invoke-Api -Method Post -Path "/widget/$appId/messages" -Body @{
        visitorId      = $visitorId
        conversationId = $conversationId
        body           = 'Thanks, one more question.'
    }
    Assert-True ($third.message.body -eq 'Thanks, one more question.') 'visitor message not recorded after resume'
    Assert-True ($null -ne $third.aiReply) 'AI did not resume answering after the flag was cleared'
}

# --------------------------------------------------------------------------
# Result
# --------------------------------------------------------------------------

Write-Host ""
if ($script:failed -eq 0) {
    Write-Host ("All {0} handoff checks passed." -f $script:passed) -ForegroundColor Green
    exit 0
}
Write-Host ("{0} passed, {1} failed." -f $script:passed, $script:failed) -ForegroundColor Red
exit 1
