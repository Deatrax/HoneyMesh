#!/usr/bin/env bash
# HoneyMesh integration verification script.
# Run from the repo root, with the stack already up:
#   docker compose up -d
#   bash scripts/verify.sh
#
# Note on section 8: it deliberately triggers a real CRITICAL threat
# escalation and a real IP block as part of verifying enforcement works.
# If you re-run this within ~5 minutes of a previous run, you may see
# your test IP already blocked from the start — that's not a failure,
# it's proof the block persisted across the run. The checks are written
# to treat "already blocked" as a pass, not just "became blocked."
#
# Requires: docker, curl. Mac/Linux, or WSL/Git Bash on Windows.
# PowerShell equivalent: scripts/verify.ps1

set -uo pipefail

PASS=0
FAIL=0

check() {
  local desc="$1"
  local result="$2" # 0 = pass, anything else = fail
  if [ "$result" -eq 0 ]; then
    echo "  [PASS] $desc"
    PASS=$((PASS + 1))
  else
    echo "  [FAIL] $desc"
    FAIL=$((FAIL + 1))
  fi
}

echo "== 1. Direct service health (Actuator) =="
for svc_port in decoy-service:8081 threat-engine-service:8082 incident-service:8083 gateway:8080; do
  name="${svc_port%%:*}"
  port="${svc_port##*:}"
  curl -sf "http://localhost:${port}/actuator/health" > /dev/null
  check "$name actuator health (port $port)" $?
done

echo
echo "== 2. Gateway routing =="
for path in decoy threat incidents; do
  curl -sf "http://localhost:8080/api/${path}/ping" > /dev/null
  check "gateway routes /api/${path}/ping" $?
done

echo
echo "== 3. Decoy admin CRUD + honeypot catch-all + basic event pipeline =="
DECOY_PATH="/api/admin/verify-$(date +%s)"

CREATE_RESPONSE=$(curl -sf -X POST "http://localhost:8080/api/decoy/admin" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"verify script decoy\",\"endpointPath\":\"${DECOY_PATH}\",\"riskLevel\":\"LOW\"}")
check "created a decoy via admin API" $?

DECOY_ID=$(echo "$CREATE_RESPONSE" | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
[ -n "$DECOY_ID" ]
check "decoy id parsed from response (id=$DECOY_ID)" $?

curl -sf "http://localhost:8080${DECOY_PATH}" > /dev/null
check "hit the decoy's live path through the gateway catch-all route" $?

sleep 2 # give the async consumer a moment to process

LAST_EVENT=$(curl -sf "http://localhost:8080/api/threat/last-event")
echo "$LAST_EVENT" | grep -q "\"decoyId\":\"${DECOY_ID}\""
check "threat-engine consumed the event for decoy $DECOY_ID" $?

COUNT=$(curl -sf "http://localhost:8080/api/threat/count/${DECOY_ID}")
{ [ -n "$COUNT" ] && [ "$COUNT" != "0" ]; }
check "Redis counter incremented for decoy $DECOY_ID (count=$COUNT)" $?

curl -sf -X DELETE "http://localhost:8080/api/decoy/admin/${DECOY_ID}" > /dev/null
check "cleaned up test decoy" $?

echo
echo "== 4. Postgres schemas =="
SCHEMAS=$(docker exec honeymesh-postgres psql -U honeymesh -d honeymesh -tAc \
  "SELECT schema_name FROM information_schema.schemata;" 2>/dev/null)
for s in decoy threat_engine incident; do
  echo "$SCHEMAS" | grep -q "^${s}$"
  check "schema '$s' exists" $?
done

echo
echo "== 5. Redis reachable =="
docker exec honeymesh-redis redis-cli ping 2>/dev/null | grep -q PONG
check "redis responds to PING" $?

echo
echo "== 6. RabbitMQ management API reachable =="
curl -sf -u honeymesh:honeymesh_dev_pw "http://localhost:15672/api/overview" > /dev/null
check "rabbitmq management API reachable" $?

echo
echo "== 7. Incident Service auth (JWT) =="

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/incidents")
[ "$STATUS" = "401" ]
check "GET /api/incidents without a token returns 401 (got $STATUS)" $?

LOGIN_RESPONSE=$(curl -sf -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"analyst","password":"analyst123"}')
check "analyst can log in and receive a token" $?

ANALYST_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
[ -n "$ANALYST_TOKEN" ]
check "JWT token parsed from login response" $?

curl -sf -H "Authorization: Bearer ${ANALYST_TOKEN}" "http://localhost:8080/api/incidents" > /dev/null
check "GET /api/incidents with a valid analyst token succeeds" $?

ASSIGN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH \
  -H "Authorization: Bearer ${ANALYST_TOKEN}" -H "Content-Type: application/json" \
  -d '{"analyst":"analyst","version":0}' \
  "http://localhost:8080/api/incidents/999999/assign")
[ "$ASSIGN_STATUS" = "403" ] || [ "$ASSIGN_STATUS" = "401" ]
check "analyst (non-admin) token is blocked on the admin-only assign endpoint (got $ASSIGN_STATUS)" $?

UNBLOCK_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH \
  -H "Authorization: Bearer ${ANALYST_TOKEN}" -H "Content-Type: application/json" \
  -d '{"version":0}' \
  "http://localhost:8080/api/incidents/999999/unblock")
[ "$UNBLOCK_STATUS" = "403" ] || [ "$UNBLOCK_STATUS" = "401" ]
check "analyst (non-admin) token is blocked on the admin-only unblock endpoint (got $UNBLOCK_STATUS)" $?

echo
echo "== 8. Full chain: escalation -> enforcement -> auto-incident =="
echo "   (this is the actual end-to-end path your demo relies on)"

ADMIN_LOGIN=$(curl -sf -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
[ -n "$ADMIN_TOKEN" ]
check "admin login succeeds (needed to read incidents below)" $?

CHAIN_PATH="/api/admin/verify-chain-$(date +%s)"
CHAIN_DECOY=$(curl -sf -X POST "http://localhost:8080/api/decoy/admin" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"verify chain decoy\",\"endpointPath\":\"${CHAIN_PATH}\",\"riskLevel\":\"CRITICAL\"}")
CHAIN_DECOY_ID=$(echo "$CHAIN_DECOY" | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
[ -n "$CHAIN_DECOY_ID" ]
check "created a CRITICAL-risk decoy for the escalation test (id=$CHAIN_DECOY_ID)" $?

# 6 hits: CRITICAL base (60) + 5-or-more-hits bonus (20) = 80, over the
# CRITICAL threshold (75). One hit of margin above the minimum 5.
for i in 1 2 3 4 5 6; do
  curl -s "http://localhost:8080${CHAIN_PATH}" > /dev/null
done
check "fired 6 rapid hits at the CRITICAL decoy" 0

sleep 3 # let the async chain (correlate -> score -> maybe block -> publish) finish

ASSESSMENT=$(curl -sf "http://localhost:8080/api/threat/last-assessment-event")
echo "$ASSESSMENT" | grep -q '"level":"CRITICAL"'
check "threat assessment escalated to CRITICAL (score/level reflect the 6 hits)" $?

CHAIN_IP=$(echo "$ASSESSMENT" | grep -o '"sourceIp":"[^"]*"' | cut -d'"' -f4)
[ -n "$CHAIN_IP" ]
check "captured the source IP that should now be blocked ($CHAIN_IP)" $?

BLOCK_STATUS_RESPONSE=$(curl -sf "http://localhost:8080/api/threat/blocklist/${CHAIN_IP}")
echo "$BLOCK_STATUS_RESPONSE" | grep -q '"blocked":true'
check "threat-engine confirms $CHAIN_IP is blocked" $?

ENFORCED_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080${CHAIN_PATH}")
[ "$ENFORCED_STATUS" = "403" ]
check "a further hit from the blocked IP is actually rejected with 403 (got $ENFORCED_STATUS)" $?

INCIDENTS=$(curl -sf -H "Authorization: Bearer ${ADMIN_TOKEN}" "http://localhost:8080/api/incidents")
echo "$INCIDENTS" | grep -q "\"sourceIp\":\"${CHAIN_IP}\""
check "an incident was auto-created for $CHAIN_IP" $?

echo
echo "-----------------------------------"
echo "Passed: $PASS   Failed: $FAIL"
echo "-----------------------------------"
echo "Not automated here — check manually:"
echo "  websocat ws://localhost:8080/ws/alerts   (should get a heartbeat every 10s,"
echo "  plus an 'incident.updated' message right after section 8 runs)"

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
