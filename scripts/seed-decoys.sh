#!/usr/bin/env bash
# Seeds a handful of realistic-looking decoys for local dev/demo.
# Safe to re-run: a duplicate path just gets skipped (decoy-service's own
# 409 check), nothing special this script needs to handle.

GATEWAY="http://localhost:8080"

seed() {
  local body="$1"
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/decoy/admin" \
    -H "Content-Type: application/json" -d "$body")
  if [ "$status" = "201" ]; then
    echo "  created"
  elif [ "$status" = "409" ]; then
    echo "  already exists, skipped"
  else
    echo "  unexpected status $status — is docker compose up?"
  fi
}

echo "Seeding decoys..."
seed '{"name":"Database backup panel","endpointPath":"/api/admin/db-backup","riskLevel":"CRITICAL","orgId":"acme-corp"}'
seed '{"name":"Payroll export","endpointPath":"/api/finance/payroll","riskLevel":"HIGH","orgId":"acme-corp"}'
seed '{"name":"Debug environment dump","endpointPath":"/api/debug/environment","riskLevel":"MEDIUM","orgId":"acme-corp"}'
echo "Done — check http://localhost:5173/decoys"
