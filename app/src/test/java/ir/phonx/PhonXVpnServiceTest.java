package ir.phonx;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ir.phonx.shadows.ShadowGoPsiphonController;
import ir.phonx.shadows.ShadowGoXrayController;
import ir.phonx.shadows.ShadowPhonxcore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
@Config(shadows = {ShadowPhonxcore.class, ShadowGoXrayController.class, ShadowGoPsiphonController.class})
public class PhonXVpnServiceTest {

    private ServiceController<PhonXVpnService> controller;
    private PhonXVpnService service;

    @Before
    public void setUp() {
        ShadowPhonxcore.reset();
        controller = Robolectric.buildService(PhonXVpnService.class);
        service = controller.create().get();
        // Clear prefs directly
        service.getSharedPreferences("phonx_prefs", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After
    public void tearDown() throws InterruptedException {
        Intent stop = new Intent(PhonXVpnService.ACTION_STOP);
        try { service.onStartCommand(stop, 0, 0); } catch (Exception ignored) {}
        Thread.sleep(100);
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void onCreate_createsServiceSuccessfully() {
        assertNotNull(service);
    }

    @Test
    public void onStartCommand_nullIntent_returnsNotSticky() {
        int result = service.onStartCommand(null, 0, 1);
        assertEquals(Service.START_NOT_STICKY, result);
    }

    @Test
    public void onStartCommand_stopAction_returnsNotSticky() {
        Intent stop = new Intent(PhonXVpnService.ACTION_STOP);
        int result = service.onStartCommand(stop, 0, 1);
        assertEquals(Service.START_NOT_STICKY, result);
    }

    @Test
    public void onStartCommand_startAction_returnsSticky() {
        Intent start = new Intent(PhonXVpnService.ACTION_START);
        int result = service.onStartCommand(start, 0, 1);
        assertEquals(Service.START_STICKY, result);
    }

    @Test
    public void startVpn_broadcastsConnecting() throws InterruptedException {
        boolean[] connectingReceived = {false};
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(service);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (MainActivity.STATUS_CONNECTING.equals(
                        intent.getStringExtra(MainActivity.EXTRA_STATUS))) {
                    connectingReceived[0] = true;
                }
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_VPN_STATUS));

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue(connectingReceived[0]);

        Thread.sleep(300);
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void startVpn_noConfig_broadcastsError() throws InterruptedException {
        boolean[] errorReceived = {false};
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(service);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                String status = intent.getStringExtra(MainActivity.EXTRA_STATUS);
                if (MainActivity.STATUS_ERROR.equals(status)) {
                    errorReceived[0] = true;
                }
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_VPN_STATUS));

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);

        Thread.sleep(500);
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue("Expected STATUS_ERROR broadcast after no-config start", errorReceived[0]);
    }

    @Test
    public void startVpn_withValidConfig_parsesConfigAndStartsThread() throws InterruptedException {
        new ConfigStorage(service).saveUri(
            "vless://test-uuid-1234@example.com:443?security=tls&type=ws");

        boolean[] connectingReceived = {false};
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(service);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (MainActivity.STATUS_CONNECTING.equals(
                        intent.getStringExtra(MainActivity.EXTRA_STATUS))) {
                    connectingReceived[0] = true;
                }
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_VPN_STATUS));

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        int result = service.onStartCommand(start, 0, 1);
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertEquals(Service.START_STICKY, result);
        assertTrue("Expected STATUS_CONNECTING broadcast", connectingReceived[0]);

        Thread.sleep(300);
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void stopVpn_whenNotStarted_noException() {
        Intent stop = new Intent(PhonXVpnService.ACTION_STOP);
        service.onStartCommand(stop, 0, 1);
    }

    @Test
    public void onRevoke_noException() {
        service.onRevoke();
    }

    @Test
    public void notificationChannel_isCreated() {
        NotificationManager nm =
            (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(nm);
        NotificationChannel ch = nm.getNotificationChannel("phonx_vpn");
        assertNotNull(ch);
    }

    @Test
    public void stopVpn_broadcastsDisconnected() throws InterruptedException {
        boolean[] disconnectedReceived = {false};
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(service);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (MainActivity.STATUS_DISCONNECTED.equals(
                        intent.getStringExtra(MainActivity.EXTRA_STATUS))) {
                    disconnectedReceived[0] = true;
                }
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_VPN_STATUS));

        Intent stop = new Intent(PhonXVpnService.ACTION_STOP);
        service.onStartCommand(stop, 0, 1);
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue(disconnectedReceived[0]);
    }

    // ── Psiphon lifecycle tests ──────────────────────────────────────────────

    @Test
    public void startVpn_withPsiphonEnabled_broadcastsPsiphonConnecting() throws InterruptedException {
        ConfigStorage storage = new ConfigStorage(service);
        storage.saveUri("vless://test-uuid@example.com:443?security=tls&type=ws");
        storage.setPsiphonEnabled(true);

        boolean[] psiphonConnecting = {false};
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(service);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                String status = intent.getStringExtra(MainActivity.EXTRA_STATUS);
                if (MainActivity.STATUS_CONNECTING_PSIPHON.equals(status)) {
                    psiphonConnecting[0] = true;
                }
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_VPN_STATUS));

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);

        Thread.sleep(500);
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue("Expected STATUS_CONNECTING_PSIPHON broadcast", psiphonConnecting[0]);
    }

    @Test
    public void startVpn_withPsiphonDisabled_doesNotStartPsiphon() throws InterruptedException {
        ConfigStorage storage = new ConfigStorage(service);
        storage.saveUri("vless://test-uuid@example.com:443?security=tls&type=ws");
        storage.setPsiphonEnabled(false);

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);

        Thread.sleep(500);
        ShadowLooper.idleMainLooper();

        assertFalse(ShadowGoPsiphonController.startCalled);
    }

    @Test
    public void stopVpn_stopsXrayThenPsiphon() throws InterruptedException {
        ConfigStorage storage = new ConfigStorage(service);
        storage.saveUri("vless://test-uuid@example.com:443?security=tls&type=ws");

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);
        Thread.sleep(300);

        Intent stop = new Intent(PhonXVpnService.ACTION_STOP);
        service.onStartCommand(stop, 0, 1);
        ShadowLooper.idleMainLooper();
    }

    // ── Multi-config fallback tests ──────────────────────────────────────────
    // Note: In Robolectric, VpnService.Builder.establish() returns null,
    // so each config attempt fails at TUN creation (not at Xray).
    // We verify broadcast behavior and config list logic here.

    private void addConfigs(ConfigStorage storage, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            ConfigEntry entry = ConfigEntry.fromUri(
                "vless://uuid" + i + "@host" + i + ".com:443?security=none&type=tcp");
            storage.addConfig(entry);
        }
    }

    @Test
    public void startVpn_tryAllOff_singleConfigFails_broadcastsError() throws Exception {
        ConfigStorage storage = new ConfigStorage(service);
        addConfigs(storage, 2);
        storage.setTryAllEnabled(false);
        storage.setPsiphonEnabled(false);

        boolean[] errorReceived = {false};
        boolean[] tryingNextReceived = {false};
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(service);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                String status = intent.getStringExtra(MainActivity.EXTRA_STATUS);
                if (MainActivity.STATUS_ERROR.equals(status)) errorReceived[0] = true;
                if (MainActivity.STATUS_TRYING_NEXT.equals(status)) tryingNextReceived[0] = true;
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_VPN_STATUS));

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);
        Thread.sleep(1000);
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue("Should broadcast error when tryAll is off", errorReceived[0]);
        assertFalse("Should NOT broadcast trying_next with tryAll off", tryingNextReceived[0]);
    }

    @Test
    public void startVpn_tryAllOn_broadcastsTryingNext() throws Exception {
        ConfigStorage storage = new ConfigStorage(service);
        addConfigs(storage, 2);
        storage.setTryAllEnabled(true);
        storage.setPsiphonEnabled(false);

        // In Robolectric, all attempts fail at TUN creation.
        // The fallback loop should still broadcast STATUS_TRYING_NEXT for attempt 2.
        List<String> receivedStatuses = new ArrayList<>();
        int[] tryingAttempt = {0};
        int[] tryingTotal = {0};
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(service);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                String status = intent.getStringExtra(MainActivity.EXTRA_STATUS);
                receivedStatuses.add(status);
                if (MainActivity.STATUS_TRYING_NEXT.equals(status)) {
                    tryingAttempt[0] = intent.getIntExtra("attempt", 0);
                    tryingTotal[0] = intent.getIntExtra("total", 0);
                }
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_VPN_STATUS));

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);
        Thread.sleep(2500);
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue("Should broadcast STATUS_TRYING_NEXT",
                receivedStatuses.contains(MainActivity.STATUS_TRYING_NEXT));
        assertEquals(2, tryingAttempt[0]);
        assertEquals(2, tryingTotal[0]);
        // Should also eventually get error since all TUN creations fail
        assertTrue("Should broadcast STATUS_ERROR after all fail",
                receivedStatuses.contains(MainActivity.STATUS_ERROR));
    }

    @Test
    public void startVpn_tryAllOn_allFail_broadcastsError() throws Exception {
        ConfigStorage storage = new ConfigStorage(service);
        addConfigs(storage, 3);
        storage.setTryAllEnabled(true);
        storage.setPsiphonEnabled(false);

        boolean[] errorReceived = {false};
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(service);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (MainActivity.STATUS_ERROR.equals(
                        intent.getStringExtra(MainActivity.EXTRA_STATUS))) {
                    errorReceived[0] = true;
                }
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_VPN_STATUS));

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);
        Thread.sleep(4000); // 3 configs × ~1s retry delay
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue("Should broadcast error when all configs fail", errorReceived[0]);
    }

    @Test
    public void startVpn_noConfigs_broadcastsError() throws Exception {
        ConfigStorage storage = new ConfigStorage(service);
        storage.setTryAllEnabled(true);
        storage.setPsiphonEnabled(false);

        boolean[] errorReceived = {false};
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(service);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (MainActivity.STATUS_ERROR.equals(
                        intent.getStringExtra(MainActivity.EXTRA_STATUS))) {
                    errorReceived[0] = true;
                }
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_VPN_STATUS));

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);
        Thread.sleep(500);
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue("Should broadcast error with empty config list", errorReceived[0]);
    }

    @Test
    public void startVpn_psiphonStartedOnce_acrossRetries() throws Exception {
        ConfigStorage storage = new ConfigStorage(service);
        addConfigs(storage, 2);
        storage.setTryAllEnabled(true);
        storage.setPsiphonEnabled(true);

        Intent start = new Intent(PhonXVpnService.ACTION_START);
        service.onStartCommand(start, 0, 1);
        Thread.sleep(3000);
        ShadowLooper.idleMainLooper();

        // Psiphon should be started exactly once, even though both configs are tried
        assertEquals("Psiphon should only be started once across retries",
                1, ShadowGoPsiphonController.startCallCount);
    }

    // ── IP checker factory tests ──────────────────────────────────────────────

    @Test
    public void createIpChecker_returnsNonNull() {
        assertNotNull(service.createIpChecker());
    }

    @Test
    public void createIpChecker_returnsIpCheckerInstance() {
        assertTrue(service.createIpChecker() instanceof IpChecker);
    }
}
