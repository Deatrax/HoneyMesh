#!/usr/bin/env bash
# HoneyMesh integration verification script.
# Run from the repo root, with the stack already up:
#   docker compose up -d
#   bash scripts/verify.sh
#
# Extend this as real features land — e.g. once Mahim's decoy admin CRUD
# exists, add a check that creates a decoy and reads it back; once Alfi's
# JWT login exists, add a check that a protected endpoint 401s without a
# token. Keep it growing alongside the codebase, don't let it go stale.
#
# Requires: docker, curl. Mac/Linux, or WSL/Git Bash on Windows.

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
echo "== 3. Decoy admin CRUD + honeypot catch-all + full event pipeline =="
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
echo "-----------------------------------"
echo "Passed: $PASS   Failed: $FAIL"
echo "-----------------------------------"
echo "Not automated here — check manually:"
echo "  websocat ws://localhost:8080/ws/alerts   (should get a heartbeat every 10s)"

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
