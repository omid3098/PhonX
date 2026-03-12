# PhonX

A minimalist single-button Android VPN app designed to bypass DPI (Deep Packet Inspection) in Iran. Built for elderly, non-technical users — one tap to connect, nothing else to configure except pasting a server URI.

## Intended Architecture

The goal is a **proxy chain** that makes traffic unrecognizable to DPI systems:

```
Device Apps → TUN (VpnService) → Psiphon → Xray (VLESS/VMESS + TLS/WS) → VPS → Internet
```

- **Psiphon** handles the TUN interface, applies its own obfuscation, and routes upstream through Xray
- **Xray** encrypts and obfuscates traffic via VLESS/VMESS with TLS + WebSocket or Reality
- The app itself is excluded from the tunnel (`addDisallowedApplication`) to prevent routing loops

## Current Architecture (Psiphon disabled)

```
Device Apps → TUN (VpnService) → Xray (VLESS/VMESS + TLS/WS) → VPS → Internet
```

Psiphon is currently stubbed out. Both `psiphon-tunnel-core.aar` and `libv2ray.aar` are gomobile builds that export the same native library name (`libgojni.so`) and cannot coexist in one APK. Until this is resolved (e.g. by building a merged native binary), DPI bypass relies entirely on Xray's transport-layer obfuscation.

## Features

- Supports **VLESS** and **VMESS** proxy protocols
- Transport options: WebSocket, gRPC, TCP — with TLS or Reality
- System-wide VPN (all apps tunnelled)
- Foreground service with persistent notification
- ABI-split APKs (arm64-v8a, armeabi-v7a) for smaller download size
- Minimum Android 7.0 (API 24)

## Size Goal

Target APK size is **under 10 MB** (ideally ~5 MB) — large files are impractical to download under heavy throttling. The current release APK is ~32 MB due to the full Xray binary. Reaching the target requires a custom minimal Xray build with unused protocols stripped.

## Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Latest (JBR bundled) |
| Android SDK | API 36 |
| AGP | 9.1.0 |
| Gradle | 9.3.1 |

The library `app/libs/libv2ray.aar` (v26.3.9) must be present. It is not included in the repository due to size (~54 MB). Download from [AndroidLibXrayLite releases](https://github.com/2dust/AndroidLibXrayLite/releases) and place it at `app/libs/libv2ray.aar`.

## Build

From PowerShell at the project root:

```powershell
.\scripts\build.ps1
```

To build, install, and launch on a connected device:

```powershell
.\scripts\build_and_run.ps1
```

Output APKs are written to `app/build/outputs/apk/debug/`.

> **First run:** if PowerShell blocks the scripts, run once:
> `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`

## Server Configuration

The app accepts standard **VLESS** and **VMESS** URI formats.

**VLESS:**
```
vless://UUID@host:port?security=tls&type=ws&path=/path&host=sni.example.com#name
```

**VMESS:**
```
vmess://BASE64_ENCODED_JSON
```

Paste the URI in the Settings screen (gear icon → top-right). The URI is validated on save and persisted locally.

## Project Structure

```
PhonX/
├── app/
│   ├── libs/
│   │   └── libv2ray.aar              # Xray core (add manually)
│   └── src/main/
│       ├── java/ir/phonx/
│       │   ├── MainActivity.java         # Connect/disconnect UI
│       │   ├── SettingsActivity.java     # Server URI input
│       │   ├── PhonXVpnService.java      # VpnService, TUN setup
│       │   ├── XrayController.java       # Xray core wrapper
│       │   ├── ConfigParser.java         # vless:// and vmess:// parser
│       │   ├── ConfigStorage.java        # SharedPreferences wrapper
│       │   └── PsiphonController.java    # Stub (pending libgojni.so conflict fix)
│       └── AndroidManifest.xml
├── scripts/
│   ├── build.ps1                     # Build only
│   └── build_and_run.ps1            # Build + install + launch
├── idea.md                           # Original design document
└── README.md
```

## Architecture Notes

**TUN file descriptor handoff:**
`PhonXVpnService` builds the VPN interface via `VpnService.Builder`, calls `establish()`, detaches the raw fd with `detachFd()`, and passes it to `XrayController.start()`. Xray takes ownership of the fd and processes all traffic in-process — no separate tun2socks process needed.

**Loop prevention:**
`VpnService.Builder.addDisallowedApplication(getPackageName())` excludes the app's own traffic from the tunnel so Xray's outbound connections are not re-routed back into the TUN.

**Psiphon re-integration options:**
- Build a single merged `.so` that links both Psiphon and Xray (requires custom gomobile build)
- Replace Psiphon with [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) (C-based, <1 MB, no conflict)
- Use [xjasonlyu/tun2socks](https://github.com/xjasonlyu/tun2socks) as an intermediary

## Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | Proxy outbound traffic |
| `FOREGROUND_SERVICE` | Keep VPN alive in background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required on Android 14+ for VPN foreground service |
| `RECEIVE_BOOT_COMPLETED` | Reserved for auto-start (not yet implemented) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent system from killing the VPN service |

## APK Sizes (release build)

| ABI | Current | Target |
|-----|---------|--------|
| arm64-v8a | ~32 MB | <10 MB |
| armeabi-v7a | ~30 MB | <10 MB |

Size is dominated by `libgojni.so` from the Xray core. Reaching the target requires a custom minimal Xray build with unused protocols (Shadowsocks, Trojan, WireGuard, etc.) stripped out.
