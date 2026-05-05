# Run jarvis reflect only if no daily-reflection wiki entry exists in the
# last 20 hours. Keeps logon-triggered runs from producing duplicate
# reflections when the user logs in multiple times per day.

$wiki = "$env:USERPROFILE\.life-os\wiki.md"
$threshold = (Get-Date).ToUniversalTime().AddHours(-20)
$stale = $true

if (Test-Path $wiki) {
    $lines = Get-Content $wiki -Encoding UTF8
    foreach ($line in $lines) {
        if ($line -match '^\#\#\s+\[(?<ts>[\d\-:\s]+)\sUTC\]\s+daily reflection') {
            try {
                $entryTs = [DateTime]::ParseExact($Matches.ts.Trim(), 'yyyy-MM-dd HH:mm', $null)
                if ($entryTs -gt $threshold) {
                    $stale = $false
                }
            } catch {
                # malformed timestamp: ignore and keep scanning
            }
        }
    }
}

if (-not $stale) {
    Write-Host "Recent reflection found (within 20h). Skipping."
    exit 0
}

Write-Host "Stale or missing reflection. Running jarvis reflect..."
Set-Location "C:\Users\User\jarvis-kotlin"
& "C:\Users\User\jarvis-kotlin\build\install\jarvis-kotlin\bin\jarvis-kotlin.bat" reflect
