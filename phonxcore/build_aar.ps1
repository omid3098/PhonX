$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# ── Environment Setup ──
$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:USERPROFILE\AppData\Local\Android\Sdk" }
$ndkDir = Get-ChildItem "$env:ANDROID_HOME\ndk" -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
$env:ANDROID_NDK_HOME = if ($env:ANDROID_NDK_HOME) { $env:ANDROID_NDK_HOME } else { $ndkDir.FullName }

if (-not (Test-Path $env:ANDROID_NDK_HOME)) {
    Write-Error "Android NDK not found. Set ANDROID_NDK_HOME or install via SDK Manager."
    exit 1
}

# Add Java (jbr) to path for javac and jar
$jbrPath = "C:\Program Files\Android\Android Studio\jbr\bin"
if (Test-Path $jbrPath) { $env:PATH = "$jbrPath;$env:PATH" }

$outputPath = Join-Path $PSScriptRoot "..\app\libs\phonxcore.aar"
$workDir = Join-Path $env:TEMP "phonxbuild-$([Guid]::NewGuid().ToString().Substring(0,8))"
$gobindDir = Join-Path $PSScriptRoot "cmd\gobind"

New-Item -ItemType Directory -Path $workDir -Force | Out-Null
New-Item -ItemType Directory -Path $gobindDir -Force | Out-Null

try {
    Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
    Write-Host "ANDROID_NDK_HOME=$env:ANDROID_NDK_HOME"

    # ── Step 1: Generate bindings with gobind ──
    Write-Host "`n==> Step 1: Running gobind..."
    $env:GOFLAGS = "-mod=readonly"
    # Quote arguments to prevent PowerShell parser issues with commas
    & go run golang.org/x/mobile/cmd/gobind@v0.0.0-20260204172633-1dceadbbeea3 `
      -lang="go,java" -outdir="$workDir" -tags="PSIPHON_DISABLE_INPROXY" phonxcore

    # ── Step 2: Set up gobind code inside module ──
    Write-Host "==> Step 2: Setting up gobind code..."
    Copy-Item (Join-Path $workDir "src\gobind\*") $gobindDir -Include *.go, *.c, *.h -Force

    # ── Step 3: Build .so for each architecture ──
    $archs = @(
        @{ Arch = "arm"; JniDir = "armeabi-v7a"; CC = "armv7a-linux-androideabi24-clang.cmd"; GOARM = "7" },
        @{ Arch = "arm64"; JniDir = "arm64-v8a"; CC = "aarch64-linux-android24-clang.cmd" }
    )

    $toolchainBin = Join-Path $env:ANDROID_NDK_HOME "toolchains\llvm\prebuilt\windows-x86_64\bin"

    foreach ($a in $archs) {
        Write-Host "`n==> Step 3: Building android/$($a.Arch)..."
        $jniPath = Join-Path $workDir "aar\jni\$($a.JniDir)"
        New-Item -ItemType Directory -Path $jniPath -Force | Out-Null

        $env:GOOS = "android"
        $env:GOARCH = $a.Arch
        $env:CGO_ENABLED = "1"
        $env:CC = Join-Path $toolchainBin $a.CC
        $env:CXX = Join-Path $toolchainBin ($a.CC -replace "clang", "clang++")
        if ($a.GOARM) { $env:GOARM = $a.GOARM } else { Remove-Item Env:GOARM -ErrorAction SilentlyContinue }

        # Use vendor mode for the actual build
        $env:GOFLAGS = "-mod=vendor"

        & go build -tags "PSIPHON_DISABLE_INPROXY" `
          -trimpath -ldflags="-s -w -extldflags '-Wl,-z,max-page-size=16384'" `
          -buildmode=c-shared `
          -o (Join-Path $jniPath "libgojni.so") `
          ./cmd/gobind
    }

    # ── Step 4: Package AAR ──
    Write-Host "`n==> Step 4: Packaging AAR..."
    $aarBuild = Join-Path $workDir "aar"

    # Create AndroidManifest.xml
    Set-Content -Path (Join-Path $aarBuild "AndroidManifest.xml") -Value '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="go"></manifest>'

    # Find android.jar
    $platDir = Get-ChildItem "$env:ANDROID_HOME\platforms\android-*" | Sort-Object Name -Descending | Select-Object -First 1
    $androidJar = Join-Path $platDir.FullName "android.jar"

    # Compile Java
    $classesDir = Join-Path $workDir "classes"
    New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
    $javaSources = Get-ChildItem (Join-Path $workDir "java") -Filter *.java -Recurse | ForEach-Object { $_.FullName }
    Set-Content -Path (Join-Path $workDir "sources.txt") -Value $javaSources

    Write-Host "    Compiling Java sources..."
    & javac -source 8 -target 8 -classpath $androidJar -d $classesDir "@$($workDir)\sources.txt"

    # Create classes.jar
    $oldPwd = $pwd
    Set-Location $classesDir
    & jar cf (Join-Path $aarBuild "classes.jar") .
    Set-Location $oldPwd

    # Create AAR
    if (!(Test-Path (Split-Path $outputPath))) { New-Item -ItemType Directory -Path (Split-Path $outputPath) -Force }
    Set-Location $aarBuild
    & jar cf $outputPath AndroidManifest.xml classes.jar jni
    Set-Location $oldPwd

    Write-Host "`nSuccessfully built phonxcore.aar -> $outputPath"
    Get-Item $outputPath | Select-Object Name, Length
}
finally {
    # Cleanup
    Set-Location $PSScriptRoot
    if (Test-Path $workDir) { Remove-Item -Path $workDir -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path $gobindDir) { Remove-Item -Path $gobindDir -Recurse -Force -ErrorAction SilentlyContinue }
}
