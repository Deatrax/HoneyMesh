#!/usr/bin/env bash

GATEWAY="http://localhost:8080"

pause() {
  echo
  read -p "-- press Enter to continue --" _
  echo
}

clear
echo "======================================================="
echo " PHASE 1 — Reconnaissance: what's actually running?"
echo "======================================================="
echo "An attacker who found this system starts by mapping what's listening."
pause

if command -v nmap >/dev/null 2>&1; then
  nmap -p 5432,6379,5672,15672,8080,8081,8082,8083 -sV localhost
else
  echo "(nmap not installed here — skipping the live scan, but this is what"
  echo " it would show: ports 5432/6379/5672/15672/8080-8083 open, with"
  echo " 8080 identified as the Spring Cloud Gateway.)"
fi

echo
echo "Port 8080 is the intended front door — the Gateway. Ports 8081-8083"
echo "are the individual services, exposed here only for our own local dev"
echo "convenience; a real deployment would firewall those off so only"
echo "8080 is internet-facing."
pause

clear
echo "======================================================="
echo " PHASE 2 — Path discovery: nmap finds ports, not URLs"
echo "======================================================="
echo "Knowing 8080 is open doesn't tell you what's behind it. For that,"
echo "an attacker probes a wordlist of likely-sounding paths."
pause

WORDLIST=(
  "/api/admin/users"
  "/api/admin/db-backup"
  "/api/config/database"
  "/api/admin/backup-logs"
  "/api/v2/status"
  "/api/v1/health"
  "/.env"
  "/api/internal/metrics"
)

for path in "${WORDLIST[@]}"; do
  status=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY$path")
  if [ "$status" = "200" ]; then
    echo "  [200] $path   <-- responds!"
  else
    echo "  [$status] $path"
  fi
  sleep 0.2
done

pause

clear
echo "======================================================="
echo " PHASE 3 — Investigate the interesting one"
echo "======================================================="
echo "/api/admin/db-backup. That name is not subtle."
pause

curl -s "$GATEWAY/api/admin/db-backup"
echo
echo
echo "Looks like a normal, working API response. From the attacker's side,"
echo "this looks like it worked — nothing here suggests anything's wrong yet."
pause

clear
echo "======================================================="
echo " PHASE 4 — Escalate: if it's real, make sure"
echo "======================================================="
echo "Hitting it repeatedly, fast — the way an automated tool would."
pause

for i in 1 2 3 4 5 6; do
  echo -n "  request $i... "
  curl -s -o /dev/null -w "%{http_code}\n" "$GATEWAY/api/admin/db-backup"
  sleep 0.3
done

pause

clear
echo "======================================================="
echo " PHASE 5 — The consequence"
echo "======================================================="
echo "One more request."
pause

echo -n "  final request... "
FINAL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/admin/db-backup")
echo "$FINAL_STATUS"

echo
if [ "$FINAL_STATUS" = "403" ]; then
  echo "403 — blocked. Not by a human watching a dashboard at the right"
  echo "moment. By the system, automatically, within seconds of the"
  echo "pattern crossing its threshold."
else
  echo "(Didn't get 403 — the burst above may not have crossed the"
  echo "threshold yet, or this IP was already past its block TTL. Check"
  echo "http://localhost:8080/api/threat/last-assessment-event for the"
  echo "actual score/level before re-running phase 4.)"
fi

echo
echo "Check the dashboard now — an incident should already be open,"
echo "auto-created from the same assessment that triggered this block."
