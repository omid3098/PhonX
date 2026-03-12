package ir.phonx;

import android.content.Context;
import android.util.Log;

/**
 * Psiphon stub — currently disabled.
 *
 * IMPORTANT: ca.psiphon.aar and libv2ray.aar both ship a native library named
 * "libgojni.so" (both are gomobile-built Go programs). They CANNOT coexist in
 * the same APK. Until one of the libraries is renamed or a separate process
 * is used, Psiphon is excluded from the build.
 *
 * DPI bypass is currently achieved solely via Xray's transport layer:
 *   - vless+ws+tls  → traffic looks like HTTPS/WebSocket
 *   - vless+reality → traffic looks like native TLS to a real domain
 *   - vmess+ws+tls  → same obfuscation as above
 */
public class PsiphonController {

    private static final String TAG = "PsiphonController";

    public PsiphonController(Context context) {
        // stub
    }

    public void start(String upstreamSocksProxy) {
        Log.i(TAG, "Psiphon is disabled (native lib conflict with libv2ray). Skipping.");
    }

    public void stop() {
        // nothing to stop
    }

    public boolean isRunning() {
        return false;
    }
}
