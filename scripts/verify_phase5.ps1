# HoneyMesh Phase 5 Integration & Regression Verification Script
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

Write-Host "=========================================================="
Write-Host "  HoneyMesh Phase 5 End-to-End Threat Engine Verification "
Write-Host "=========================================================="

# 1. Setup Test Decoys
$simIp = "203.0.113.88"
$lowPath = "/api/v5/low-trap"
$medPath = "/api/v5/med-trap"
$critPath = "/api/v5/crit-trap"

Write-Host "`n== Step 1: Seeding Test Decoys via Admin API =="
try {
    $decoyLow = Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin" -Method Post -ContentType "application/json" -Body (@{ name="v5 low trap"; endpointPath=$lowPath; riskLevel="LOW" } | ConvertTo-Json)
    $decoyMed = Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin" -Method Post -ContentType "application/json" -Body (@{ name="v5 med trap"; endpointPath=$medPath; riskLevel="MEDIUM" } | ConvertTo-Json)
    $decoyCrit = Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin" -Method Post -ContentType "application/json" -Body (@{ name="v5 crit trap"; endpointPath=$critPath; riskLevel="CRITICAL" } | ConvertTo-Json)

    Check "Created LOW decoy (id=$($decoyLow.id))" ($null -ne $decoyLow.id)
    Check "Created MEDIUM decoy (id=$($decoyMed.id))" ($null -ne $decoyMed.id)
    Check "Created CRITICAL decoy (id=$($decoyCrit.id))" ($null -ne $decoyCrit.id)

    # Clean previous Redis test correlation & block keys for $simIp
    docker exec honeymesh-redis redis-cli del "honeymesh:correlation:source:${simIp}" "honeymesh:block:${simIp}" | Out-Null

    # 2. Test INFORMATIONAL assessment (1 hit on LOW decoy)
    Write-Host "`n== Step 2: Testing INFORMATIONAL Level & Telemetry Event =="
    Invoke-WebRequest -Uri "http://localhost:8080${lowPath}" -Headers @{ "X-Forwarded-For" = $simIp } -UseBasicParsing | Out-Null
    Start-Sleep -Milliseconds 800

    $lastEvt = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-event"
    Check "Threat Engine received TelemetryEvent for decoy $($decoyLow.id)" ($lastEvt.decoyId -eq "$($decoyLow.id)")

    $lastAss = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-assessment"
    Check "Threat Assessment score is 15 (INFORMATIONAL)" ($lastAss.score -eq 15 -and $lastAss.level -eq "INFORMATIONAL")

    $lastEvtMsg = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-assessment-event"
    Check "ThreatAssessmentEvent published with blocked=false" ($lastEvtMsg.blocked -eq $false -and $null -eq $lastEvtMsg.blockExpiresAt)

    $blockStatus = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/blocklist/${simIp}"
    Check "No Redis block key created for INFORMATIONAL level" ($blockStatus.blocked -eq $false)

    # 3. Test Score Progression (Hits on MEDIUM decoy)
    Write-Host "`n== Step 3: Testing Progression (Frequency & Diversity Bonuses) =="
    Invoke-WebRequest -Uri "http://localhost:8080${medPath}" -Headers @{ "X-Forwarded-For" = $simIp } -UseBasicParsing | Out-Null
    Invoke-WebRequest -Uri "http://localhost:8080${medPath}" -Headers @{ "X-Forwarded-For" = $simIp } -UseBasicParsing | Out-Null
    Start-Sleep -Milliseconds 800

    $lastAss2 = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-assessment"
    # Medium risk (+30) + 3 hits (+10) + 2 distinct decoys (+10) = 50 (HIGH)
    Check "Score progressed to $($lastAss2.score) ($($lastAss2.level))" ($lastAss2.score -ge 50 -and $lastAss2.level -eq "HIGH")
    Check "Reasons list contains explainable strings" ($lastAss2.reasons.Count -ge 2)

    # 4. Test CRITICAL Level & Blocklisting
    Write-Host "`n== Step 4: Testing CRITICAL Level & Temporary Block Creation =="
    Invoke-WebRequest -Uri "http://localhost:8080${critPath}" -Headers @{ "X-Forwarded-For" = $simIp } -UseBasicParsing | Out-Null
    Start-Sleep -Milliseconds 800

    $lastAss3 = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-assessment"
    Check "Score hit $($lastAss3.score) (CRITICAL)" ($lastAss3.score -ge 75 -and $lastAss3.level -eq "CRITICAL")

    $blockStatusCrit = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/blocklist/${simIp}"
    Check "Redis block created for ${simIp} (blocked=true)" ($blockStatusCrit.blocked -eq $true)
    Check "Redis block TTL is valid (~300 seconds)" ($blockStatusCrit.ttlSeconds -gt 280 -and $blockStatusCrit.ttlSeconds -le 300)

    $lastEvtMsgCrit = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-assessment-event"
    Check "ThreatAssessmentEvent published with blocked=true & blockExpiresAt" ($lastEvtMsgCrit.blocked -eq $true -and $null -ne $lastEvtMsgCrit.blockExpiresAt)

    # 5. RabbitMQ Queue Check
    Write-Host "`n== Step 5: Verifying RabbitMQ incident-service Queue =="
    $queueCheck = docker exec honeymesh-rabbitmq rabbitmqctl list_queues name messages | Out-String
    Check "Queue 'incident-service.threat-assessment-queue' exists in RabbitMQ" ($queueCheck -match "incident-service.threat-assessment-queue")

    # 6. Cleanup Test Decoys
    Write-Host "`n== Step 6: Cleaning Up Test Decoys =="
    Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin/$($decoyLow.id)" -Method Delete | Out-Null
    Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin/$($decoyMed.id)" -Method Delete | Out-Null
    Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin/$($decoyCrit.id)" -Method Delete | Out-Null
    Check "Cleaned up all 3 test decoys" $true

} catch {
    Check "Phase 5 verification failed with error: $_" $false
}

Write-Host "`n----------------------------------------------------------"
Write-Host "Passed: $PASS   Failed: $FAIL"
Write-Host "----------------------------------------------------------"

if ($FAIL -gt 0) { exit 1 }
