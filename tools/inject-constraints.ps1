# UserPromptSubmit hook: re-inject the live ACTIVE CONSTRAINTS at decision-altitude
# (bottom of context) every turn, so they cannot go cold mid-session. See the
# self-discipline council 2026-05-30 (Tooth 1). Stays silent if the file is absent.
$ErrorActionPreference = 'Stop'
try {
    $f = Join-Path $PSScriptRoot '..\.claude\active-constraints.md'
    if (Test-Path $f) {
        $body = Get-Content $f -Raw
        @{
            hookSpecificOutput = @{
                hookEventName     = 'UserPromptSubmit'
                additionalContext = $body
            }
        } | ConvertTo-Json -Compress
    }
} catch {
    # Never break a turn over the discipline hook — fail silent.
}
