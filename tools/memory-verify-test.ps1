#!/usr/bin/env pwsh
# Test harness for tools/memory-verify.ps1.
# Sources the script as a module and exercises the parser + verify path.

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent $here
$script = Join-Path $repo 'tools/memory-verify.ps1'

# Dot-source the script with a sentinel that prevents the runner block from firing.
$env:MEMORY_VERIFY_TEST_MODE = '1'
. $script

$failures = 0

function Assert-Equal($expected, $actual, $label) {
    if ($expected -ne $actual) {
        Write-Host "[FAIL] $label  expected=<$expected>  actual=<$actual>" -ForegroundColor Red
        $script:failures++
    } else {
        Write-Host "[OK]   $label" -ForegroundColor Green
    }
}

# Test 1: Parse frontmatter from ok.md
$okPath = Join-Path $repo 'tools/memory-verify-fixtures/ok.md'
$parsed = Parse-MemoryFrontmatter -Path $okPath
Assert-Equal 'OK fixture' $parsed.name 'parse name from ok.md'
Assert-Equal 1 $parsed.verify.Count 'parse 1 verify entry from ok.md'
Assert-Equal 'echo MAGIC_OK' $parsed.verify[0].cmd 'parse cmd from ok.md'
Assert-Equal '^MAGIC_OK$' $parsed.verify[0].expect_match 'parse expect_match from ok.md'

# Test 2: Parse no-verify.md returns empty verify array
$nvPath = Join-Path $repo 'tools/memory-verify-fixtures/no-verify.md'
$nvParsed = Parse-MemoryFrontmatter -Path $nvPath
Assert-Equal 'No-verify fixture' $nvParsed.name 'parse name from no-verify.md'
Assert-Equal 0 $nvParsed.verify.Count 'no-verify.md has empty verify array'

if ($failures -gt 0) {
    Write-Host "$failures test(s) failed" -ForegroundColor Red
    exit 1
}
Write-Host "All tests passed" -ForegroundColor Green
exit 0
