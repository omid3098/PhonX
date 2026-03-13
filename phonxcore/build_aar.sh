#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Android environment
export ANDROID_HOME="${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}"
NDK_DIR=$(ls -d "$ANDROID_HOME/ndk/"* 2>/dev/null | sort -V | tail -1)
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$NDK_DIR}"

if [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "ERROR: Android NDK not found. Set ANDROID_NDK_HOME or install via SDK Manager."
    exit 1
fi

echo "ANDROID_HOME=$ANDROID_HOME"
echo "ANDROID_NDK_HOME=$ANDROID_NDK_HOME"

# Ensure gomobile is installed
if ! command -v gomobile &>/dev/null; then
    echo "Installing gomobile..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    go install golang.org/x/mobile/cmd/gobind@latest
fi

gomobile init

echo "Building phonxcore.aar..."
gomobile bind \
    -v \
    -mod=vendor \
    -tags "PSIPHON_DISABLE_INPROXY" \
    -target=android/arm,android/arm64 \
    -androidapi 24 \
    -ldflags="-s -w" \
    -o ../app/libs/phonxcore.aar \
    ./

echo ""
echo "Built phonxcore.aar -> ../app/libs/phonxcore.aar"
ls -lh ../app/libs/phonxcore.aar
