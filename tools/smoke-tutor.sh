#!/usr/bin/env bash
set -euo pipefail
HOST="${HOST:-http://localhost:7331}"

echo "[smoke] /api/v1/health"
curl -fsS "$HOST/api/v1/health" | grep -q '"ok":true'

echo "[smoke] /tutor/"
curl -fsS "$HOST/tutor/" | grep -q '<div id="root">'

echo "[smoke] OK"
