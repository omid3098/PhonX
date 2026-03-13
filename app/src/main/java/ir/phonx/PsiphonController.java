package ir.phonx;

import android.content.Context;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import phonxcore.Phonxcore;
import phonxcore.PsiphonCallbackHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Controls the Psiphon tunnel via phonxcore (combined Go module).
 *
 * Psiphon provides obfuscated transport to bypass DPI.
 * When active, it exposes a local SOCKS proxy that Xray routes through
 * via {@code sockopt.dialerProxy}.
 *
 * API (from phonxcore):
 *   Phonxcore.newPsiphonController(PsiphonCallbackHandler) → phonxcore.PsiphonController
 *   controller.start(String configJson, String dataDir) throws Exception
 *   controller.stop()
 *   controller.getIsRunning() → boolean
 *   controller.getSOCKSPort() → int
 */
public class PsiphonController {

    private static final String TAG = "PsiphonController";
    private static final long CONNECT_TIMEOUT_SECONDS = 60;

    private final Context context;
    private phonxcore.PsiphonController goController;
    private int socksPort;
    private StatusListener statusListener;

    /** Listener for Psiphon lifecycle events. */
    public interface StatusListener {
        void onConnecting();
        void onConnected(int socksPort);
        void onDisconnected();
        void onError(String message);
    }

    public PsiphonController(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
    }

    /**
     * Starts the Psiphon tunnel. Blocks until connected or timeout (60s).
     *
     * @param vpnService the VpnService instance for BindToDevice (protect fd)
     * @return the local SOCKS proxy port
     * @throws Exception if tunnel fails to connect within timeout
     */
    public int start(VpnService vpnService) throws Exception {
        stop();

        String configJson = loadPsiphonConfig();
        String dataDir = context.getFilesDir().getAbsolutePath() + "/psiphon";

        // Ensure data directory exists
        java.io.File dataDirFile = new java.io.File(dataDir);
        if (!dataDirFile.exists()) {
            dataDirFile.mkdirs();
        }

        CountDownLatch connectedLatch = new CountDownLatch(1);
        final Exception[] startError = {null};

        PsiphonCallback callback = new PsiphonCallback(vpnService, connectedLatch, startError);
        goController = Phonxcore.newPsiphonController(callback);

        if (statusListener != null) statusListener.onConnecting();

        Log.i(TAG, "Starting Psiphon tunnel...");
        goController.start(configJson, dataDir);

        // Block until connected or timeout
        boolean connected = connectedLatch.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (startError[0] != null) {
            stop();
            throw startError[0];
        }

        if (!connected) {
            stop();
            throw new Exception("Psiphon tunnel connect timeout (" + CONNECT_TIMEOUT_SECONDS + "s)");
        }

        socksPort = goController.getSOCKSPort();
        Log.i(TAG, "Psiphon connected, SOCKS port=" + socksPort);
        return socksPort;
    }

    public void stop() {
        if (goController == null) return;
        try {
            goController.stop();
        } catch (Throwable t) {
            Log.w(TAG, "stop: " + t.getMessage());
        }
        goController = null;
        socksPort = 0;
        Log.i(TAG, "Psiphon stopped");
    }

    public boolean isRunning() {
        return goController != null && goController.getIsRunning();
    }

    public int getSocksPort() {
        return socksPort;
    }

    private String loadPsiphonConfig() throws IOException {
        InputStream is = context.getAssets().open("psiphon_config.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    // ── PsiphonCallbackHandler implementation ────────────────────────────────

    private class PsiphonCallback implements PsiphonCallbackHandler {

        private final VpnService vpnService;
        private final CountDownLatch connectedLatch;
        private final Exception[] errorHolder;

        PsiphonCallback(VpnService vpnService, CountDownLatch latch, Exception[] errorHolder) {
            this.vpnService = vpnService;
            this.connectedLatch = latch;
            this.errorHolder = errorHolder;
        }

        @Override
        public void onConnecting() {
            Log.d(TAG, "Psiphon: connecting");
            if (statusListener != null) statusListener.onConnecting();
        }

        @Override
        public void onConnected(long socksPort) {
            Log.i(TAG, "Psiphon: connected, SOCKS port=" + socksPort);
            PsiphonController.this.socksPort = (int) socksPort;
            if (statusListener != null) statusListener.onConnected((int) socksPort);
            connectedLatch.countDown();
        }

        @Override
        public void onHomepage(String url) {
            Log.d(TAG, "Psiphon: homepage=" + url);
        }

        @Override
        public void onDisconnected() {
            Log.i(TAG, "Psiphon: disconnected");
            if (statusListener != null) statusListener.onDisconnected();
        }

        @Override
        public void onError(String message) {
            Log.e(TAG, "Psiphon: error=" + message);
            errorHolder[0] = new Exception("Psiphon error: " + message);
            if (statusListener != null) statusListener.onError(message);
            connectedLatch.countDown(); // unblock start() so it can throw
        }

        @Override
        public void bindToDevice(long fileDescriptor) throws Exception {
            boolean ok = vpnService.protect((int) fileDescriptor);
            if (!ok) {
                throw new Exception("VpnService.protect() failed for fd=" + fileDescriptor);
            }
        }
    }
}
