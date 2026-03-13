#!/bin/bash
# Build phonxcore.aar by running the build inside the phonxcore module itself,
# avoiding gomobile's vendor-mode bug. The generated gobind code is placed
# under phonxcore/cmd/gobind/ so it can use -mod=vendor natively.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT="$PROJECT_DIR/app/libs/phonxcore.aar"

# Android env — Unix paths for shell, Windows paths for Go tools
ANDROID_HOME_U="${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}"
NDK_VER=$(ls "$ANDROID_HOME_U/ndk/" 2>/dev/null | sort -V | tail -1)
ANDROID_NDK_U="$ANDROID_HOME_U/ndk/$NDK_VER"
NDK_TOOLCHAIN_U="$ANDROID_NDK_U/toolchains/llvm/prebuilt/windows-x86_64/bin"

# Windows paths for Go/NDK
export ANDROID_HOME="$(cygpath -w "$ANDROID_HOME_U")"
export ANDROID_NDK_HOME="$(cygpath -w "$ANDROID_NDK_U")"
export GOTOOLCHAIN=go1.25.7
export PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH"
export MSYS_NO_PATHCONV=1

echo "ANDROID_HOME=$ANDROID_HOME"
echo "ANDROID_NDK_HOME=$ANDROID_NDK_HOME"

# Temp dir — Unix path for shell ops, Windows path for Go tools
WORK_U="$(MSYS_NO_PATHCONV=0 mktemp -d)"
WORK_W="$(cygpath -w "$WORK_U")"
trap 'rm -rf "$WORK_U" "$SCRIPT_DIR/cmd"' EXIT
echo "WORK=$WORK_W"

# ── Step 1: Generate bindings with gobind ──
echo ""
echo "==> Step 1: Running gobind..."
cd "$SCRIPT_DIR"
GOFLAGS="-mod=readonly" go run golang.org/x/mobile/cmd/gobind@v0.0.0-20260204172633-1dceadbbeea3 \
  -lang=go,java -outdir="$WORK_W" -tags=PSIPHON_DISABLE_INPROXY phonxcore

echo "    Generated bindings OK"

# ── Step 2: Place gobind Go code inside phonxcore module ──
echo ""
echo "==> Step 2: Setting up gobind code inside module..."
GOBIND_DIR="$SCRIPT_DIR/cmd/gobind"
mkdir -p "$GOBIND_DIR"

# Copy generated Go/C/H files into phonxcore/cmd/gobind/
find "$WORK_U/src/gobind" -maxdepth 1 -type f \( -name '*.go' -o -name '*.c' -o -name '*.h' \) \
  -exec cp {} "$GOBIND_DIR/" \;

echo "    Placed $(ls "$GOBIND_DIR" | wc -l) files in cmd/gobind/"

# ── Step 3: Build .so for each architecture ──
for arch in arm arm64; do
  echo ""
  echo "==> Step 3: Building android/$arch..."

  case "$arch" in
    arm)
      CC_BIN="$NDK_TOOLCHAIN_U/armv7a-linux-androideabi24-clang"
      CXX_BIN="$NDK_TOOLCHAIN_U/armv7a-linux-androideabi24-clang++"
      JNI_DIR="armeabi-v7a"
      EXTRA_GOARM="7"
      ;;
    arm64)
      CC_BIN="$NDK_TOOLCHAIN_U/aarch64-linux-android24-clang"
      CXX_BIN="$NDK_TOOLCHAIN_U/aarch64-linux-android24-clang++"
      JNI_DIR="arm64-v8a"
      EXTRA_GOARM=""
      ;;
  esac

  JNIDIR_U="$WORK_U/aar/jni/$JNI_DIR"
  JNIDIR_W="$(mkdir -p "$JNIDIR_U" && cygpath -w "$JNIDIR_U")"

  (
    cd "$SCRIPT_DIR"
    export GOOS=android
    export GOARCH=$arch
    export CGO_ENABLED=1
    export CC="$(cygpath -w "$CC_BIN")"
    export CXX="$(cygpath -w "$CXX_BIN")"
    [ -n "$EXTRA_GOARM" ] && export GOARM="$EXTRA_GOARM"

    # Add -extldflags '-Wl,-z,max-page-size=16384' for 16 KB page support
    go build -mod=vendor -tags PSIPHON_DISABLE_INPROXY \
      -trimpath -ldflags="-s -w -extldflags '-Wl,-z,max-page-size=16384'" \
      -buildmode=c-shared \
      -o "$JNIDIR_W\\libgojni.so" \
      ./cmd/gobind
  )
  echo "    Built: $JNI_DIR/libgojni.so ($(du -h "$JNIDIR_U/libgojni.so" | cut -f1))"
done

# ── Step 4: Package AAR ──
echo ""
echo "==> Step 4: Packaging AAR..."
AAR_BUILD_U="$WORK_U/aar"

# AndroidManifest.xml
cat > "$AAR_BUILD_U/AndroidManifest.xml" << 'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="go">
</manifest>
XML

# Compile generated Java sources into classes.jar
echo "    Compiling Java sources..."
ANDROID_JAR=""
for plat in $(ls -rd "$ANDROID_HOME_U/platforms/android-"* 2>/dev/null); do
  if [ -f "$plat/android.jar" ]; then
    ANDROID_JAR="$plat/android.jar"
    break
  fi
done
[ -z "$ANDROID_JAR" ] && { echo "ERROR: android.jar not found"; exit 1; }

CLASSES_DIR_U="$WORK_U/classes"
mkdir -p "$CLASSES_DIR_U"

# javac needs Windows-style paths in the sources file
find "$WORK_U/java" -name "*.java" | while read f; do cygpath -w "$f"; done > "$WORK_U/sources.txt"
javac -source 8 -target 8 \
  -classpath "$(cygpath -w "$ANDROID_JAR")" \
  -d "$(cygpath -w "$CLASSES_DIR_U")" \
  @"$(cygpath -w "$WORK_U/sources.txt")"

(cd "$CLASSES_DIR_U" && jar cf "$(cygpath -w "$AAR_BUILD_U/classes.jar")" .)

# Create AAR zip
mkdir -p "$(dirname "$OUTPUT")"
(cd "$AAR_BUILD_U" && jar cf "$(cygpath -w "$OUTPUT")" AndroidManifest.xml classes.jar jni)

echo ""
echo "==> Done!"
ls -lh "$OUTPUT"
