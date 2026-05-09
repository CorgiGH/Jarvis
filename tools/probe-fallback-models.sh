#!/usr/bin/env bash
# Probe each model in the JARVIS_OPENROUTER_MODEL + JARVIS_OPENROUTER_FALLBACK_MODELS
# chain to catch dead / pulled / 404 model IDs BEFORE they bite mid-chat.
#
# Council R5 Pragmatist flagged "list rot" as MEDIUM risk: a stale fallback
# model is just as broken as a stale primary, and OpenRouter free-tier models
# churn frequently (gemini-2.0-flash-exp:free was pulled mid-session;
# mistralai/mistral-small-3.1-24b-instruct:free was 404 within 2 min of
# being added to the chain on 2026-05-09).
#
# Usage:
#   bash tools/probe-fallback-models.sh                # use /opt/jarvis/.env
#   ENV_FILE=/path/.env bash tools/probe-fallback-models.sh
#
# Exit codes:
#   0 — every model in the chain returned 200 OR 429-rate-limited (transient)
#   1 — one or more models returned a permanent failure (404/410/400/etc)
#   2 — config error (no API key, no chain to probe)
#
# Recommended cron: weekly. Add to /etc/cron.d/jarvis-probe:
#   30 6 * * 1 root /opt/jarvis/jarvis-kotlin/tools/probe-fallback-models.sh \
#       >> /var/log/jarvis-fallback-probe.log 2>&1

set -uo pipefail

ENV_FILE="${ENV_FILE:-/opt/jarvis/.env}"
if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

API_KEY="${OPENROUTER_API_KEY:-}"
if [[ -z "$API_KEY" ]]; then
    echo "[probe] ERROR: OPENROUTER_API_KEY not set (checked $ENV_FILE + env)" >&2
    exit 2
fi

PRIMARY="${JARVIS_OPENROUTER_MODEL:-meta-llama/llama-3.3-70b-instruct:free}"
FALLBACKS="${JARVIS_OPENROUTER_FALLBACK_MODELS:-}"

# Build chain — primary first, then comma-separated fallbacks.
CHAIN=("$PRIMARY")
if [[ -n "$FALLBACKS" ]]; then
    IFS=',' read -ra extra <<< "$FALLBACKS"
    for m in "${extra[@]}"; do
        m_trim="$(echo -n "$m" | xargs)"
        if [[ -n "$m_trim" ]]; then CHAIN+=("$m_trim"); fi
    done
fi

ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "[probe] $ts chain has ${#CHAIN[@]} model(s)"

permanent_fails=0
for model in "${CHAIN[@]}"; do
    body=$(curl -sS -X POST https://openrouter.ai/api/v1/chat/completions \
        -H "Authorization: Bearer $API_KEY" \
        -H "HTTP-Referer: https://github.com/CorgiGH/Jarvis" \
        -H "X-Title: jarvis-kotlin-fallback-probe" \
        -H "Content-Type: application/json" \
        -d "{\"model\":\"$model\",\"max_tokens\":8,\"messages\":[{\"role\":\"user\",\"content\":\"reply with one word: PROBE_OK\"}]}")
    code=$(echo "$body" | python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
except Exception:
    print("invalid_json"); sys.exit(0)
err = d.get("error")
if err:
    print(err.get("code") or "err")
else:
    m = d.get("model") or "?"
    print("200 served-by=" + str(m))
' 2>/dev/null)
    case "$code" in
        200*) echo "[probe] OK   $model    ($code)" ;;
        429)  echo "[probe] WARN $model    rate-limited (transient — retry expected to succeed)" ;;
        404|410)
            echo "[probe] FAIL $model    permanent (model pulled/removed)"
            permanent_fails=$((permanent_fails + 1))
            ;;
        400)
            echo "[probe] FAIL $model    bad request — likely invalid model ID"
            permanent_fails=$((permanent_fails + 1))
            ;;
        *)
            echo "[probe] FAIL $model    unknown ($code)"
            permanent_fails=$((permanent_fails + 1))
            ;;
    esac
done

if (( permanent_fails > 0 )); then
    echo "[probe] $permanent_fails permanent failure(s) — edit JARVIS_OPENROUTER_FALLBACK_MODELS in $ENV_FILE"
    exit 1
fi
echo "[probe] all chain entries reachable (200 or transient 429)"
exit 0
