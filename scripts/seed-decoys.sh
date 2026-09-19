#!/usr/bin/env bash

GATEWAY="http://localhost:8080"

echo "Logging in as admin..."
LOGIN_RESPONSE=$(curl -sf -X POST "$GATEWAY/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
ADMIN_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "Could not log in as admin — is docker compose up? Aborting."
  exit 1
fi

seed() {
  local body="$1"
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/decoy/admin" \
    -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" -d "$body")
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
