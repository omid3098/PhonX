package ir.phonx;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ir.phonx.shadows.ShadowCoreController;
import ir.phonx.shadows.ShadowLibv2ray;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
@Config(shadows = {ShadowLibv2ray.class, ShadowCoreController.class})
public class PhonXVpnServiceTest {

    private ServiceController<PhonXVpnService> controller;
    private PhonXVpnService service;

    @Before
    public void setUp() {
        ShadowLibv2ray.reset();
        controller = Robolectric.buildService(PhonXVpnService.class);
        service = controller.create().get();
        // Clear any saved config
        new ConfigStorage(service).clear();
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
        // STATUS_CONNECTING is broadcast on the calling thread before the bg thread starts
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue(connectingReceived[0]);

        // Let the background thread finish cleanly
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

        // Wait for background thread to run and post broadcast to main looper
        Thread.sleep(500);
        ShadowLooper.idleMainLooper();

        lbm.unregisterReceiver(receiver);
        assertTrue("Expected STATUS_ERROR broadcast after no-config start", errorReceived[0]);
    }

    @Test
    public void startVpn_withValidConfig_parsesConfigAndStartsThread() throws InterruptedException {
        // Save a valid config; verify the service starts the background thread (reaches
        // STATUS_CONNECTING before the thread) and doesn't crash during setup.
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
        // The service should return START_STICKY and broadcast CONNECTING before the thread runs
        assertEquals(Service.START_STICKY, result);
        assertTrue("Expected STATUS_CONNECTING broadcast", connectingReceived[0]);

        // Let the background thread finish (it will throw a JVM-level Error from VPN builder,
        // which terminates the thread — that's acceptable in the test environment)
        Thread.sleep(300);
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void stopVpn_whenNotStarted_noException() {
        Intent stop = new Intent(PhonXVpnService.ACTION_STOP);
        service.onStartCommand(stop, 0, 1); // rawTunFd = -1, should not throw
    }

    @Test
    public void onRevoke_noException() {
        service.onRevoke(); // should call stopVpn() without crashing
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
}
