$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot "..")

$env:JAVA_HOME   = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\Omid\AppData\Local\Android\Sdk"
$env:PATH        = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

Write-Host "Building PhonX debug APKs..."
& ".\gradlew.bat" assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "`nOutput APKs:"
Get-ChildItem "app\build\outputs\apk\debug\*.apk" |
    ForEach-Object { "{0,10} KB  {1}" -f [math]::Round($_.Length/1KB), $_.Name }
