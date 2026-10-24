[CmdletBinding()]
param(
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [int]$Port = 5580,
    [ValidateSet('auto', 'swiftshader')]
    [string]$GpuMode = 'auto'
)

$ErrorActionPreference = 'Stop'
if ($Port -lt 5554 -or $Port -gt 5682 -or ($Port % 2) -ne 0) { throw 'Use an even emulator port between 5554 and 5682.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$avdRoot = Join-Path $projectRoot '.local/optimization/avd'
$avdName = 'Miowser_Optimization_API37_1'
$avdPath = Join-Path $avdRoot "$avdName.avd"
$imagePath = Join-Path $SdkRoot 'system-images/android-37.1/google_apis_playstore_ps16k/x86_64'
if (-not (Test-Path -LiteralPath (Join-Path $imagePath 'system.img'))) { throw "Required installed system image is missing: $imagePath" }
$emulator = Join-Path $SdkRoot 'emulator/emulator.exe'
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$deviceListing = & $adb devices
if ($LASTEXITCODE -ne 0) { throw 'Could not list connected devices.' }
if ($deviceListing -match "emulator-$Port\s") { throw "Port $Port already has a device. Verify its identity before using it; no device was changed." }
New-Item -ItemType Directory -Path $avdPath -Force | Out-Null
$configPath = Join-Path $avdPath 'config.ini'
if (-not (Test-Path -LiteralPath $configPath)) {
    @"
AvdId=$avdName
avd.ini.displayname=Miowser disposable optimization test
avd.ini.encoding=UTF-8
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.device.name=medium_phone
hw.device.manufacturer=Generic
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.gpu.enabled=yes
hw.gpu.mode=$GpuMode
hw.keyboard=yes
hw.mainKeys=no
hw.battery=yes
hw.audioInput=no
hw.camera.back=none
hw.camera.front=none
hw.sdCard=no
disk.dataPartition.size=4G
image.sysdir.1=$imagePath\
tag.id=google_apis_playstore
tag.display=Google APIs PlayStore
tag.ids=google_apis_playstore,page_size_16kb
tag.displaynames=Google APIs PlayStore,Page Size 16KB
PlayStore.enabled=true
target=android-37.1
fastboot.forceColdBoot=yes
fastboot.forceFastBoot=no
showDeviceFrame=no
"@ | Set-Content -LiteralPath $configPath -Encoding utf8
}
@"
avd.ini.encoding=UTF-8
path=$avdPath
target=android-37.1
"@ | Set-Content -LiteralPath (Join-Path $avdRoot "$avdName.ini") -Encoding utf8
$env:ANDROID_AVD_HOME = $avdRoot
$logRoot = Join-Path $projectRoot '.local/optimization/evidence/step01'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$process = Start-Process -FilePath $emulator -ArgumentList @('-avd', $avdName, '-port', $Port, '-no-window', '-no-audio', '-no-snapshot', '-no-boot-anim', '-gpu', $GpuMode) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logRoot 'emulator-stdout.log') -RedirectStandardError (Join-Path $logRoot 'emulator-stderr.log')
[pscustomobject]@{ ProcessId = $process.Id; Serial = "emulator-$Port"; Avd = $avdName; DataDirectory = $avdPath; Logs = $logRoot }
