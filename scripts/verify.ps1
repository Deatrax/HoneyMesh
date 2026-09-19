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

function Get-StatusCode($uri, $method = "GET", $headers = @{}, $body = $null) {
    try {
        $params = @{ Uri = $uri; Method = $method; Headers = $headers; TimeoutSec = 5 }
        if ($body) { $params.Body = $body; $params.ContentType = "application/json" }
        $res = Invoke-WebRequest @params
        return $res.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode.value__ }
        return 0
    }
}

Write-Host "== 1. Direct service health (Actuator) =="
$services = @("decoy-service:8081", "threat-engine-service:8082", "incident-service:8083", "gateway:8080")
foreach ($svc in $services) {
    $parts = $svc.Split(":")
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:$($parts[1])/actuator/health" -TimeoutSec 5
        Check "$($parts[0]) actuator health (port $($parts[1]))" ($res.status -eq "UP")
    } catch {
        Check "$($parts[0]) actuator health (port $($parts[1]))" $false
    }
}

Write-Host "`n== 2. Gateway routing =="
foreach ($p in @("decoy", "threat", "incidents")) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/$p/ping" -TimeoutSec 5
        Check "gateway routes /api/$p/ping" ($null -ne $res)
    } catch {
        Check "gateway routes /api/$p/ping" $false
    }
}

Write-Host "`n== 3. Decoy admin CRUD + honeypot catch-all + basic event pipeline =="
$decoyPath = "/api/admin/verify-$(Get-Date -UFormat %s)"
try {
    $decoyAdminLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" `
        -Body (@{ username = "admin"; password = "admin123" } | ConvertTo-Json)
    $decoyAdminToken = $decoyAdminLogin.token
    Check "admin login succeeds (needed for decoy-admin/threat APIs, now token-protected)" ($null -ne $decoyAdminToken)
    $decoyAuthHeader = @{ Authorization = "Bearer $decoyAdminToken" }

    $create = Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin" -Method POST -ContentType "application/json" -Headers $decoyAuthHeader `
        -Body (@{ name = "verify script decoy"; endpointPath = $decoyPath; riskLevel = "LOW" } | ConvertTo-Json)
    Check "created a decoy via admin API" $true
    $decoyId = $create.id
    Check "decoy id parsed from response (id=$decoyId)" ($null -ne $decoyId)

    Invoke-RestMethod -Uri "http://localhost:8080$decoyPath" -TimeoutSec 5 | Out-Null
    Check "hit the decoy's live path through the gateway catch-all route (still public — see decoy-service's SecurityConfig)" $true

    Start-Sleep -Seconds 2
    $lastEvent = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-event" -Headers $decoyAuthHeader
    Check "threat-engine consumed the event for decoy $decoyId" ($lastEvent.decoyId -eq "$decoyId")

    $count = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/count/$decoyId" -Headers $decoyAuthHeader
    Check "Redis counter incremented for decoy $decoyId (count=$count)" ($count -ne "0")

    Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin/$decoyId" -Method DELETE -Headers $decoyAuthHeader | Out-Null
    Check "cleaned up test decoy" $true
} catch {
    Check "section 3 failed with error: $_" $false
}

Write-Host "`n== 4. Postgres schemas =="
$schemas = docker exec honeymesh-postgres psql -U honeymesh -d honeymesh -tAc "SELECT schema_name FROM information_schema.schemata;" 2>$null
foreach ($s in @("decoy", "threat_engine", "incident")) {
    Check "schema '$s' exists" ($schemas -match "^$s$")
}

Write-Host "`n== 5. Redis reachable =="
$pong = docker exec honeymesh-redis redis-cli ping 2>$null
Check "redis responds to PING" ($pong -eq "PONG")

Write-Host "`n== 6. RabbitMQ management API reachable =="
try {
    $cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("honeymesh:honeymesh_dev_pw"))
    Invoke-RestMethod -Uri "http://localhost:15672/api/overview" -Headers @{ Authorization = "Basic $cred" } | Out-Null
    Check "rabbitmq management API reachable" $true
} catch {
    Check "rabbitmq management API reachable" $false
}

Write-Host "`n== 7. Incident Service auth (JWT) =="
$status = Get-StatusCode "http://localhost:8080/api/incidents"
Check "GET /api/incidents without a token returns 401 (got $status)" ($status -eq 401)

try {
    $login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" `
        -Body (@{ username = "analyst"; password = "analyst123" } | ConvertTo-Json)
    Check "analyst can log in and receive a token" $true
    $analystToken = $login.token
    Check "JWT token parsed from login response" ($null -ne $analystToken)

    Invoke-RestMethod -Uri "http://localhost:8080/api/incidents" -Headers @{ Authorization = "Bearer $analystToken" } | Out-Null
    Check "GET /api/incidents with a valid analyst token succeeds" $true
} catch {
    Check "section 7 login/token flow failed with error: $_" $false
}

$assignStatus = Get-StatusCode -uri "http://localhost:8080/api/incidents/999999/assign" -method "PATCH" `
    -headers @{ Authorization = "Bearer $analystToken" } -body (@{ analyst = "analyst"; version = 0 } | ConvertTo-Json)
Check "analyst (non-admin) token is blocked on the admin-only assign endpoint (got $assignStatus)" ($assignStatus -eq 403 -or $assignStatus -eq 401)

Write-Host "`n== 8. Full chain: escalation -> enforcement -> auto-incident =="
Write-Host "   (this is the actual end-to-end path your demo relies on)"
try {
    $adminLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" `
        -Body (@{ username = "admin"; password = "admin123" } | ConvertTo-Json)
    $adminToken = $adminLogin.token
    Check "admin login succeeds (needed to read incidents below)" ($null -ne $adminToken)

    $chainPath = "/api/admin/verify-chain-$(Get-Date -UFormat %s)"
    $chainDecoy = Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin" -Method POST -ContentType "application/json" -Headers @{ Authorization = "Bearer $adminToken" } `
        -Body (@{ name = "verify chain decoy"; endpointPath = $chainPath; riskLevel = "CRITICAL" } | ConvertTo-Json)
    $chainDecoyId = $chainDecoy.id
    Check "created a CRITICAL-risk decoy for the escalation test (id=$chainDecoyId)" ($null -ne $chainDecoyId)

    for ($i = 0; $i -lt 6; $i++) {
        try { Invoke-RestMethod -Uri "http://localhost:8080$chainPath" -TimeoutSec 5 | Out-Null } catch {}
    }
    Check "fired 6 rapid hits at the CRITICAL decoy" $true

    Start-Sleep -Seconds 3

    $assessment = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-assessment-event" -Headers @{ Authorization = "Bearer $adminToken" }
    Check "threat assessment escalated to CRITICAL (score/level reflect the 6 hits)" ($assessment.level -eq "CRITICAL")

    $chainIp = $assessment.sourceIp
    Check "captured the source IP that should now be blocked ($chainIp)" ($null -ne $chainIp)

    $blockStatus = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/blocklist/$chainIp" -Headers @{ Authorization = "Bearer $adminToken" }
    Check "threat-engine confirms $chainIp is blocked" ($blockStatus.blocked -eq $true)

    $enforcedStatus = Get-StatusCode "http://localhost:8080$chainPath"
    Check "a further hit from the blocked IP is actually rejected with 403 (got $enforcedStatus)" ($enforcedStatus -eq 403)

    $incidents = Invoke-RestMethod -Uri "http://localhost:8080/api/incidents" -Headers @{ Authorization = "Bearer $adminToken" }
    Check "an incident was auto-created for $chainIp" (($incidents | Where-Object { $_.sourceIp -eq $chainIp }).Count -gt 0)
} catch {
    Check "section 8 failed with error: $_" $false
}

Write-Host "`n-----------------------------------"
Write-Host "Passed: $PASS   Failed: $FAIL"
Write-Host "-----------------------------------"
Write-Host "Not automated here — check manually:"
Write-Host "  a WebSocket client on ws://localhost:8080/ws/alerts should get a heartbeat"
Write-Host "  every 10s, plus an 'incident.updated' message right after section 8 runs."

if ($FAIL -gt 0) { exit 1 }
