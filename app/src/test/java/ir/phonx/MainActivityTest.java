package ir.phonx;

import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ActivityScenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
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

    // ── Broadcast → state transitions ─────────────────────────────────────────

    private void sendVpnStatus(MainActivity activity, String status) {
        Intent i = new Intent(MainActivity.ACTION_VPN_STATUS);
        i.putExtra(MainActivity.EXTRA_STATUS, status);
        LocalBroadcastManager.getInstance(activity).sendBroadcast(i);
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void broadcast_connected_buttonShowsDisconnect() {
        scenario.onActivity(activity -> {
            sendVpnStatus(activity, MainActivity.STATUS_CONNECTED);
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.disconnect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_connecting_buttonShowsDisconnect() {
        scenario.onActivity(activity -> {
            sendVpnStatus(activity, MainActivity.STATUS_CONNECTING);
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.disconnect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_disconnected_buttonShowsConnect() {
        scenario.onActivity(activity -> {
            // First go to connected
            sendVpnStatus(activity, MainActivity.STATUS_CONNECTED);
            // Then disconnect
            sendVpnStatus(activity, MainActivity.STATUS_DISCONNECTED);
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.connect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_error_setsDisconnectedState() {
        scenario.onActivity(activity -> {
            sendVpnStatus(activity, MainActivity.STATUS_CONNECTED);
            // Error should revert to DISCONNECTED state
            Intent i = new Intent(MainActivity.ACTION_VPN_STATUS);
            i.putExtra(MainActivity.EXTRA_STATUS, MainActivity.STATUS_ERROR);
            i.putExtra("message", "Connection failed");
            LocalBroadcastManager.getInstance(activity).sendBroadcast(i);
            ShadowLooper.idleMainLooper();
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.connect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_noStatus_noStateChange() {
        scenario.onActivity(activity -> {
            // Intent with no EXTRA_STATUS — should be a no-op
            Intent i = new Intent(MainActivity.ACTION_VPN_STATUS);
            LocalBroadcastManager.getInstance(activity).sendBroadcast(i);
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
            sendVpnStatus(activity, MainActivity.STATUS_CONNECTED);
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
            sendVpnStatus(activity, MainActivity.STATUS_CONNECTING);
            Button btn = activity.findViewById(R.id.btnConnect);
            btn.performClick();
            Intent started = Shadows.shadowOf(activity.getApplication()).getNextStartedService();
            assertNotNull(started);
            assertEquals(PhonXVpnService.ACTION_STOP, started.getAction());
        });
    }

    @Test
    public void click_whenDisconnected_noConfig_opensSettingsActivity() {
        scenario.onActivity(activity -> {
            // No config saved (setUp cleared it)
            new ConfigStorage(activity).clear();
            Button btn = activity.findViewById(R.id.btnConnect);
            btn.performClick();
            // Should start SettingsActivity (not a service)
            Intent nextActivity = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextActivity);
            assertEquals(SettingsActivity.class.getName(),
                    nextActivity.getComponent().getClassName());
        });
    }

    @Test
    public void click_whenDisconnected_withConfig_becomesConnecting() {
        scenario.onActivity(activity -> {
            new ConfigStorage(activity).saveUri("vless://uuid@host.com:443");
            Button btn = activity.findViewById(R.id.btnConnect);
            btn.performClick();
            // VpnService.prepare() returns null in Robolectric → startVpnService() is called
            // State was set to CONNECTING before launching VPN
            TextView tvStatus = activity.findViewById(R.id.tvStatus);
            // Either CONNECTING status or the service was started
            String text = tvStatus.getText().toString();
            boolean isConnecting = activity.getString(R.string.status_connecting).equals(text);
            Intent svcIntent = Shadows.shadowOf(activity.getApplication()).peekNextStartedService();
            boolean serviceStarted = svcIntent != null &&
                PhonXVpnService.ACTION_START.equals(svcIntent.getAction());
            assertTrue("Should be connecting or have started service", isConnecting || serviceStarted);
        });
    }

    @Test
    public void settingsButton_opensSettingsActivity() {
        scenario.onActivity(activity -> {
            activity.findViewById(R.id.btnSettings).performClick();
            Intent nextActivity = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextActivity);
            assertEquals(SettingsActivity.class.getName(),
                    nextActivity.getComponent().getClassName());
        });
    }

    // ── STATUS_TRYING_NEXT tests ──────────────────────────────────────────────

    @Test
    public void broadcast_tryingNext_staysInConnectingState() {
        scenario.onActivity(activity -> {
            Intent i = new Intent(MainActivity.ACTION_VPN_STATUS);
            i.putExtra(MainActivity.EXTRA_STATUS, MainActivity.STATUS_TRYING_NEXT);
            i.putExtra("attempt", 2);
            i.putExtra("total", 3);
            LocalBroadcastManager.getInstance(activity).sendBroadcast(i);
            ShadowLooper.idleMainLooper();
            Button btn = activity.findViewById(R.id.btnConnect);
            assertEquals(activity.getString(R.string.disconnect), btn.getText().toString());
        });
    }

    @Test
    public void broadcast_tryingNext_updatesStatusText() {
        scenario.onActivity(activity -> {
            Intent i = new Intent(MainActivity.ACTION_VPN_STATUS);
            i.putExtra(MainActivity.EXTRA_STATUS, MainActivity.STATUS_TRYING_NEXT);
            i.putExtra("attempt", 2);
            i.putExtra("total", 3);
            LocalBroadcastManager.getInstance(activity).sendBroadcast(i);
            ShadowLooper.idleMainLooper();
            TextView tv = activity.findViewById(R.id.tvStatus);
            String expected = activity.getString(R.string.status_trying_config, 2, 3);
            assertEquals(expected, tv.getText().toString());
        });
    }

    @Test
    public void click_whenDisconnected_withConfigs_becomesConnecting() {
        scenario.onActivity(activity -> {
            // Use multi-config API
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
    public void onPause_unregistersReceiver_broadcastAfterPauseNoCrash() {
        scenario.moveToState(Lifecycle.State.STARTED); // triggers onPause()
        scenario.onActivity(activity -> {
            // Should not crash — receiver is unregistered
            Intent i = new Intent(MainActivity.ACTION_VPN_STATUS);
            i.putExtra(MainActivity.EXTRA_STATUS, MainActivity.STATUS_CONNECTED);
            LocalBroadcastManager.getInstance(activity).sendBroadcast(i);
            ShadowLooper.idleMainLooper();
        });
    }
}
