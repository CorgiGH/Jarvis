#!/usr/bin/env bash
# Memory sanity-check runner (bash mirror of memory-verify.ps1).
# Walks $CLAUDE_MEMORY_DIR (or default), parses YAML frontmatter via awk,
# runs `verify:` cmds, writes report to <memory>/.verify-report.md.
set -u

# --- Resolve memory dir ---
MEMORY_DIR="${CLAUDE_MEMORY_DIR:-}"
if [ -z "$MEMORY_DIR" ]; then
    SANITIZED="$(pwd | sed 's|[/\\:]|-|g')"
    MEMORY_DIR="$HOME/.claude/projects/$SANITIZED/memory"
fi

if [ ! -d "$MEMORY_DIR" ]; then
    echo "[memory-verify] memory dir not found: $MEMORY_DIR (skipping)"
    exit 0
fi

REPORT_PATH="${MEMORY_VERIFY_REPORT_PATH:-$MEMORY_DIR/.verify-report.md}"
NOW="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

# Strip surrounding single/double quotes from a YAML-ish value.
unquote() { sed "s/^['\"]//; s/['\"]$//" <<< "$1"; }

# Parse a single memory file's frontmatter into a sequence of "key=value"
# lines on stdout. verify entries get prefixed numbered keys verify_0_cmd,
# verify_0_expect_match, etc.
parse_frontmatter() {
    local file="$1"
    awk '
        BEGIN { inblock = 0; inverify = 0; vidx = -1 }
        NR == 1 && $0 == "---" { inblock = 1; next }
        inblock && $0 == "---" { exit }
        !inblock { next }
        /^name:/        { sub(/^name:[[:space:]]*/, "");        print "name=" $0; inverify = 0; next }
        /^description:/ { sub(/^description:[[:space:]]*/, ""); print "description=" $0; inverify = 0; next }
        /^type:/        { sub(/^type:[[:space:]]*/, "");        print "type=" $0; inverify = 0; next }
        /^last_verified_at:/    { sub(/^last_verified_at:[[:space:]]*/, "");    print "last_verified_at=" $0; inverify = 0; next }
        /^freshness_window_hours:/ { sub(/^freshness_window_hours:[[:space:]]*/, ""); print "freshness_window_hours=" $0; inverify = 0; next }
        /^verify:[[:space:]]*$/  { inverify = 1; next }
        inverify && /^[[:space:]]*-[[:space:]]*cmd:/  { sub(/^[[:space:]]*-[[:space:]]*cmd:[[:space:]]*/, ""); vidx++; print "verify_" vidx "_cmd=" $0; next }
        inverify && /^[[:space:]]+expect_match:/      { sub(/^[[:space:]]+expect_match:[[:space:]]*/, "");      print "verify_" vidx "_expect_match=" $0; next }
        inverify && /^[[:space:]]+expect_no_match:/   { sub(/^[[:space:]]+expect_no_match:[[:space:]]*/, "");   print "verify_" vidx "_expect_no_match=" $0; next }
        inverify && /^[[:space:]]+expect_exit:/       { sub(/^[[:space:]]+expect_exit:[[:space:]]*/, "");       print "verify_" vidx "_expect_exit=" $0; next }
        inverify && /^[[:space:]]+on_fail:/           { sub(/^[[:space:]]+on_fail:[[:space:]]*/, "");           print "verify_" vidx "_on_fail=" $0; next }
    ' "$file"
}

# Run a single file's verify entries; print "STATUS|RAN_COUNT|REASONS" on stdout.
run_one_file() {
    local file="$1"
    local fm
    fm="$(parse_frontmatter "$file")"

    local status="OK"
    local reasons=""
    local ran=0

    # Discover number of verify entries.
    local max_idx=-1
    while IFS= read -r line; do
        if [[ "$line" =~ ^verify_([0-9]+)_cmd= ]]; then
            local idx="${BASH_REMATCH[1]}"
            if [ "$idx" -gt "$max_idx" ]; then max_idx="$idx"; fi
        fi
    done <<< "$fm"

    if [ "$max_idx" -lt 0 ]; then
        echo "OK|0|"
        return
    fi

    for idx in $(seq 0 "$max_idx"); do
        ran=$((ran + 1))
        local cmd="" exp_match="" exp_no_match="" exp_exit="0" on_fail=""
        while IFS= read -r line; do
            case "$line" in
                "verify_${idx}_cmd="*)            cmd="$(unquote "${line#verify_${idx}_cmd=}")" ;;
                "verify_${idx}_expect_match="*)   exp_match="$(unquote "${line#verify_${idx}_expect_match=}")" ;;
                "verify_${idx}_expect_no_match="*) exp_no_match="$(unquote "${line#verify_${idx}_expect_no_match=}")" ;;
                "verify_${idx}_expect_exit="*)    exp_exit="$(unquote "${line#verify_${idx}_expect_exit=}")" ;;
                "verify_${idx}_on_fail="*)        on_fail="$(unquote "${line#verify_${idx}_on_fail=}")" ;;
            esac
        done <<< "$fm"

        # Run the cmd, capture stdout + exit.
        local stdout exit_code
        stdout="$(bash -c "$cmd" 2>&1)"
        exit_code=$?

        if [ "$exit_code" -ne "$exp_exit" ] && [ "$exit_code" -ne 0 ]; then
            status="ERROR"
            reasons="${reasons}cmd '$cmd' exit=$exit_code (expected $exp_exit). on_fail: $on_fail|"
            continue
        fi

        if [ -n "$exp_match" ]; then
            if ! echo "$stdout" | grep -Eq -- "$exp_match"; then
                if [ "$status" = "OK" ]; then status="STALE"; fi
                reasons="${reasons}cmd '$cmd' output '$stdout' does NOT match '$exp_match'. on_fail: $on_fail|"
            fi
        fi
        if [ -n "$exp_no_match" ]; then
            if echo "$stdout" | grep -Eq -- "$exp_no_match"; then
                if [ "$status" = "OK" ]; then status="STALE"; fi
                reasons="${reasons}cmd '$cmd' output matches forbidden '$exp_no_match'. on_fail: $on_fail|"
            fi
        fi
    done

    echo "$status|$ran|$reasons"
}

# --- Walk the directory ---
{
    echo "# Verify report — $NOW"
    echo ""
    for f in "$MEMORY_DIR"/*.md; do
        [ -e "$f" ] || continue
        base="$(basename "$f")"
        case "$base" in
            MEMORY.md|BRIDGE.md|.*.md) continue ;;
        esac
        result="$(run_one_file "$f")"
        IFS='|' read -r status ran reasons <<< "$result"
        if [ "$ran" = "0" ]; then
            echo "[$status] $base (no verify; trusted)"
        else
            echo "[$status] $base ($ran verify)"
        fi
        if [ -n "$reasons" ]; then
            IFS='|' read -ra rs <<< "$reasons"
            for r in "${rs[@]}"; do
                [ -n "$r" ] && echo "    reason: $r"
            done
        fi
    done
} > "$REPORT_PATH"

echo "[memory-verify] report written to $REPORT_PATH"
exit 0
