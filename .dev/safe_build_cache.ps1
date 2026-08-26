# Safe replacement for `./gradlew :kronos-boot:build_cache` / running tool-cache-packer.exe directly.
#
# tool-cache-packer.exe rebuilds the ENTIRE binary cache purely from the toml source
# tree. That means every run discards anything that only ever exists as a direct
# binary patch (custom NPCs/objects/items/placeholders added outside the toml
# pipeline), AND tool-cache-packer.exe itself has a reproducible race-condition bug
# that non-deterministically corrupts sprite archives (empty stubs, or two different
# ids ending up with identical/swapped pixel data) on every single run.
#
# This script runs the real packer, then diffs the result against a known-good
# "golden" cache snapshot (data zelus/data/cache_golden) and restores anything
# that's missing or differs -- so a rebuild is safe to run without silently
# reverting/breaking custom content.
#
# IMPORTANT:
#   - Stop the dev server before running this (it holds the cache files open).
#   - Restart the server after this finishes.
#   - If you intentionally change EXISTING toml content and want the new version
#     to stick, refresh the golden snapshot afterwards:
#       Copy-Item "..\data zelus\data\cache\main_file_cache.*" "..\data zelus\data\cache_golden\"
#     Otherwise this script will keep reverting that content back to golden.

$ErrorActionPreference = "Stop"

$devDir = $PSScriptRoot
$serverRoot = Split-Path -Parent $devDir
$dataRoot = Join-Path (Split-Path -Parent $serverRoot) "data zelus\data"
$cache = Join-Path $dataRoot "cache"
$golden = Join-Path $dataRoot "cache_golden"
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
$backup = Join-Path $dataRoot "cache_backup_pre_saferebuild_$ts"

if (-not (Test-Path $golden)) {
    Write-Error "No golden snapshot found at $golden -- cannot safely proceed. Restore a known-good cache there first."
    exit 1
}

Write-Host "Backing up current cache to $backup ..."
New-Item -ItemType Directory -Force -Path $backup | Out-Null
Copy-Item "$cache\main_file_cache.dat2" $backup
Copy-Item "$cache\main_file_cache.idx*" $backup

Write-Host "Running tool-cache-packer.exe ..."
Push-Location $serverRoot
& "$devDir\tool-cache-packer.exe" --in "$cache\toml" --out "$cache"
Pop-Location

Write-Host "Restoring anything the regen wiped or corrupted, from golden snapshot ..."
$restoreTool = Join-Path $devDir "cache-restore-tool"
$cp = (Get-Content (Join-Path $restoreTool "classpath.txt")) + ";" + (Join-Path $restoreTool "out")
& java -cp $cp SafeCacheRestore $cache $golden

Write-Host ""
Write-Host "Done. Restart the server now."
Write-Host "(Pre-rebuild backup kept at: $backup)"
