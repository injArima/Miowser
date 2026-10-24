[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [ValidateSet('populatedStartup','libraryScroll','downloadsScroll','browserAndTabs','localPlayer','hubNavigation','hubDownloadLists','tabSwipeReversals','jumpToTopReversals','playerControlsAndOverlays','remoteSubtitleControls','all','profile')]
    [string]$Journey = 'populatedStartup',
    [ValidateSet(1,10,30)][int]$Tabs = 10,
    [ValidateSet(200,1000)][int]$Downloads = 200,
    [ValidateSet(500,1000,5000)][int]$Library = 500,
    [ValidateSet('http://127.0.0.1:8765')][string]$FixtureServer,
    [switch]$ActiveTransfers,
    [ValidateRange(1,100)][int]$Iterations = 20,
    [ValidateSet('None','BaselineProfile')][string]$Compilation = 'None',
    [switch]$R8,
    [switch]$EmulatorDiagnostic,
    [string]$SdkRoot = "$env:LOCALAPPDATA/Android/Sdk"
)
$ErrorActionPreference = 'Stop'
if ($Journey -eq 'profile' -and $R8) { throw 'Generate profiles with the unminified target; omit -R8.' }
if ($ActiveTransfers -and -not $FixtureServer) { throw 'Active transfers require the loopback fixture server.' }
if ($Journey -eq 'remoteSubtitleControls' -and -not $FixtureServer) { throw 'Remote subtitle measurements require the loopback fixture server.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$connected = @(& $adb devices | Where-Object { $_ -match '\sdevice$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($connected.Count -ne 1 -or $connected[0] -ne $Serial) {
    throw 'Connect only the explicitly selected device before running instrumented benchmarks.'
}
$isEmulator = ((& $adb -s $Serial shell getprop ro.kernel.qemu) -join '').Trim() -eq '1'
if ($isEmulator -and -not $EmulatorDiagnostic -and $Journey -ne 'profile') {
    throw 'Emulator measurements require -EmulatorDiagnostic and cannot establish shipping targets.'
}
$stamp = Get-Date -Format 'yyyyMMddTHHmmssfff'
$evidence = Join-Path $projectRoot ".local/optimization/evidence/step03/$stamp"
New-Item -ItemType Directory -Force $evidence | Out-Null
& (Join-Path $PSScriptRoot 'New-OptimizationCheckpoint.ps1') | ConvertTo-Json -Depth 5 |
    Set-Content (Join-Path $evidence 'source-checkpoint.json')
$oldSerial = $env:ANDROID_SERIAL
try {
    $env:ANDROID_SERIAL = $Serial
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
    $testClass = if ($Journey -eq 'profile') { 'com.miowser.benchmark.BaselineProfileGenerator' }
        elseif ($Journey -eq 'all') { 'com.miowser.benchmark.AppBenchmarks' }
        else { "com.miowser.benchmark.AppBenchmarks#$Journey" }
    $arguments = @(':benchmark:connectedBenchmarkAndroidTest', "-Pbenchmark.r8=$($R8.IsPresent.ToString().ToLowerInvariant())",
        "-Pandroid.testInstrumentationRunnerArguments.class=$testClass",
        "-Pandroid.testInstrumentationRunnerArguments.iterations=$Iterations",
        "-Pandroid.testInstrumentationRunnerArguments.compilation=$Compilation",
        "-Pandroid.testInstrumentationRunnerArguments.tabs=$Tabs",
        "-Pandroid.testInstrumentationRunnerArguments.downloads=$Downloads",
        "-Pandroid.testInstrumentationRunnerArguments.library=$Library")
    if ($FixtureServer) {
        $arguments += "-Pandroid.testInstrumentationRunnerArguments.fixture_server=$FixtureServer"
        $arguments += "-Pandroid.testInstrumentationRunnerArguments.active_transfers=$($ActiveTransfers.IsPresent.ToString().ToLowerInvariant())"
    }
    if ($EmulatorDiagnostic) { $arguments += '-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR' }
    [ordered]@{
        timestamp = $stamp; serial = $Serial; emulatorDiagnostic = $isEmulator; journey = $Journey
        tabs = $Tabs; downloads = $Downloads; library = $Library; history = 2000; fixture = 'artwork-media-v3-tall-tabs'
        fixtureServer = $FixtureServer; activeTransfers = $ActiveTransfers.IsPresent
        iterations = $(if ($Journey -eq 'profile') { $null } else { $Iterations })
        r8 = $R8.IsPresent; compilation = $(if ($Journey -eq 'profile') { 'Profile collection' } else { $Compilation })
        applicationId = 'com.miowser.app.benchmark'
        model = (& $adb -s $Serial shell getprop ro.product.model) -join ''
        sdk = (& $adb -s $Serial shell getprop ro.build.version.sdk) -join ''
        revision = (git -C $projectRoot rev-parse HEAD) -join ''
    } | ConvertTo-Json | Set-Content (Join-Path $evidence 'environment.json')
    & $adb -s $Serial shell dumpsys webviewupdate | Set-Content (Join-Path $evidence 'webview.txt')
    & $adb -s $Serial shell dumpsys display | Set-Content (Join-Path $evidence 'display.txt')
    & $adb -s $Serial shell dumpsys thermalservice | Set-Content (Join-Path $evidence 'thermal.txt')
    & $adb -s $Serial shell dumpsys battery | Set-Content (Join-Path $evidence 'battery-before.txt')
    & $adb -s $Serial shell settings get global low_power | Set-Content (Join-Path $evidence 'power-saving.txt')
    & $adb -s $Serial shell settings get system font_scale | Set-Content (Join-Path $evidence 'font-scale.txt')
    & $adb -s $Serial shell getprop persist.sys.locale | Set-Content (Join-Path $evidence 'locale.txt')
    & $adb -s $Serial shell cat /proc/meminfo | Set-Content (Join-Path $evidence 'memory.txt')
    git -C $projectRoot diff --binary | Set-Content (Join-Path $evidence 'working-tree.patch')
    Push-Location $projectRoot
    try {
        & ./gradlew.bat @arguments 2>&1 | Tee-Object (Join-Path $evidence 'gradle.log')
        $buildExit = $LASTEXITCODE
    } finally { Pop-Location }
    & $adb -s $Serial shell dumpsys battery | Set-Content (Join-Path $evidence 'battery-after.txt')
    & $adb -s $Serial shell dumpsys thermalservice | Set-Content (Join-Path $evidence 'thermal-after.txt')
    @{ success = ($buildExit -eq 0); exitCode = $buildExit } | ConvertTo-Json |
        Set-Content (Join-Path $evidence 'result.json')
    $outputs = Join-Path $projectRoot 'benchmark/build/outputs'
    if (Test-Path $outputs) { Copy-Item $outputs (Join-Path $evidence 'outputs') -Recurse }
    $apk = Join-Path $projectRoot 'app/build/outputs/apk/benchmark/app-benchmark.apk'
    if (Test-Path $apk) {
        Copy-Item $apk $evidence
        Get-FileHash $apk | Format-List | Out-File (Join-Path $evidence 'apk-sha256.txt')
    }
    $mapping = Join-Path $projectRoot 'app/build/outputs/mapping/benchmark'
    if ($R8 -and (Test-Path $mapping)) { Copy-Item $mapping (Join-Path $evidence 'mapping') -Recurse }
    if ($buildExit -ne 0) { throw "Benchmark failed. Evidence: $evidence" }
    Write-Output "Benchmark evidence: $evidence"
} finally { $env:ANDROID_SERIAL = $oldSerial }
