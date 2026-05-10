#!/usr/bin/env bash
# Test harness for tools/memory-verify.sh.
set -u  # pipefail intentionally off so we can detect script failures.

REPO="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="$REPO/tools/memory-verify.sh"
FIXTURES="$REPO/tools/memory-verify-fixtures"
REPORT_TMP="$(mktemp)"
failures=0

assert_eq() {
    local expected="$1"; local actual="$2"; local label="$3"
    if [ "$expected" = "$actual" ]; then
        echo "[OK]   $label"
    else
        echo "[FAIL] $label  expected=<$expected>  actual=<$actual>"
        failures=$((failures + 1))
    fi
}

assert_contains() {
    local haystack="$1"; local needle="$2"; local label="$3"
    if echo "$haystack" | grep -qF -- "$needle"; then
        echo "[OK]   $label"
    else
        echo "[FAIL] $label  needle=<$needle>"
        failures=$((failures + 1))
    fi
}

# Test 1: Run script against fixture dir, capture report
CLAUDE_MEMORY_DIR="$FIXTURES" MEMORY_VERIFY_REPORT_PATH="$REPORT_TMP" bash "$SCRIPT"
assert_eq "0" "$?" "script exit 0"

REPORT="$(cat "$REPORT_TMP")"
assert_contains "$REPORT" "[OK] ok.md"          "report contains [OK] ok.md"
assert_contains "$REPORT" "[STALE] stale.md"    "report contains [STALE] stale.md"
assert_contains "$REPORT" "[ERROR] error.md"    "report contains [ERROR] error.md"
assert_contains "$REPORT" "[OK] no-verify.md"   "report contains [OK] no-verify.md (trusted)"

rm -f "$REPORT_TMP"
if [ "$failures" -gt 0 ]; then
    echo "$failures test(s) failed"
    exit 1
fi
echo "All tests passed"
exit 0
