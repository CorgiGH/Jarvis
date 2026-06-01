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
echo "[deploy] tutor-web SPA build"
( cd tutor-web && npm ci && npm run build )

echo "[deploy] gradle :test :installDist :android:assembleDebug"
gradle :test :installDist :android:assembleDebug

if [[ ! -d "$DIST_LOCAL" ]]; then
    echo "[deploy] FATAL: $DIST_LOCAL missing after installDist"
    exit 1
fi

APK_LOCAL="android/build/outputs/apk/debug/android-debug.apk"
if [[ ! -f "$APK_LOCAL" ]]; then
    echo "[deploy] FATAL: $APK_LOCAL missing after assembleDebug"
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

# scp the APK so /apk endpoint serves the current Phase 3.1 build. Without
# this the VPS keeps whatever stale APK was built on it locally (caught in
# 2026-05-08 Phase 3.1 smoke — VPS APK was 2 days old, no WorkManager).
ssh "$VPS" "mkdir -p /opt/jarvis/android/build/outputs/apk/debug"
scp -q "$APK_LOCAL" "$VPS:/opt/jarvis/android/build/outputs/apk/debug/android-debug.apk"

# 2026-05-13 non-root migration: scp lands as root-owned by default. Chown
# the fresh dist + APK to jarvis:jarvis so the User=jarvis service can
# actually read/execute them on restart.
ssh "$VPS" "chown -R jarvis:jarvis $DIST_REMOTE /opt/jarvis/android"

# === CONTENT CORPUS (Gate 3 / E2 / E3) ===
# Historically deploy.sh shipped only the dist; the content/ knowledge-concept
# corpus — which the tutor reads at runtime via JARVIS_CONTENT_DIR (curator
# routes, the E3 generate-drills KC lookup, the content validator) — was never
# deployed. Ship it to a stable sibling dir (like data/), replaced each deploy.
echo "[deploy] scp content/ corpus → /opt/jarvis/content"
ssh "$VPS" "rm -rf /opt/jarvis/content.tmp"
scp -rq content "$VPS:/opt/jarvis/content.tmp"
ssh "$VPS" "
    rm -rf /opt/jarvis/content &&
    mv /opt/jarvis/content.tmp /opt/jarvis/content &&
    chown -R jarvis:jarvis /opt/jarvis/content
"
# The app resolves the corpus via JARVIS_CONTENT_DIR (default 'content',
# CWD-relative). systemd's WorkingDirectory is NOT the repo, so this MUST be an
# absolute path in /opt/jarvis/.env (preserved across deploys). Warn — don't
# fail — so the operator sets it once: JARVIS_CONTENT_DIR=/opt/jarvis/content
ssh "$VPS" "grep -q '^JARVIS_CONTENT_DIR=' /opt/jarvis/.env || echo '[deploy] WARNING: JARVIS_CONTENT_DIR not set in /opt/jarvis/.env — add JARVIS_CONTENT_DIR=/opt/jarvis/content or the tutor reads an empty/missing corpus'"

ssh "$VPS" "
    systemctl start jarvis &&
    sleep 4 &&
    systemctl is-active jarvis
"

# === VERIFY ===
echo "[deploy] verifying $HEALTH_URL"
curl -s -m 10 "$HEALTH_URL" && echo
curl -fsS "https://corgflix.duckdns.org/" | grep -q '<div id="root"' || { echo "SMOKE FAIL: SPA index did not serve"; exit 1; }
ssh "$VPS" "tail -25 /var/log/jarvis.log | grep -Ev '^SLF4J|^Picked up|^Jarvis web|^Auth required' | head -20"

# === SURFACE X ADVISORY (opt-in, never blocks) ===
if [[ "${RUN_LLM_EVAL:-0}" == "1" ]]; then
    echo "[deploy] RUN_LLM_EVAL=1 — running Surface X advisory grader on smoke trace..."
    ( cd tools && node surface-x.mjs --task=01KR6K07T6PATPRR5KH1JXYF8E --invariants=INV-01,INV-02,INV-08 ) \
        || echo "[deploy] Surface X advisory: non-zero exit ($?). Findings written. Deploy continues."
fi

echo "[deploy] done. rollback: bash tools/deploy.sh rollback"

# SCHEMA-DEPLOY PRECONDITION (gap-ledger must-resolve #3):
#   ssh root@46.247.109.91 'python3 - /opt/jarvis/backups' < tools/db-backup.py
#   (or copy tools/db-backup.py to the VPS and run it there)
# Run this and confirm the card count BEFORE any deploy that ALTERs a table.
