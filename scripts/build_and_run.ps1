$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot "..")

$env:JAVA_HOME    = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\Omid\AppData\Local\Android\Sdk"
$env:PATH         = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
$ADB              = "$env:ANDROID_HOME\platform-tools\adb.exe"

# Build
Write-Host "Building PhonX debug APKs..."
& ".\gradlew.bat" assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Detect first authorized device
$device = & $ADB devices |
    Select-String '^\S+\s+device$' |
    Select-Object -First 1 |
    ForEach-Object { ($_ -split '\s+')[0] }
if (-not $device) { Write-Error "No authorized ADB device found."; exit 1 }
Write-Host "Device: $device"

# Detect ABI
$abi = (& $ADB -s $device shell getprop ro.product.cpu.abi).Trim()
Write-Host "ABI: $abi"

# Select APK
$apk = switch ($abi) {
    "arm64-v8a"   { "app\build\outputs\apk\debug\app-arm64-v8a-debug.apk" }
    "armeabi-v7a" { "app\build\outputs\apk\debug\app-armeabi-v7a-debug.apk" }
    default       { Write-Warning "Unknown ABI '$abi', falling back to arm64-v8a"
                    "app\build\outputs\apk\debug\app-arm64-v8a-debug.apk" }
}

# Install
Write-Host "Installing $apk..."
& $ADB -s $device install -r $apk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Launch
Write-Host "Launching ir.phonx/.MainActivity..."
& $ADB -s $device shell am start -n ir.phonx/.MainActivity
Write-Host "Done."
