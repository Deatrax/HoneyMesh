# HoneyMesh PowerShell Verification Script
$PASS = 0
$FAIL = 0

function Check($desc, $result) {
    if ($result) {
        Write-Host "  [PASS] $desc" -ForegroundColor Green
        $global:PASS++
    } else {
        Write-Host "  [FAIL] $desc" -ForegroundColor Red
        $global:FAIL++
    }
}

Write-Host "== 1. Direct service health (Actuator) =="
$services = @("decoy-service:8081", "threat-engine-service:8082", "incident-service:8083", "gateway:8080")
foreach ($svc in $services) {
    $parts = $svc.Split(":")
    $name = $parts[0]
    $port = $parts[1]
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:${port}/actuator/health" -TimeoutSec 5
        Check "$name actuator health (port $port)" ($res.status -eq "UP")
    } catch {
        Check "$name actuator health (port $port)" $false
    }
}

Write-Host "`n== 2. Gateway routing =="
$paths = @("decoy", "threat", "incidents")
foreach ($p in $paths) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/${p}/ping" -TimeoutSec 5
        Check "gateway routes /api/${p}/ping" ($null -ne $res)
    } catch {
        Check "gateway routes /api/${p}/ping" $false
    }
}

Write-Host "`n== 3. Decoy admin CRUD + honeypot catch-all + full event pipeline =="
$decoyPath = "/api/admin/verify-$(Get-Date -UFormat %s)"
try {
    $body = @{ name = "verify script decoy"; endpointPath = $decoyPath; riskLevel = "LOW" } | ConvertTo-Json
    $createRes = Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin" -Method Post -ContentType "application/json" -Body $body
    Check "created a decoy via admin API" ($null -ne $createRes)

    $decoyId = $createRes.id
    Check "decoy id parsed from response (id=$decoyId)" ($null -ne $decoyId)

    $hitRes = try { Invoke-WebRequest -Uri "http://localhost:8080${decoyPath}" -UseBasicParsing } catch { $_.Exception.Response }
    Check "hit the decoy's live path through the gateway catch-all route" ($null -ne $hitRes)


    Start-Sleep -Seconds 2

    $lastEvent = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-event"
    Check "threat-engine consumed the event for decoy $decoyId" ("$($lastEvent.decoyId)" -eq "$decoyId")

    $count = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/count/${decoyId}"
    Check "Redis counter incremented for decoy $decoyId (count=$count)" ($count -ne "0" -and $null -ne $count)

    Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin/${decoyId}" -Method Delete | Out-Null
    Check "cleaned up test decoy" $true
} catch {
    Check "decoy pipeline failed: $_" $false
}

Write-Host "`n== 4. Postgres schemas =="
$schemas = docker exec honeymesh-postgres psql -U honeymesh -d honeymesh -tAc "SELECT schema_name FROM information_schema.schemata;"
foreach ($s in @("decoy", "threat_engine", "incident")) {
    Check "schema '$s' exists" ($schemas -match "^$s`$")
}

Write-Host "`n== 5. Redis reachable =="
$pong = docker exec honeymesh-redis redis-cli ping
Check "redis responds to PING" ($pong.Trim() -eq "PONG")

Write-Host "`n== 6. RabbitMQ management API reachable =="
try {
    $pair = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("honeymesh:honeymesh_dev_pw"))
    $res = Invoke-RestMethod -Uri "http://localhost:15672/api/overview" -Headers @{ Authorization = "Basic $pair" }
    Check "rabbitmq management API reachable" ($null -ne $res)
} catch {
    Check "rabbitmq management API reachable" $false
}

Write-Host "`n-----------------------------------"
Write-Host "Passed: $PASS   Failed: $FAIL"
Write-Host "-----------------------------------"

if ($FAIL -gt 0) { exit 1 }
