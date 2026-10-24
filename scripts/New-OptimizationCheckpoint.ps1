[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$checkpointRoot = Join-Path $projectRoot '.local/optimization/checkpoints'
$checkpointPath = Join-Path $checkpointRoot ([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))
New-Item -ItemType Directory -Path $checkpointPath -Force | Out-Null

Push-Location $projectRoot
try {
    $revision = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the source revision.' }
    git status --porcelain=v1 | Set-Content -LiteralPath (Join-Path $checkpointPath 'git-status.txt') -Encoding utf8
    $patchPath = Join-Path $checkpointPath 'working-tree.patch'
    $stagedPath = Join-Path $checkpointPath 'staged.patch'
    $archivePath = Join-Path $checkpointPath 'head-source.zip'
    git diff --binary HEAD "--output=$patchPath"
    if ($LASTEXITCODE -ne 0) { throw 'Could not capture the working-tree patch.' }
    git diff --cached --binary "--output=$stagedPath"
    if ($LASTEXITCODE -ne 0) { throw 'Could not capture staged changes.' }
    git archive --format=zip "--output=$archivePath" HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Could not archive the base revision.' }

    # Complete current source snapshot, including relevant untracked documents/scripts.
    # Never copy arbitrary untracked screenshots, credentials, caches, or local.properties.
    $trackedFiles = @(git ls-files)
    if ($LASTEXITCODE -ne 0) { throw 'Could not enumerate tracked sources.' }
    $untrackedFiles = @(git ls-files --others --exclude-standard -- docs scripts app/src benchmark)
    if ($LASTEXITCODE -ne 0) { throw 'Could not enumerate untracked guide files.' }
    $sourceFiles = @($trackedFiles + $untrackedFiles | Sort-Object -Unique)
    $snapshotRoot = Join-Path $checkpointPath 'source'
    $manifest = foreach ($relativePath in $sourceFiles) {
        $sourcePath = Join-Path $projectRoot $relativePath
        if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) { continue }
        $targetPath = Join-Path $snapshotRoot $relativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $targetPath) -Force | Out-Null
        Copy-Item -LiteralPath $sourcePath -Destination $targetPath
        $originalHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
        $copiedHash = (Get-FileHash -LiteralPath $targetPath -Algorithm SHA256).Hash
        if ($originalHash -ne $copiedHash) { throw "Snapshot mismatch: $relativePath" }
        [pscustomobject]@{ Path = $relativePath; SHA256 = $copiedHash }
    }
    $manifest | Export-Csv -LiteralPath (Join-Path $checkpointPath 'source-manifest.csv') -NoTypeInformation -Encoding utf8
    $patchPath = Join-Path $checkpointPath 'working-tree.patch'
    if ((Get-Item -LiteralPath $patchPath).Length -gt 0) {
        git apply --reverse --check $patchPath
        if ($LASTEXITCODE -ne 0) { throw 'Patch does not match the captured working tree.' }
    }
    $metadata = [ordered]@{
        CreatedUtc = [DateTime]::UtcNow.ToString('o')
        Head = $revision
        FileCount = @($manifest).Count
        PatchSHA256 = (Get-FileHash -LiteralPath $patchPath -Algorithm SHA256).Hash
        ManifestSHA256 = (Get-FileHash -LiteralPath (Join-Path $checkpointPath 'source-manifest.csv') -Algorithm SHA256).Hash
        Snapshot = 'source contains current tracked files and untracked docs/scripts/app sources/benchmark module; ignored outputs, local device data and secrets excluded'
        Verified = 'Every copied file SHA256 checked; working-tree patch reverse-check passed'
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $checkpointPath 'checkpoint.json') -Encoding utf8
    [pscustomobject]@{ Checkpoint = $checkpointPath; Metadata = $metadata }
} finally {
    Pop-Location
}
