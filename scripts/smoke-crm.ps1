<#
.SYNOPSIS
    End-to-end smoke test for the Monotron CRM backend.

.DESCRIPTION
    Registers a throwaway tenant, exercises every CRM module (leads, deals,
    companies, contacts, tasks, calls, meetings, products, orders + line items,
    documents), checks the overview aggregates, verifies search, and finally
    proves that a second tenant cannot read the first tenant's CRM records.

.EXAMPLE
    .\scripts\smoke-crm.ps1
    .\scripts\smoke-crm.ps1 -BaseUrl http://localhost:8080/api
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
    param([string]$Method, [string]$Path, [hashtable]$Headers = @{})
    try {
        Invoke-WebRequest -Uri "$BaseUrl$Path" -Method $Method -Headers $Headers -TimeoutSec 60 -UseBasicParsing | Out-Null
        return 200
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        return [int]$_.Exception.Response.StatusCode
    }
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
    email         = "crm$stamp@monotron.test"
    password      = $password
    name          = 'CRM Tester'
    workspaceName = "CRM Co $stamp"
}
$auth = @{ Authorization = "Bearer $($session.accessToken)" }

Write-Host "`nCRM modules" -ForegroundColor Cyan

$company = $null
Check 'company is created and searchable' {
    $script:company = Invoke-Api -Method Post -Path '/crm/companies' -Headers $auth -Body @{
        name     = 'Acme Corporation'
        domain   = 'acme.test'
        industry = 'SaaS'
        size     = '51-200'
    }
    Assert-True ($script:company.id.Length -eq 36) 'company id missing'
    $found = Invoke-Api -Method Get -Path '/crm/companies?q=acme' -Headers $auth
    Assert-True ($found.total -eq 1) "expected 1 company, got $($found.total)"
}

$lead = $null
Check 'lead is created, linked to the company and updatable' {
    $script:lead = Invoke-Api -Method Post -Path '/crm/leads' -Headers $auth -Body @{
        name                = 'Priya Sharma'
        email               = 'priya@acme.test'
        phone               = '+91 90000 00000'
        companyId           = $company.id
        companyText         = 'Acme Corporation'
        source              = 'WEBSITE'
        status              = 'NEW'
        priority            = 'HIGH'
        expectedPurchaseDate = '2026-12-31'
    }
    Assert-True ($script:lead.companyName -eq 'Acme Corporation') 'company name was not joined'

    $updated = Invoke-Api -Method Patch -Path "/crm/leads/$($script:lead.id)" -Headers $auth -Body @{
        status = 'QUALIFIED'
    }
    Assert-True ($updated.status -eq 'QUALIFIED') 'lead status was not updated'

    $searched = Invoke-Api -Method Get -Path '/crm/leads?q=priya' -Headers $auth
    Assert-True ($searched.total -eq 1) "expected 1 lead, got $($searched.total)"
}

$deal = $null
Check 'deal is created with company and lead links' {
    $script:deal = Invoke-Api -Method Post -Path '/crm/deals' -Headers $auth -Body @{
        title             = 'Acme annual plan'
        valueAmount       = 250000
        stage             = 'QUALIFIED'
        companyId         = $company.id
        leadId            = $lead.id
        expectedCloseDate = '2026-12-15'
    }
    Assert-True ($script:deal.leadName -eq 'Priya Sharma') 'lead name was not joined'
}

Check 'contact is created' {
    $contact = Invoke-Api -Method Post -Path '/crm/contacts' -Headers $auth -Body @{
        firstName = 'Priya'
        lastName  = 'Sharma'
        email     = 'priya@acme.test'
        title     = 'Head of Operations'
        companyId = $company.id
        status    = 'ACTIVE'
    }
    Assert-True ($contact.companyName -eq 'Acme Corporation') 'contact company was not joined'
}

Check 'task, call and meeting are created' {
    $task = Invoke-Api -Method Post -Path '/crm/tasks' -Headers $auth -Body @{
        title       = 'Send the proposal'
        dueDate     = '2026-10-30'
        priority    = 'HIGH'
        status      = 'OPEN'
        relatedType = 'LEAD'
        relatedId   = $lead.id
    }
    Assert-True ($task.id.Length -eq 36) 'task id missing'

    $call = Invoke-Api -Method Post -Path '/crm/calls' -Headers $auth -Body @{
        subject         = 'Discovery call'
        direction       = 'OUTBOUND'
        outcome         = 'CONNECTED'
        durationSeconds = 600
        occurredAt      = '2026-10-24T10:30'
        relatedType     = 'LEAD'
        relatedId       = $lead.id
    }
    Assert-True ($call.outcome -eq 'CONNECTED') 'call outcome missing'

    $meeting = Invoke-Api -Method Post -Path '/crm/meetings' -Headers $auth -Body @{
        title           = 'Product demo'
        startsAt        = '2026-12-20T15:00'
        durationMinutes = 45
        location        = 'Google Meet'
        status          = 'SCHEDULED'
        relatedType     = 'DEAL'
        relatedId       = $deal.id
    }
    Assert-True ($meeting.durationMinutes -eq 45) 'meeting duration missing'
}

$productA = $null
$order = $null
Check 'order totals are computed from line items' {
    $script:productA = Invoke-Api -Method Post -Path '/crm/products' -Headers $auth -Body @{
        name  = 'Starter plan'
        sku   = 'ST-1'
        price = 4999
        active = $true
    }
    $productB = Invoke-Api -Method Post -Path '/crm/products' -Headers $auth -Body @{
        name  = 'Priority support'
        sku   = 'PS-1'
        price = 1999
        active = $true
    }

    $script:order = Invoke-Api -Method Post -Path '/crm/orders' -Headers $auth -Body @{
        orderNumber = "ORD-$stamp"
        status      = 'PENDING'
        orderDate   = '2026-10-24'
        companyId   = $company.id
        leadId      = $lead.id
    }

    Invoke-Api -Method Post -Path "/crm/orders/$($script:order.id)/items" -Headers $auth -Body @{
        productId = $script:productA.id
        quantity  = 2
    } | Out-Null
    Invoke-Api -Method Post -Path "/crm/orders/$($script:order.id)/items" -Headers $auth -Body @{
        productId = $productB.id
        quantity  = 1
    } | Out-Null

    $items = Invoke-Api -Method Get -Path "/crm/orders/$($script:order.id)/items" -Headers $auth
    Assert-True ($items.Count -eq 2) "expected 2 line items, got $($items.Count)"

    $orders = Invoke-Api -Method Get -Path '/crm/orders' -Headers $auth
    $row = $orders.items | Where-Object { $_.id -eq $script:order.id }
    Assert-True ($null -ne $row) 'order missing from list'
    Assert-True ([decimal]$row.itemCount -eq 2) "expected itemCount 2, got $($row.itemCount)"
    Assert-True ([decimal]$row.totalAmount -eq 11997) "expected total 11997, got $($row.totalAmount)"

    $items[0] | Out-Null
    Invoke-Api -Method Delete -Path "/crm/orders/$($script:order.id)/items/$($items[1].id)" -Headers $auth | Out-Null
    $remaining = Invoke-Api -Method Get -Path "/crm/orders/$($script:order.id)/items" -Headers $auth
    Assert-True ($remaining.Count -eq 1) 'line item was not deleted'
}

Check 'document is created' {
    $document = Invoke-Api -Method Post -Path '/crm/documents' -Headers $auth -Body @{
        name        = 'Acme signed contract'
        category    = 'CONTRACT'
        url         = 'https://example.test/contract.pdf'
        relatedType = 'LEAD'
        relatedId   = $lead.id
    }
    Assert-True ($document.id.Length -eq 36) 'document id missing'
}

Check 'lead profile aggregates every linked record' {
    $profile = Invoke-Api -Method Get -Path "/crm/leads/$($lead.id)/profile" -Headers $auth
    Assert-True ($profile.lead.id -eq $lead.id) 'profile lead mismatch'
    Assert-True ($profile.deals.Count -eq 1) "expected 1 linked deal, got $($profile.deals.Count)"
    Assert-True ($profile.orders.Count -eq 1) "expected 1 linked order, got $($profile.orders.Count)"
    Assert-True ($profile.tasks.Count -eq 1) "expected 1 linked task, got $($profile.tasks.Count)"
    Assert-True ($profile.calls.Count -eq 1) "expected 1 linked call, got $($profile.calls.Count)"
    Assert-True ($profile.meetings.Count -eq 0) "expected no lead-linked meetings, got $($profile.meetings.Count)"
    Assert-True ($profile.documents.Count -eq 1) "expected 1 linked document, got $($profile.documents.Count)"
    Assert-True ([decimal]$profile.orders[0].totalAmount -eq 9998) 'profile order total incorrect'
}

Check 'CSV rows import in bulk and resolve company names' {
    $import = Invoke-Api -Method Post -Path '/crm/leads/import' -Headers $auth -Body @{
        rows = @(
            @{
                name      = 'Import One'
                email     = 'one@import.test'
                companyId = 'Acme Corporation'
                status    = 'QUALIFIED'
                leadScore = '42'
            },
            @{
                name      = 'Import Two'
                email     = 'two@import.test'
                companyId = $company.id
                status    = 'NEW'
            }
        )
    }
    Assert-True ($import.imported -eq 2) "expected 2 imported leads, got $($import.imported)"
    Assert-True ($import.total -eq 2) 'import total mismatch'
    Assert-True ($import.errors.Count -eq 0) "expected no import errors, got $($import.errors.Count)"

    $imported = Invoke-Api -Method Get -Path '/crm/leads?q=Import' -Headers $auth
    Assert-True ($imported.total -eq 2) "expected 2 imported leads, got $($imported.total)"
    $one = $imported.items | Where-Object { $_.name -eq 'Import One' }
    Assert-True ($one.companyName -eq 'Acme Corporation') 'company name was not resolved on import'
    Assert-True ([int]$one.leadScore -eq 42) 'lead score was not coerced'
}

Check 'a failed import is atomic and reports the spreadsheet row' {
    $before = (Invoke-Api -Method Get -Path '/crm/leads?q=Should Not Exist' -Headers $auth).total
    $import = Invoke-Api -Method Post -Path '/crm/leads/import' -Headers $auth -Body @{
        rows = @(
            @{ name = 'Should Not Exist'; email = 'nope@import.test' },
            @{ email = 'missing@import.test' }
        )
    }
    Assert-True ($import.imported -eq 0) "expected 0 imported, got $($import.imported)"
    Assert-True ($import.errors.Count -eq 1) "expected 1 import error, got $($import.errors.Count)"
    Assert-True ([int]$import.errors[0].row -eq 3) "expected row 3, got $($import.errors[0].row)"
    Assert-True ($import.errors[0].message -match 'name is required') 'missing required error text'

    $after = (Invoke-Api -Method Get -Path '/crm/leads?q=Should Not Exist' -Headers $auth).total
    Assert-True ($after -eq $before) 'the valid row was imported despite a failed file'
}

Check 'related record names resolve on import' {
    $taskImport = Invoke-Api -Method Post -Path '/crm/tasks/import' -Headers $auth -Body @{
        rows = @(
            @{
                title       = 'Imported follow-up'
                relatedType = 'LEAD'
                relatedId   = 'Priya Sharma'
                status      = 'OPEN'
            }
        )
    }
    Assert-True ($taskImport.imported -eq 1) "expected 1 imported task, got $($taskImport.imported)"

    $tasks = Invoke-Api -Method Get -Path '/crm/tasks?q=Imported follow-up' -Headers $auth
    Assert-True ($tasks.total -eq 1) "expected 1 imported task, got $($tasks.total)"
    Assert-True ($tasks.items[0].relatedId -eq $lead.id) 'related lead name was not resolved'
    Invoke-Api -Method Delete -Path "/crm/tasks/$($tasks.items[0].id)" -Headers $auth | Out-Null
}

Check 'filters narrow lists, combine with search and validate values' {
    $qualified = Invoke-Api -Method Get -Path '/crm/leads?filter.status=QUALIFIED' -Headers $auth
    Assert-True ($qualified.total -ge 1) "expected qualified leads, got $($qualified.total)"
    $wrongStatus = @($qualified.items | Where-Object { $_.status -ne 'QUALIFIED' })
    Assert-True ($wrongStatus.Count -eq 0) 'status filter returned another status'

    $lowercase = Invoke-Api -Method Get -Path '/crm/leads?filter.status=qualified' -Headers $auth
    Assert-True ($lowercase.total -eq $qualified.total) 'text filters should be case-insensitive'

    $combined = Invoke-Api -Method Get -Path '/crm/leads?q=Priya&filter.status=QUALIFIED' -Headers $auth
    Assert-True ($combined.total -eq 1) "expected 1 combined result, got $($combined.total)"

    $dealFilter = Invoke-Api -Method Get -Path "/crm/deals?filter.companyId=$($company.id)" -Headers $auth
    Assert-True ($dealFilter.total -eq 1) "expected 1 filtered deal, got $($dealFilter.total)"
    Assert-True ($dealFilter.items[0].companyName -eq 'Acme Corporation') 'filtered deal lost its company name'

    $activeProducts = Invoke-Api -Method Get -Path '/crm/products?filter.active=true' -Headers $auth
    Assert-True ($activeProducts.total -eq 2) "expected 2 active products, got $($activeProducts.total)"

    $unknown = Get-StatusCode -Method Get -Path '/crm/leads?filter.notAField=value' -Headers $auth
    Assert-True ($unknown -eq 400) "expected 400 for an unknown filter, got $unknown"

    $invalid = Get-StatusCode -Method Get -Path '/crm/products?filter.active=maybe' -Headers $auth
    Assert-True ($invalid -eq 400) "expected 400 for an invalid boolean filter, got $invalid"
}

Check 'overview aggregates every module' {
    $overview = Invoke-Api -Method Get -Path '/crm/overview' -Headers $auth
    Assert-True ($overview.leads -ge 1) 'lead count missing'
    Assert-True ($overview.deals -ge 1) 'deal count missing'
    Assert-True ($overview.companies -ge 1) 'company count missing'
    Assert-True ($overview.products -ge 2) 'product count missing'
    Assert-True ($overview.orders -ge 1) 'order count missing'
    Assert-True ([decimal]$overview.pipelineValue -ge 250000) 'pipeline value incorrect'
    Assert-True ([decimal]$overview.orderValue -eq 9998) "order value should be 9998, got $($overview.orderValue)"
}

# --------------------------------------------------------------------------
# Tenant isolation
# --------------------------------------------------------------------------

Write-Host "`nTenant isolation" -ForegroundColor Cyan

$other = Invoke-Api -Method Post -Path '/auth/register' -Body @{
    email         = "crmother$stamp@monotron.test"
    password      = $password
    name          = 'Other Tenant'
    workspaceName = "Other Co $stamp"
}
$otherAuth = @{ Authorization = "Bearer $($other.accessToken)" }

Check "another tenant cannot read or write the first tenant's CRM data" {
    $list = Invoke-Api -Method Get -Path '/crm/leads' -Headers $otherAuth
    Assert-True ($list.total -eq 0) "expected an empty list, got $($list.total)"

    $contacts = Invoke-Api -Method Get -Path '/crm/contacts' -Headers $otherAuth
    Assert-True ($contacts.total -eq 0) "expected no contacts, got $($contacts.total)"

    $documents = Invoke-Api -Method Get -Path '/crm/documents' -Headers $otherAuth
    Assert-True ($documents.total -eq 0) "expected no documents, got $($documents.total)"

    $status = Get-StatusCode -Method Get -Path "/crm/leads/$($lead.id)" -Headers $otherAuth
    Assert-True ($status -eq 404) "expected 404 for a foreign lead, got $status"

    $status = Get-StatusCode -Method Get -Path "/crm/leads/$($lead.id)/profile" -Headers $otherAuth
    Assert-True ($status -eq 404) "expected 404 for a foreign lead profile, got $status"

    $status = Get-StatusCode -Method Delete -Path "/crm/companies/$($company.id)" -Headers $otherAuth
    Assert-True ($status -eq 404) "expected 404 deleting a foreign company, got $status"

    $foreignCompany = Invoke-Api -Method Post -Path '/crm/companies' -Headers $otherAuth -Body @{
        name = 'Foreign Co'
    }
    $crossImport = Invoke-Api -Method Post -Path '/crm/leads/import' -Headers $auth -Body @{
        rows = @(
            @{ name = 'Cross tenant lead'; companyId = $foreignCompany.id }
        )
    }
    Assert-True ($crossImport.imported -eq 0) 'cross-tenant import was accepted'
    Assert-True ($crossImport.errors.Count -eq 1) 'expected a cross-tenant import error'
    Assert-True ($crossImport.errors[0].message -match 'does not match') 'unexpected cross-tenant import error'
}

# --------------------------------------------------------------------------
# Cleanup (inside the owning tenant)
# --------------------------------------------------------------------------

Check 'records can be deleted' {
    $tasks = Invoke-Api -Method Get -Path '/crm/tasks' -Headers $auth
    $task = $tasks.items | Select-Object -First 1
    if ($task) {
        Invoke-Api -Method Delete -Path "/crm/tasks/$($task.id)" -Headers $auth | Out-Null
        $after = Invoke-Api -Method Get -Path '/crm/tasks' -Headers $auth
        Assert-True ($after.total -eq 0) 'task was not deleted'
    }
}

# --------------------------------------------------------------------------
# Result
# --------------------------------------------------------------------------

Write-Host ""
if ($script:failed -eq 0) {
    Write-Host ("All {0} CRM checks passed." -f $script:passed) -ForegroundColor Green
    exit 0
}
Write-Host ("{0} passed, {1} failed." -f $script:passed, $script:failed) -ForegroundColor Red
exit 1
