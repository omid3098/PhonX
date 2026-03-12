package ir.phonx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class PhonXVpnService extends VpnService {

    private static final String TAG = "PhonXVpnService";
    private static final String CHANNEL_ID = "phonx_vpn";
    private static final int NOTIF_ID = 1;

    public static final String ACTION_START = "ir.phonx.START_VPN";
    public static final String ACTION_STOP  = "ir.phonx.STOP_VPN";

    private XrayController xrayController;
    private PsiphonController psiphonController;
    private PowerManager.WakeLock wakeLock;

    // Raw TUN fd owned by Xray after detachFd(); kept so we can close on stop
    private int rawTunFd = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        xrayController    = new XrayController(this);
        psiphonController = new PsiphonController(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(intent.getAction())) {
            startVpn();
        }
        return START_STICKY;
    }

    // ── VPN lifecycle ────────────────────────────────────────────────────────

    private void startVpn() {
        startForeground(NOTIF_ID, buildNotification(getString(R.string.status_connecting)));
        broadcastStatus(MainActivity.STATUS_CONNECTING);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhonX:VpnWakeLock");
            wakeLock.acquire(30 * 60 * 1000L);
        }

        new Thread(() -> {
            try {
                ConfigStorage storage = new ConfigStorage(this);
                String uri = storage.loadUri();
                if (uri == null || uri.isEmpty()) throw new Exception("No server config saved");

                ConfigParser.ProxyConfig config = ConfigParser.parse(uri);

                // Build TUN interface — exclude our own app to prevent routing loop
                ParcelFileDescriptor tunPfd = new Builder()
                    .addAddress("10.0.0.2", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("8.8.4.4")
                    .setMtu(1500)
                    .addDisallowedApplication(getPackageName())
                    .setSession("PhonX")
                    .establish();

                if (tunPfd == null) throw new Exception("Failed to create TUN (permission revoked?)");

                // Detach fd — transfer ownership to Xray core
                rawTunFd = tunPfd.detachFd();

                // Start Xray with the real TUN fd
                xrayController.start(config, rawTunFd);

                updateNotification(getString(R.string.status_connected));
                broadcastStatus(MainActivity.STATUS_CONNECTED);
                Log.i(TAG, "VPN started successfully");

            } catch (Exception e) {
                Log.e(TAG, "Failed to start VPN", e);
                broadcastError(e.getMessage());
                stopVpn();
            }
        }, "PhonX-VpnStart").start();
    }

    private void stopVpn() {
        Log.i(TAG, "Stopping VPN");

        psiphonController.stop();
        xrayController.stop();

        // Close the raw TUN fd (Xray may have already closed it — swallow any error)
        if (rawTunFd != -1) {
            try {
                ParcelFileDescriptor.adoptFd(rawTunFd).close();
            } catch (Exception ignored) {}
            rawTunFd = -1;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }

        broadcastStatus(MainActivity.STATUS_DISCONNECTED);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "VPN permission revoked");
        stopVpn();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    // ── Notifications ────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.vpn_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.vpn_channel_desc));
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    // ── Broadcasts ───────────────────────────────────────────────────────────

    private void broadcastStatus(String status) {
        Intent i = new Intent(MainActivity.ACTION_VPN_STATUS);
        i.putExtra(MainActivity.EXTRA_STATUS, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private void broadcastError(String message) {
        Intent i = new Intent(MainActivity.ACTION_VPN_STATUS);
        i.putExtra(MainActivity.EXTRA_STATUS, MainActivity.STATUS_ERROR);
        if (message != null) i.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }
}
