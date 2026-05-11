#!/usr/bin/env bash
# Sync converted Markdown corpus to VPS archival; trigger ingest-corpus.
#
# Usage:
#   bash tools/sync-corpus-to-vps.sh
#
# Expects:
#   - tmp-md/ exists locally (run tools/pdf-to-md.py first)
#   - VPS is reachable at root@46.247.109.91 (ssh key auth)
#   - VPS has /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin in PATH
#
# Side effects:
#   - rsync tmp-md/* → root@VPS:/opt/jarvis/data/archival/_extras/
#   - ssh + run jarvis-kotlin ingest-corpus on VPS
#   - Updates /opt/jarvis/data/knowledge.jsonl

set -euo pipefail

VPS="${JARVIS_VPS:-root@46.247.109.91}"
LOCAL_DIR="${LOCAL_DIR:-./tmp-md}"
REMOTE_DIR="${REMOTE_DIR:-/opt/jarvis/data/archival/_extras}"

if [[ ! -d "$LOCAL_DIR" ]]; then
    echo "error: $LOCAL_DIR does not exist; run tools/pdf-to-md.py first" >&2
    exit 2
fi

echo "[sync] rsync $LOCAL_DIR/ → $VPS:$REMOTE_DIR/"
rsync -av --update --exclude='*.swp' "$LOCAL_DIR/" "$VPS:$REMOTE_DIR/"

echo "[ingest] running ingest-corpus on $VPS"
ssh "$VPS" "set -a; source /opt/jarvis/.env; set +a; /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin ingest-corpus"

echo "[verify] knowledge.jsonl size + last entries on $VPS"
ssh "$VPS" "wc -l /opt/jarvis/data/knowledge.jsonl; tail -1 /opt/jarvis/data/knowledge.jsonl | head -c 400"

echo "[done] sync + ingest complete"
