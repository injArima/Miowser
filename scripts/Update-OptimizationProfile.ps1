[CmdletBinding()]
param([Parameter(Mandatory)][string]$EvidenceDirectory)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $EvidenceDirectory).Path
$environment = Get-Content -LiteralPath (Join-Path $root 'environment.json') -Raw | ConvertFrom-Json
$result = Get-Content -LiteralPath (Join-Path $root 'result.json') -Raw | ConvertFrom-Json
if (-not $result.success) { throw 'Profile-generation test did not pass.' }
if ($environment.journey -ne 'profile' -or $environment.r8) {
    throw 'Use evidence from a successful unminified profile-generation run.'
}
$appSource = Join-Path (Split-Path -Parent $PSScriptRoot) 'app/src/main'
$profiles = @{}
foreach ($kind in @('baseline','startup')) {
    # Startup rules are also baseline rules. Macrobenchmark 1.5 emits a startup file
    # for includeInStartupProfile=true; merge it into both artifacts like the plugin does.
    $files = @(Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object {
        $_.Name -like "*-$kind-prof.txt" -or ($kind -eq 'baseline' -and $_.Name -like '*-startup-prof.txt')
    })
    if ($files.Count -eq 0) { throw "No $kind profile found." }
    $rules = @($files | ForEach-Object { Get-Content -LiteralPath $_.FullName } |
        Where-Object { $_ -match '^[HSP]*L' -and $_ -notmatch 'Lcom/miowser/app/benchmark/' } |
        Sort-Object -CaseSensitive -Unique)
    if ($rules.Count -eq 0) { throw "Empty $kind profile." }
    $profiles[$kind] = [string[]]$rules
}
# Validate both inputs before replacing either source artifact.
foreach ($kind in @('baseline','startup')) {
    $path = Join-Path $appSource "$kind-prof.txt"
    [IO.File]::WriteAllLines($path, $profiles[$kind], [Text.UTF8Encoding]::new($false))
    Write-Output "$path : $($profiles[$kind].Count) rules"
}
Write-Output 'Build the release APK and verify assets/dexopt/baseline.prof before claiming profile packaging.'
