<#
.SYNOPSIS
    Builds SafeTrack Watch and runs it on a Wear OS emulator (or watch).

.DESCRIPTION
    Finds adb, picks the connected Wear OS device (phones are skipped), builds the
    debug APK, installs it as an update (keeping the pairing and Watch ID) and
    launches the app.

.EXAMPLE
    .\run-emulator.ps1                 # build, install, launch
    .\run-emulator.ps1 -NoBuild        # reinstall the last build
    .\run-emulator.ps1 -Logs           # ...then follow the app's logs (Ctrl+C to stop)
    .\run-emulator.ps1 -Serial emulator-5556
#>
param(
    # adb serial of the target; default: the only connected Wear OS device.
    [string]$Serial,
    # Skip the Gradle build and install the existing APK.
    [switch]$NoBuild,
    # Follow the app's log output after launching.
    [switch]$Logs
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$package = 'com.safetrack.watch'
$activity = "$package/.app.MainActivity"
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'

function Find-Adb {
    $candidates = @()
    $props = Join-Path $root 'local.properties'
    if (Test-Path $props) {
        $line = Get-Content $props | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($line) {
            # Properties escaping: "C\:/Program Files" -> "C:/Program Files"
            $sdk = ($line -split '=', 2)[1].Trim() -replace '\\:', ':' -replace '\\\\', '\'
            $candidates += Join-Path $sdk 'platform-tools\adb.exe'
        }
    }
    foreach ($var in 'ANDROID_HOME', 'ANDROID_SDK_ROOT') {
        $value = [Environment]::GetEnvironmentVariable($var)
        if ($value) { $candidates += Join-Path $value 'platform-tools\adb.exe' }
    }
    $candidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'

    foreach ($path in $candidates) {
        if (Test-Path $path) { return $path }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    throw 'adb not found. Set sdk.dir in local.properties or ANDROID_HOME.'
}

function Get-ConnectedSerials([string]$adb) {
    @(& $adb devices | Select-Object -Skip 1 |
        Where-Object { $_ -match '^(\S+)\s+device$' } |
        ForEach-Object { $Matches[1] })
}

function Find-WearDevice([string]$adb) {
    $serials = Get-ConnectedSerials $adb
    if (-not $serials) { throw 'No emulator or device is connected. Start the Wear OS emulator first.' }

    $wear = @($serials | Where-Object {
        (& $adb -s $_ shell getprop ro.build.characteristics) -match 'watch'
    })
    if ($wear.Count -eq 0) { throw "No Wear OS device found among: $($serials -join ', ')" }
    if ($wear.Count -gt 1) { throw "Several Wear OS devices found ($($wear -join ', ')). Choose one with -Serial." }
    return $wear[0]
}

$adb = Find-Adb
if (-not $Serial) {
    $Serial = Find-WearDevice $adb
} elseif ((Get-ConnectedSerials $adb) -notcontains $Serial) {
    throw "Device '$Serial' is not connected. Check with: adb devices"
}
Write-Host "Target: $Serial" -ForegroundColor Green

if (-not $NoBuild) {
    Write-Host 'Building debug APK...' -ForegroundColor Green
    & (Join-Path $root 'gradlew.bat') -p $root assembleDebug --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
}
if (-not (Test-Path $apk)) { throw "APK not found at $apk. Run without -NoBuild." }

# -r updates in place, so the pairing and Watch ID are kept.
Write-Host 'Installing...' -ForegroundColor Green
& $adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed.' }

Write-Host 'Launching SafeTrack...' -ForegroundColor Green
& $adb -s $Serial shell am start -n $activity | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Launch failed.' }
Write-Host 'SafeTrack is running.' -ForegroundColor Green

if ($Logs) {
    Write-Host 'Following app logs (Ctrl+C to stop)...' -ForegroundColor Green
    & $adb -s $Serial logcat -c
    & $adb -s $Serial logcat -s SafeTrackApi DeviceRepository MonitoringService SosController SosRepository LocationTracker ShakeDetector AndroidRuntime:E
}
