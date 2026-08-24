# HoneyMesh Phase 6 REST APIs Verification Script
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
Write-Host "  HoneyMesh Phase 6 Threat Engine REST API Verification  "
Write-Host "=========================================================="

# Wait for threat-engine-service to be healthy
$healthy = $false
for ($i = 0; $i -lt 15; $i++) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/ping" -ErrorAction Stop
        if ($res -eq "threat-engine-service is up") {
            $healthy = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 1
    }
}

if (-not $healthy) {
    Write-Host "threat-engine-service is not responding!" -ForegroundColor Red
    exit 1
}

$simIp = "203.0.113.99"
$lowPath = "/api/v6/low-trap"
$critPath = "/api/v6/crit-trap"

try {
    # Step 1: Clear Redis recent history & test IP state
    Write-Host "`n== Step 1: Clearing Previous Redis History & IP State =="
    docker exec honeymesh-redis redis-cli del "honeymesh:threat:recent-assessments" "honeymesh:correlation:source:${simIp}" "honeymesh:block:${simIp}" | Out-Null
    Check "Cleared Redis history and correlation state" $true

    # Step 2: Seed Decoys (clean pre-existing if any)
    Write-Host "`n== Step 2: Seeding Test Decoys via Admin API =="
    $allDecoys = Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin"
    foreach ($d in $allDecoys) {
        if ($d.endpointPath -eq $lowPath -or $d.endpointPath -eq $critPath) {
            Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin/$($d.id)" -Method Delete | Out-Null
        }
    }

    $decoyLow = Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin" -Method Post -ContentType "application/json" -Body (@{ name="v6 low trap"; endpointPath=$lowPath; riskLevel="LOW" } | ConvertTo-Json)
    $decoyCrit = Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin" -Method Post -ContentType "application/json" -Body (@{ name="v6 crit trap"; endpointPath=$critPath; riskLevel="CRITICAL" } | ConvertTo-Json)
    Check "Created test decoys (LOW id=$($decoyLow.id), CRITICAL id=$($decoyCrit.id))" ($null -ne $decoyLow.id -and $null -ne $decoyCrit.id)

    # Step 3: Trigger Decoy Hit & Test GET /api/threat/last-assessment
    Write-Host "`n== Step 3: Testing GET /api/threat/last-assessment =="
    Invoke-WebRequest -Uri "http://localhost:8080${lowPath}" -Headers @{ "X-Forwarded-For" = $simIp } -UseBasicParsing | Out-Null
    Start-Sleep -Milliseconds 800

    $lastAss = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/last-assessment"
    Check "GET /api/threat/last-assessment returns latest assessment JSON" ($null -ne $lastAss -and $lastAss.sourceIp -eq $simIp)
    Check "Assessment fields populated (score=$($lastAss.score), level=$($lastAss.level))" ($lastAss.score -eq 15 -and $lastAss.level -eq "INFORMATIONAL")

    # Step 4: Test GET /api/threat/recent-assessments
    Write-Host "`n== Step 4: Testing GET /api/threat/recent-assessments =="
    Invoke-WebRequest -Uri "http://localhost:8080${lowPath}" -Headers @{ "X-Forwarded-For" = $simIp } -UseBasicParsing | Out-Null
    Start-Sleep -Milliseconds 800

    $recentList = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/recent-assessments"
    Check "GET /api/threat/recent-assessments returns list of assessments" ($recentList.Count -ge 2)
    Check "Assessments are ordered newest first" ($recentList[0].assessedAt -ge $recentList[1].assessedAt)

    # Step 5: Test Bounded History (max 50 limit)
    Write-Host "`n== Step 5: Verifying Bounded Redis Recent-History List (Max 50) =="
    $redisListLen = docker exec honeymesh-redis redis-cli llen "honeymesh:threat:recent-assessments" | Out-String
    $len = [int]($redisListLen.Trim())
    Check "Redis list length is bounded (length = $len <= 50)" ($len -le 50)

    # Step 6: Test GET /api/threat/source/{sourceIp}
    Write-Host "`n== Step 6: Testing GET /api/threat/source/{sourceIp} =="
    $srcCorrelation = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/source/${simIp}"
    Check "Source correlation matches rolling window (recentHitCount=$($srcCorrelation.recentHitCount))" ($srcCorrelation.sourceIp -eq $simIp -and $srcCorrelation.recentHitCount -eq 2)
    Check "Source correlation reports blocked status" ($srcCorrelation.blocked -eq $false)

    # Step 7: Test GET /api/threat/block/{sourceIp} for non-blocked & blocked IP
    Write-Host "`n== Step 7: Testing GET /api/threat/block/{sourceIp} & TTL Behavior =="
    $unblockedStatus = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/block/${simIp}"
    Check "Non-blocked IP returns blocked=false" ($unblockedStatus.blocked -eq $false -and $unblockedStatus.remainingTtlSeconds -eq 0)

    # Trigger CRITICAL hit
    Invoke-WebRequest -Uri "http://localhost:8080${critPath}" -Headers @{ "X-Forwarded-For" = $simIp } -UseBasicParsing | Out-Null
    Start-Sleep -Milliseconds 800

    $blockedStatus1 = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/block/${simIp}"
    Check "CRITICAL hit produces blocked=true" ($blockedStatus1.blocked -eq $true)
    Check "Remaining TTL is ~300 seconds (actual=$($blockedStatus1.remainingTtlSeconds))" ($blockedStatus1.remainingTtlSeconds -gt 280 -and $blockedStatus1.remainingTtlSeconds -le 300)

    # Sleep 3 seconds and verify TTL decreases
    Start-Sleep -Seconds 3
    $blockedStatus2 = Invoke-RestMethod -Uri "http://localhost:8080/api/threat/block/${simIp}"
    Check "TTL decreased over time (ttl1=$($blockedStatus1.remainingTtlSeconds) > ttl2=$($blockedStatus2.remainingTtlSeconds))" ($blockedStatus1.remainingTtlSeconds -gt $blockedStatus2.remainingTtlSeconds)

    # Step 8: Cleanup Test Decoys
    Write-Host "`n== Step 8: Cleaning Up Test Decoys =="
    Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin/$($decoyLow.id)" -Method Delete | Out-Null
    Invoke-RestMethod -Uri "http://localhost:8080/api/decoy/admin/$($decoyCrit.id)" -Method Delete | Out-Null
    Check "Cleaned up test decoys" $true

} catch {
    Check "Phase 6 verification failed with error: $_" $false
}

Write-Host "`n----------------------------------------------------------"
Write-Host "Passed: $PASS   Failed: $FAIL"
Write-Host "----------------------------------------------------------"

if ($FAIL -gt 0) { exit 1 }
