<#
.SYNOPSIS
Builds and deploys the Jarvix Android App to a connected device via ADB automatically.

.DESCRIPTION
Finds a Wi-Fi-connected phone, builds the APK, and installs + launches it.
Checks for an already-connected device first, since some phones stop broadcasting
themselves over mDNS once the Wireless Debugging screen isn't open, even while the
connection itself is still alive.

Make sure Wireless Debugging is toggled ON on your phone the first time you connect.
#>

# Navigate to the Android project root
cd "$PSScriptRoot\Jarvix"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

function Get-ConnectedWifiDevice {
    # Matches lines like "192.168.6.136:35711    device" - an already-authenticated Wi-Fi link.
    $lines = & $adb devices
    foreach ($line in $lines) {
        if ($line -match '^(\d{1,3}(\.\d{1,3}){3}:\d+)\s+device\s*$') {
            return $matches[1]
        }
    }
    return $null
}

Write-Host "Checking for an already-connected phone..." -ForegroundColor Cyan
$deviceAddress = Get-ConnectedWifiDevice

if ($deviceAddress) {
    Write-Host "Already connected to $deviceAddress." -ForegroundColor Green
} else {
    Write-Host "Not connected yet - searching for your phone on Wi-Fi (mDNS)..." -ForegroundColor Cyan
    $mdnsOutput = & $adb mdns services
    $deviceLine = $mdnsOutput | Select-String "_adb-tls-connect._tcp"

    if (-not $deviceLine) {
        Write-Host "Could not find your phone on Wi-Fi." -ForegroundColor Red
        Write-Host "On the phone: Settings > Developer options > Wireless debugging - make sure it's ON and the screen is open (some phones pause discovery when that screen is closed)." -ForegroundColor Yellow
        exit 1
    }

    # Extract IP and Port from the mDNS output line (the last column)
    $deviceAddress = ($deviceLine.ToString().Trim() -split "`t" | Select-Object -Last 1).Trim()
    Write-Host "Found device at $deviceAddress. Connecting..." -ForegroundColor Green
    & $adb connect $deviceAddress
}

Write-Host "Building Jarvix APK..." -ForegroundColor Cyan
.\gradlew assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed! See output above." -ForegroundColor Red
    exit 1
}

Write-Host "Installing APK on $deviceAddress..." -ForegroundColor Cyan
& $adb -s $deviceAddress install -r app\build\outputs\apk\debug\app-debug.apk

if ($LASTEXITCODE -ne 0) {
    Write-Host "Installation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Launching Jarvix on the device..." -ForegroundColor Cyan
& $adb -s $deviceAddress shell am start -n com.example.aisecretary/.MainActivity

Write-Host "Done!" -ForegroundColor Green
