# DEPRECATED 2026-05-07: dumping a 988-file corpus into wiki.md collapsed
# chat retrieval (recent(N) returned lecture pseudocode instead of recent
# conversations). Council verdict (council-1778078829.md) said this is a
# schema problem, not a retrieval problem; the right fix is the
# Letta-style split (conversations.jsonl + archival/ + core_memory.md).
#
# This script remains as reference / for future small ingestions only.
# It now also skips noise dirs (node_modules, .git, build, dist, etc.)
# so it does not re-pull the React app's npm readmes when re-run.
#
# Walks one or more directories on the PC and POSTs every .md file to the
# Jarvis backend's /api/wiki endpoint. Each file becomes a single wiki
# section tagged with its source path so retrieval can credit it back.
#
# Usage (defaults shown):
#   powershell -ExecutionPolicy Bypass -File tools\populate.ps1
#   powershell -ExecutionPolicy Bypass -File tools\populate.ps1 -Backend https://...
#   powershell -ExecutionPolicy Bypass -File tools\populate.ps1 -Roots @("C:\Path\Notes")
#
# Notes:
# - Empty / whitespace-only files are skipped.
# - Each file gets its own wiki entry; large files are NOT chunked, so
#   anything over ~16 KB will go in whole. The Ktor server's
#   client_max_body_size is 16m so this is comfortable headroom.
# - Files are processed in directory order; rerunning the script will
#   re-post (the wiki is append-only, so you'll get duplicates). Re-index
#   the embeddings store separately if you want semantic retrieval to
#   pick up the new entries.

param(
    [string]$Backend = "https://corgflix.duckdns.org",
    [string]$TokenFile = "C:\Users\User\jarvis-kotlin\tools\AUTH_TOKEN.txt",
    [string[]]$Roots = @(
        "$env:USERPROFILE\Desktop\Second brain",
        "$env:USERPROFILE\Desktop\SO\os-study-guide"
    ),
    [int]$Sleep = 50  # ms between requests, gentle to the server
)

if (-not (Test-Path $TokenFile)) {
    Write-Error "Token file missing: $TokenFile"
    exit 1
}
$token = (Get-Content $TokenFile -Raw).Trim()
if ($token.Length -lt 16) {
    Write-Error "Token from $TokenFile looks too short (length=$($token.Length))."
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type"  = "application/json; charset=utf-8"
}
$ok = 0
$skipped = 0
$failed = 0
$start = Get-Date

foreach ($root in $Roots) {
    if (-not (Test-Path $root)) {
        Write-Warning "Missing root: $root"
        continue
    }
    $rootName = Split-Path $root -Leaf
    Write-Host "=== Walking $root ==="

    # Exclude common build/cache/vendor dirs; without this a React app
    # under -Roots pulls in hundreds of npm package readmes.
    $excludeDirs = @(
        '\\node_modules\\', '\\.git\\', '\\build\\', '\\dist\\',
        '\\.gradle\\', '\\target\\', '\\.idea\\', '\\.vscode\\',
        '\\.next\\', '\\.cache\\', '\\out\\'
    )
    $files = Get-ChildItem -Path $root -Recurse -Filter *.md -File -ErrorAction SilentlyContinue |
        Where-Object {
            $p = $_.FullName
            -not ($excludeDirs | Where-Object { $p -like "*$_*" })
        }
    Write-Host "  found $($files.Count) markdown files (after excluding node_modules / build / etc.)"

    foreach ($f in $files) {
        $rel = $f.FullName.Substring($root.Length).TrimStart('\','/').Replace('\','/')
        # [string] cast forces unwrap of any PS-side object the -Raw / -Encoding
        # combination returns; without it ConvertTo-Json on PS 5.1 emits
        # {"value":"..."} which the Ktor decoder then 400s on.
        $body = [string](Get-Content $f.FullName -Raw -Encoding UTF8)
        if ([string]::IsNullOrWhiteSpace($body)) {
            $skipped++
            continue
        }

        $section = "[SOURCE: $rootName/$rel]"
        $obj = [PSCustomObject]@{
            section = $section
            content = $body
        }
        $payload = $obj | ConvertTo-Json -Compress -Depth 3
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)

        try {
            Invoke-RestMethod -Uri "$Backend/api/wiki" -Method Post `
                -Headers $headers -Body $bytes -ErrorAction Stop | Out-Null
            $ok++
            if ($ok % 25 -eq 0) {
                Write-Host "  …$ok entries posted"
            }
        } catch {
            $failed++
            Write-Warning "FAIL $rel : $($_.Exception.Message)"
        }

        if ($Sleep -gt 0) { Start-Sleep -Milliseconds $Sleep }
    }
}

$elapsed = (Get-Date) - $start
Write-Host ""
Write-Host "DONE in $([int]$elapsed.TotalSeconds)s : ok=$ok skipped=$skipped failed=$failed"
Write-Host "Wiki on VPS: ssh $env:VPS 'wc -l /opt/jarvis/data/wiki.md; ls -la /opt/jarvis/data/'"
