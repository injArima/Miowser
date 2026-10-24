param(
    [Parameter(Mandatory = $true)][string]$Keystore,
    [Parameter(Mandatory = $true)][string]$Alias,
    [string]$Bundle = "app/build/outputs/bundle/release/app-release.aab",
    [string]$Output = "app/release/miowser-play-upload-signed.aab"
)

$ErrorActionPreference = "Stop"
if (-not $env:MIOWSER_KEYSTORE_PASSWORD -or -not $env:MIOWSER_KEY_PASSWORD) {
    throw "Set MIOWSER_KEYSTORE_PASSWORD and MIOWSER_KEY_PASSWORD in this shell."
}
$keystorePath = (Resolve-Path -LiteralPath $Keystore).Path
$bundlePath = (Resolve-Path -LiteralPath $Bundle).Path
$outputPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Output))
$outputDirectory = [System.IO.Path]::GetDirectoryName($outputPath)
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$jarsigner = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin/jarsigner.exe"
} else {
    (Get-Command jarsigner.exe -ErrorAction Stop).Source
}
if (-not (Test-Path -LiteralPath $jarsigner)) { throw "jarsigner.exe was not found." }
if (Test-Path -LiteralPath $outputPath) { throw "Output already exists: $outputPath" }

& $jarsigner -keystore $keystorePath -storepass:env MIOWSER_KEYSTORE_PASSWORD `
    -keypass:env MIOWSER_KEY_PASSWORD -signedjar $outputPath $bundlePath $Alias
if ($LASTEXITCODE -ne 0) { throw "Bundle signing failed." }
& $jarsigner -verify -certs $outputPath
if ($LASTEXITCODE -ne 0) { throw "Signed bundle verification failed." }
Write-Host "Signed Play upload bundle: $outputPath"
Get-FileHash -Algorithm SHA256 -LiteralPath $outputPath | Select-Object Path, Hash
