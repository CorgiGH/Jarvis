# Stage all .md files from the source directories into a single zip,
# preserving their relative paths under per-source subtrees:
#   archival/second-brain/<rel>.md
#   archival/study-guide/<rel>.md
# Output: C:\Users\User\AppData\Local\Temp\jarvis-archival.zip
#
# After this runs, scp the zip to the VPS and unzip into
# /opt/jarvis/data/archival/. The chat path will not see these files
# (wiki.md is the only thing recent() reads); they live there waiting
# for the future Letta-split storage / FTS / embeddings indexer.

$ErrorActionPreference = "Stop"

$staging = "C:\Users\User\AppData\Local\Temp\jarvis-archival"
$zip     = "C:\Users\User\AppData\Local\Temp\jarvis-archival.zip"

if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Path $staging -Force | Out-Null

$pairs = @(
    @{ Src = "$env:USERPROFILE\Desktop\Second brain";    Name = "second-brain" },
    @{ Src = "$env:USERPROFILE\Desktop\SO\os-study-guide"; Name = "study-guide"  }
)

$total = 0
foreach ($p in $pairs) {
    $src  = $p.Src
    $name = $p.Name
    if (-not (Test-Path $src)) {
        Write-Warning "Missing source: $src"
        continue
    }
    Write-Host "=== Staging $name from $src ==="
    $excludeDirs = @(
        '\\node_modules\\', '\\.git\\', '\\build\\', '\\dist\\',
        '\\.gradle\\', '\\target\\', '\\.idea\\', '\\.vscode\\',
        '\\.next\\', '\\.cache\\', '\\out\\'
    )
    $files = Get-ChildItem -Path $src -Recurse -Filter *.md -File -ErrorAction SilentlyContinue |
        Where-Object {
            $p = $_.FullName
            -not ($excludeDirs | Where-Object { $p -like "*$_*" })
        }
    foreach ($f in $files) {
        $rel  = $f.FullName.Substring($src.Length).TrimStart('\','/')
        $dest = Join-Path "$staging\$name" $rel
        $destDir = Split-Path $dest -Parent
        if (-not (Test-Path $destDir)) {
            New-Item -ItemType Directory -Path $destDir -Force | Out-Null
        }
        Copy-Item -LiteralPath $f.FullName -Destination $dest -Force
        $total++
    }
    Write-Host "  copied $($files.Count) files"
}

if (Test-Path $zip) { Remove-Item $zip -Force }
Write-Host "=== Compressing ==="
Compress-Archive -Path "$staging\*" -DestinationPath $zip -CompressionLevel Optimal

$mb = [math]::Round((Get-Item $zip).Length / 1MB, 2)
Write-Host "DONE: $total files staged, zip = $zip ($mb MB)"
