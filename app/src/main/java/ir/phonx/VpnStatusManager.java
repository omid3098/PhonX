package ir.phonx;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

public class VpnStatusManager {
    public interface Listener {
        void onVpnStatusChanged(String status, String ip, String country, int attempt, int total, String configName, String message);
    }

    private static VpnStatusManager instance;
    private final List<Listener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized VpnStatusManager getInstance() {
        if (instance == null) instance = new VpnStatusManager();
        return instance;
    }

    public synchronized void register(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void unregister(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized void broadcastStatus(String status, String ip, String country, int attempt, int total, String configName, String message) {
        mainHandler.post(() -> {
            List<Listener> targets;
            synchronized (this) {
                targets = new ArrayList<>(listeners);
            }
            for (Listener l : targets) {
                l.onVpnStatusChanged(status, ip, country, attempt, total, configName, message);
            }
        });
    }

    // Convenience methods
    public void broadcastStatus(String status) {
        broadcastStatus(status, null, null, 0, 0, null, null);
    }

    public void broadcastConnected(String ip, String country) {
        broadcastStatus(MainActivity.STATUS_CONNECTED, ip, country, 0, 0, null, null);
    }

    public void broadcastError(String message) {
        broadcastStatus(MainActivity.STATUS_ERROR, null, null, 0, 0, null, message);
    }

    public void broadcastTryingNext(int attempt, int total, String configName) {
        broadcastStatus(MainActivity.STATUS_TRYING_NEXT, null, null, attempt, total, configName, null);
    }
}
