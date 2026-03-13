package ir.phonx;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class MainActivityTest {

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setUp() {
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void sendVpnStatus(String status) {
        VpnStatusManager.getInstance().broadcastStatus(status);
        ShadowLooper.idleMainLooper();
    }

    private void sendConnected(String ip, String country) {
        VpnStatusManager.getInstance().broadcastConnected(ip, country);
        ShadowLooper.idleMainLooper();
    }

    private void sendError(String message) {
        VpnStatusManager.getInstance().broadcastError(message);
        ShadowLooper.idleMainLooper();
    }

    private void sendTryingNext(int attempt, int total) {
        VpnStatusManager.getInstance().broadcastTryingNext(attempt, total, null);
        ShadowLooper.idleMainLooper();
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    public void initialState_buttonShowsConnect() {
        scenario.onActivity(activity -> {
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.connect), btn.getText().toString());
        });
    }

    @Test
    public void initialState_statusShowsDisconnected() {
        scenario.onActivity(activity -> {
            TextView tv = activity.findViewById(R.id.tvStatus);
            assertEquals(activity.getString(R.string.status_disconnected), tv.getText().toString());
        });
    }

    // ── Status → state transitions ──────────────────────────────────────────────

    @Test
    public void broadcast_connected_buttonShowsDisconnect() {
        scenario.onActivity(activity -> {
            sendVpnStatus(MainActivity.STATUS_CONNECTED);
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.disconnect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_connecting_buttonShowsDisconnect() {
        scenario.onActivity(activity -> {
            sendVpnStatus(MainActivity.STATUS_CONNECTING);
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.disconnect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_disconnected_buttonShowsConnect() {
        scenario.onActivity(activity -> {
            // First go to connected
            sendVpnStatus(MainActivity.STATUS_CONNECTED);
            // Then disconnect
            sendVpnStatus(MainActivity.STATUS_DISCONNECTED);
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.connect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_error_setsDisconnectedState() {
        scenario.onActivity(activity -> {
            sendVpnStatus(MainActivity.STATUS_CONNECTED);
            // Error should revert to DISCONNECTED state
            sendError("Connection failed");
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.connect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_noStatus_noStateChange() {
        scenario.onActivity(activity -> {
            // Null status — should be a no-op
            VpnStatusManager.getInstance().broadcastStatus(null, null, null, 0, 0, null, null);
            ShadowLooper.idleMainLooper();
            // Still in initial DISCONNECTED state
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.connect), btn.getText().toString());
        });
    }

    // ── Button click behavior ─────────────────────────────────────────────────

    @Test
    public void click_whenConnected_sendsStopIntent() {
        scenario.onActivity(activity -> {
            sendVpnStatus(MainActivity.STATUS_CONNECTED);
            Button btn = activity.findViewById(R.id.btnConnect);
            btn.performClick();
            // Should call stopVpnService() → startService(ACTION_STOP intent)
            Intent started = Shadows.shadowOf(activity.getApplication()).getNextStartedService();
            assertNotNull(started);
            assertEquals(PhonXVpnService.ACTION_STOP, started.getAction());
        });
    }

    @Test
    public void click_whenConnecting_sendsStopIntent() {
        scenario.onActivity(activity -> {
            sendVpnStatus(MainActivity.STATUS_CONNECTING);
            Button btn = activity.findViewById(R.id.btnConnect);
            btn.performClick();
            Intent started = Shadows.shadowOf(activity.getApplication()).getNextStartedService();
            assertNotNull(started);
            assertEquals(PhonXVpnService.ACTION_STOP, started.getAction());
        });
    }

    @Test
    public void click_whenDisconnected_noConfig_switchesToSettingsTab() {
        scenario.onActivity(activity -> {
            new ConfigStorage(activity).clear();
            Button btn = activity.findViewById(R.id.btnConnect);
            btn.performClick();
            ShadowLooper.idleMainLooper();
            BottomNavigationView nav = activity.getBottomNav();
            assertEquals(R.id.nav_settings, nav.getSelectedItemId());
        });
    }

    @Test
    public void click_whenDisconnected_withConfig_becomesConnecting() {
        scenario.onActivity(activity -> {
            new ConfigStorage(activity).saveUri("vless://uuid@host.com:443");
            Button btn = activity.findViewById(R.id.btnConnect);
            btn.performClick();
            TextView tvStatus = activity.findViewById(R.id.tvStatus);
            String text = tvStatus.getText().toString();
            boolean isConnecting = activity.getString(R.string.status_connecting).equals(text);
            Intent svcIntent = Shadows.shadowOf(activity.getApplication()).peekNextStartedService();
            boolean serviceStarted = svcIntent != null &&
                PhonXVpnService.ACTION_START.equals(svcIntent.getAction());
            assertTrue("Should be connecting or have started service", isConnecting || serviceStarted);
        });
    }

    @Test
    public void bottomNav_defaultsToHomeTab() {
        scenario.onActivity(activity -> {
            BottomNavigationView nav = activity.getBottomNav();
            assertEquals(R.id.nav_home, nav.getSelectedItemId());
        });
    }

    // ── STATUS_TRYING_NEXT tests ──────────────────────────────────────────────

    @Test
    public void broadcast_tryingNext_staysInConnectingState() {
        scenario.onActivity(activity -> {
            sendTryingNext(2, 3);
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.disconnect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_tryingNext_updatesStatusText() {
        scenario.onActivity(activity -> {
            sendTryingNext(2, 3);
            TextView tv = activity.findViewById(R.id.tvStatus);
            String expected = activity.getString(R.string.status_trying_config, 2, 3);
            assertEquals(expected, tv.getText().toString());
        });
    }

    @Test
    public void click_whenDisconnected_withConfigs_becomesConnecting() {
        scenario.onActivity(activity -> {
            try {
                ConfigStorage storage = new ConfigStorage(activity);
                storage.addConfig(ConfigEntry.fromUri("vless://uuid@host.com:443?security=none&type=tcp"));
            } catch (Exception e) { fail("Should not throw"); }
            Button btn = activity.findViewById(R.id.btnConnect);
            btn.performClick();
            TextView tvStatus = activity.findViewById(R.id.tvStatus);
            String text = tvStatus.getText().toString();
            boolean isConnecting = activity.getString(R.string.status_connecting).equals(text);
            Intent svcIntent = Shadows.shadowOf(activity.getApplication()).peekNextStartedService();
            boolean serviceStarted = svcIntent != null &&
                PhonXVpnService.ACTION_START.equals(svcIntent.getAction());
            assertTrue("Should be connecting or have started service", isConnecting || serviceStarted);
        });
    }

    @Test
    public void onStop_unregisters_broadcastAfterStopNoCrash() {
        scenario.moveToState(Lifecycle.State.CREATED);
        // Activity is now stopped — unregistered from VpnStatusManager
        // Sending a status should not crash
        VpnStatusManager.getInstance().broadcastStatus(MainActivity.STATUS_CONNECTED);
        ShadowLooper.idleMainLooper();
    }

    // ── IP verification tests ─────────────────────────────────────────────────

    @Test
    public void initialState_ipAddressHidden() {
        scenario.onActivity(activity -> {
            TextView tvIp = activity.findViewById(R.id.tvIpAddress);
            assertEquals(View.GONE, tvIp.getVisibility());
        });
    }

    @Test
    public void broadcast_connectedWithIp_displaysIpAddress() {
        scenario.onActivity(activity -> {
            sendConnected("1.2.3.4", null);

            TextView tvIp = activity.findViewById(R.id.tvIpAddress);
            assertEquals(View.VISIBLE, tvIp.getVisibility());
            assertEquals(activity.getString(R.string.ip_label, "1.2.3.4"),
                    tvIp.getText().toString());
        });
    }

    @Test
    public void broadcast_disconnected_clearsIpAddress() {
        scenario.onActivity(activity -> {
            // First connect with IP
            sendConnected("1.2.3.4", null);

            // Then disconnect
            sendVpnStatus(MainActivity.STATUS_DISCONNECTED);
            TextView tvIp = activity.findViewById(R.id.tvIpAddress);
            assertEquals(View.GONE, tvIp.getVisibility());
        });
    }

    @Test
    public void broadcast_verifying_showsVerifyingText() {
        scenario.onActivity(activity -> {
            sendVpnStatus(MainActivity.STATUS_VERIFYING);
            TextView tvStatus = activity.findViewById(R.id.tvStatus);
            assertEquals(activity.getString(R.string.status_verifying),
                    tvStatus.getText().toString());
            // Button should show Disconnect (CONNECTING state)
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.disconnect), btn.getText().toString());
            // IP should be hidden
            TextView tvIp = activity.findViewById(R.id.tvIpAddress);
            assertEquals(View.GONE, tvIp.getVisibility());
        });
    }

    @Test
    public void broadcast_connecting_hidesIpAddress() {
        scenario.onActivity(activity -> {
            // First connect with IP showing
            sendConnected("1.2.3.4", null);

            // Now go to connecting
            sendVpnStatus(MainActivity.STATUS_CONNECTING);
            TextView tvIp = activity.findViewById(R.id.tvIpAddress);
            assertEquals(View.GONE, tvIp.getVisibility());
        });
    }

    @Test
    public void broadcast_connectedWithIpAndCountry_displaysCountry() {
        scenario.onActivity(activity -> {
            sendConnected("5.6.7.8", "Germany");

            TextView tvIp = activity.findViewById(R.id.tvIpAddress);
            assertEquals(View.VISIBLE, tvIp.getVisibility());
            assertEquals(activity.getString(R.string.ip_label_with_country, "5.6.7.8", "Germany"),
                    tvIp.getText().toString());
        });
    }

    @Test
    public void broadcast_connectedWithIpNoCountry_displaysIpOnly() {
        scenario.onActivity(activity -> {
            sendConnected("1.2.3.4", null);

            TextView tvIp = activity.findViewById(R.id.tvIpAddress);
            assertEquals(View.VISIBLE, tvIp.getVisibility());
            assertEquals(activity.getString(R.string.ip_label, "1.2.3.4"),
                    tvIp.getText().toString());
        });
    }
}
