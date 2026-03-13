$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:USERPROFILE\AppData\Local\Android\Sdk" }
$ndkDir = Get-ChildItem "$env:ANDROID_HOME\ndk" -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
$env:ANDROID_NDK_HOME = if ($env:ANDROID_NDK_HOME) { $env:ANDROID_NDK_HOME } else { $ndkDir.FullName }

if (-not (Test-Path $env:ANDROID_NDK_HOME)) {
    Write-Error "Android NDK not found. Set ANDROID_NDK_HOME or install via SDK Manager."
    exit 1
}

Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "ANDROID_NDK_HOME=$env:ANDROID_NDK_HOME"

# Ensure gomobile
if (-not (Get-Command gomobile -ErrorAction SilentlyContinue)) {
    Write-Host "Installing gomobile..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    go install golang.org/x/mobile/cmd/gobind@latest
}

gomobile init

Write-Host "Building phonxcore.aar..."
gomobile bind `
    -v `
    -mod=vendor `
    -tags "PSIPHON_DISABLE_INPROXY" `
    -target=android/arm,android/arm64 `
    -androidapi 24 `
    -ldflags="-s -w" `
    -o ..\app\libs\phonxcore.aar `
    ./

Write-Host ""
Write-Host "Built phonxcore.aar -> ..\app\libs\phonxcore.aar"
Get-Item ..\app\libs\phonxcore.aar | Select-Object Name, Length
