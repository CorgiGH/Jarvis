#!/usr/bin/env bash
# Hourly probe of the PC relay /healthz. Writes status to
# /opt/jarvis/data/relay-health.log so the daily reflection job (or a
# manual `tail`) can see uptime trend.
#
# Install (run on VPS once):
#   chmod +x /opt/jarvis/tools/relay-health-cron.sh
#   (crontab -l 2>/dev/null | grep -v relay-health-cron; \
#    echo "0 * * * * /opt/jarvis/tools/relay-health-cron.sh") | crontab -
#
# Reads JARVIS_RELAY_URL + JARVIS_RELAY_TOKEN out of /opt/jarvis/.env via
# grep+cut (no `source` — dotenv values may contain $-vars or quotes the
# shell would re-eval).

set -euo pipefail

ENV_FILE="/opt/jarvis/.env"
LOG_FILE="/opt/jarvis/data/relay-health.log"

mkdir -p "$(dirname "$LOG_FILE")"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "$(date -u +%FT%TZ) FATAL missing $ENV_FILE" >> "$LOG_FILE"
    exit 1
fi

URL=$(grep -E '^JARVIS_RELAY_URL=' "$ENV_FILE" | head -1 | cut -d= -f2-)
TOK=$(grep -E '^JARVIS_RELAY_TOKEN=' "$ENV_FILE" | head -1 | cut -d= -f2-)

if [[ -z "$URL" || -z "$TOK" ]]; then
    echo "$(date -u +%FT%TZ) WARN JARVIS_RELAY_URL or JARVIS_RELAY_TOKEN unset" >> "$LOG_FILE"
    exit 0
fi

CODE=$(curl -s -m 15 -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $TOK" \
    "$URL/healthz" || echo "000")

echo "$(date -u +%FT%TZ) /healthz=$CODE" >> "$LOG_FILE"

# Trim log to last 1000 entries to keep it bounded.
if [[ $(wc -l < "$LOG_FILE") -gt 1000 ]]; then
    tail -1000 "$LOG_FILE" > "$LOG_FILE.tmp" && mv "$LOG_FILE.tmp" "$LOG_FILE"
fi
