#!/usr/bin/env bash
# jarvis-kotlin VPS deploy.
#
# Synthesized from council 1778155110 verdict + 2026-05-07 deploy session
# (Step B Letta-split + VectorStore wiring shipped this way).
#
# Layout assumed on VPS:
#   /opt/jarvis/.env                       (preserved across deploys)
#   /opt/jarvis/data/                      (state, preserved)
#   /opt/jarvis/jarvis-kotlin/             (current dist, replaced)
#   /opt/jarvis/jarvis-kotlin-prev/        (rollback handle, rotated each deploy)
#   /opt/jarvis/jarvis-kotlin-pre-stepb/   (deeper rollback, manual cleanup)
#
# systemd unit at /etc/systemd/system/jarvis.service points
# ExecStart=/opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin web
#
# Usage:
#   bash tools/deploy.sh           # full deploy
#   bash tools/deploy.sh rollback  # roll current → -prev, restart

set -euo pipefail

VPS="${JARVIS_VPS:-root@46.247.109.91}"
HEALTH_URL="${JARVIS_HEALTH:-https://corgflix.duckdns.org/healthz}"
DIST_LOCAL="build/install/jarvis-kotlin"
DIST_REMOTE="/opt/jarvis/jarvis-kotlin"
PREV_REMOTE="/opt/jarvis/jarvis-kotlin-prev"

if [[ "${1-}" == "rollback" ]]; then
    echo "[deploy] ROLLBACK: swapping $DIST_REMOTE ↔ $PREV_REMOTE on $VPS"
    ssh "$VPS" "
        systemctl stop jarvis &&
        rm -rf /opt/jarvis/jarvis-kotlin-rollback-staging &&
        mv $DIST_REMOTE /opt/jarvis/jarvis-kotlin-rollback-staging &&
        mv $PREV_REMOTE $DIST_REMOTE &&
        mv /opt/jarvis/jarvis-kotlin-rollback-staging $PREV_REMOTE &&
        systemctl start jarvis &&
        sleep 3 &&
        systemctl is-active jarvis
    "
    curl -s -m 10 "$HEALTH_URL" && echo
    echo "[deploy] rollback complete"
    exit 0
fi

# === LOCAL BUILD + SMOKE ===
echo "[deploy] gradle :test :installDist"
gradle :test :installDist

if [[ ! -d "$DIST_LOCAL" ]]; then
    echo "[deploy] FATAL: $DIST_LOCAL missing after installDist"
    exit 1
fi

# === VPS PRE-CHECKS ===
echo "[deploy] VPS pre-checks"
ssh "$VPS" "
    df -h /opt | tail -1 | awk '{ print \"[deploy] /opt free=\" \$4 }' &&
    free -h | awk '/^Mem:/ { print \"[deploy] RAM avail=\" \$7 }'
"

# === SWAP ===
echo "[deploy] stop service, rotate -prev, scp dist, restart"
ssh "$VPS" "
    systemctl stop jarvis &&
    rm -rf $PREV_REMOTE.tmp &&
    if [[ -d $PREV_REMOTE ]]; then mv $PREV_REMOTE $PREV_REMOTE.tmp; fi &&
    mv $DIST_REMOTE $PREV_REMOTE &&
    rm -rf $PREV_REMOTE.tmp
"

scp -rq "$DIST_LOCAL" "$VPS:/opt/jarvis/"

ssh "$VPS" "
    systemctl start jarvis &&
    sleep 4 &&
    systemctl is-active jarvis
"

# === VERIFY ===
echo "[deploy] verifying $HEALTH_URL"
curl -s -m 10 "$HEALTH_URL" && echo
ssh "$VPS" "tail -25 /var/log/jarvis.log | grep -Ev '^SLF4J|^Picked up|^Jarvis web|^Auth required' | head -20"

echo "[deploy] done. rollback: bash tools/deploy.sh rollback"
