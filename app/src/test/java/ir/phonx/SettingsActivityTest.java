package ir.phonx;

import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowToast;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class SettingsActivityTest {

    private ActivityScenario<SettingsActivity> scenario;

    @Before
    public void setUp() {
        // Clear prefs directly
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences("phonx_prefs", Context.MODE_PRIVATE).edit().clear().commit();
        scenario = ActivityScenario.launch(SettingsActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    public void noConfigs_showsEmptyState() {
        scenario.onActivity(activity -> {
            RecyclerView rv = activity.findViewById(R.id.rvConfigs);
            TextView empty = activity.findViewById(R.id.tvEmptyConfigs);
            assertEquals(android.view.View.GONE, rv.getVisibility());
            assertEquals(android.view.View.VISIBLE, empty.getVisibility());
        });
    }

    // ── Add config ────────────────────────────────────────────────────────────

    @Test
    public void addValidConfig_appearsInList() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("vless://test-uuid@example.com:443?security=tls&type=ws");
            activity.findViewById(R.id.btnSave).performClick();

            RecyclerView rv = activity.findViewById(R.id.rvConfigs);
            assertEquals(1, rv.getAdapter().getItemCount());
            assertEquals(android.view.View.VISIBLE, rv.getVisibility());
        });
    }

    @Test
    public void addMultipleConfigs_allAppearInList() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            String[] uris = {
                "vless://a@h1:443?security=none&type=tcp",
                "vless://b@h2:443?security=none&type=tcp",
                "vless://c@h3:443?security=none&type=tcp"
            };
            for (String uri : uris) {
                et.setText(uri);
                activity.findViewById(R.id.btnSave).performClick();
            }

            RecyclerView rv = activity.findViewById(R.id.rvConfigs);
            assertEquals(3, rv.getAdapter().getItemCount());
        });
    }

    @Test
    public void addConfig_invalidUri_showsErrorToast() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("http://bad-protocol.com");
            activity.findViewById(R.id.btnSave).performClick();
            String toast = ShadowToast.getTextOfLatestToast();
            assertNotNull(toast);
            assertTrue("Toast should start with 'Invalid address:'",
                    toast.startsWith("Invalid address:"));
            // Nothing added
            RecyclerView rv = activity.findViewById(R.id.rvConfigs);
            assertEquals(0, rv.getAdapter().getItemCount());
        });
    }

    @Test
    public void addConfig_emptyInput_showsToast() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("");
            activity.findViewById(R.id.btnSave).performClick();
            assertEquals("Please enter a server address",
                    ShadowToast.getTextOfLatestToast());
        });
    }

    @Test
    public void addConfig_clearsInputAfterSuccess() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("vless://test-uuid@example.com:443?security=tls&type=ws");
            activity.findViewById(R.id.btnSave).performClick();
            assertEquals("", et.getText().toString());
        });
    }

    @Test
    public void addConfig_doesNotFinishActivity() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("vless://test-uuid@example.com:443?security=tls&type=ws");
            activity.findViewById(R.id.btnSave).performClick();
            assertFalse(activity.isFinishing());
        });
    }

    @Test
    public void firstConfigAdded_autoSelectedAsActive() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("vless://test-uuid@example.com:443?security=tls&type=ws");
            activity.findViewById(R.id.btnSave).performClick();
            // Active config should be set
            ConfigStorage storage = new ConfigStorage(activity);
            assertNotNull(storage.getActiveConfig());
        });
    }

    // ── Remove config ─────────────────────────────────────────────────────────

    @Test
    public void removeConfig_removesFromList() {
        scenario.onActivity(activity -> {
            // Add 2 configs
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("vless://a@h1:443?security=none&type=tcp");
            activity.findViewById(R.id.btnSave).performClick();
            et.setText("vless://b@h2:443?security=none&type=tcp");
            activity.findViewById(R.id.btnSave).performClick();

            // Remove via storage (simulating adapter callback)
            ConfigStorage storage = new ConfigStorage(activity);
            java.util.List<ConfigEntry> configs = storage.loadConfigs();
            assertEquals(2, configs.size());
            storage.removeConfig(configs.get(0).id);

            assertEquals(1, storage.loadConfigs().size());
        });
    }

    // ── Select config ─────────────────────────────────────────────────────────

    @Test
    public void selectConfig_changesActiveInStorage() {
        scenario.onActivity(activity -> {
            // Add 2 configs
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("vless://a@h1:443?security=none&type=tcp");
            activity.findViewById(R.id.btnSave).performClick();
            et.setText("vless://b@h2:443?security=none&type=tcp");
            activity.findViewById(R.id.btnSave).performClick();

            ConfigStorage storage = new ConfigStorage(activity);
            java.util.List<ConfigEntry> configs = storage.loadConfigs();
            // First is active by default; set second
            storage.setActiveConfigId(configs.get(1).id);
            assertEquals(configs.get(1).id, storage.getActiveConfigId());
        });
    }

    // ── Try-all toggle ────────────────────────────────────────────────────────

    @Test
    public void tryAllSwitch_defaultChecked() {
        scenario.onActivity(activity -> {
            MaterialSwitch sw = activity.findViewById(R.id.switchTryAll);
            assertTrue("Try-all switch should default to checked", sw.isChecked());
        });
    }

    @Test
    public void tryAllSwitch_toggle_persistsToStorage() {
        scenario.onActivity(activity -> {
            MaterialSwitch sw = activity.findViewById(R.id.switchTryAll);
            sw.setChecked(false);
            assertFalse(new ConfigStorage(activity).isTryAllEnabled());
            sw.setChecked(true);
            assertTrue(new ConfigStorage(activity).isTryAllEnabled());
        });
    }

    // ── Psiphon toggle (unchanged) ────────────────────────────────────────────

    @Test
    public void psiphonSwitch_defaultChecked() {
        scenario.onActivity(activity -> {
            MaterialSwitch sw = activity.findViewById(R.id.switchPsiphon);
            assertTrue("Psiphon switch should default to checked", sw.isChecked());
        });
    }

    @Test
    public void psiphonSwitch_toggle_persistsToStorage() {
        scenario.onActivity(activity -> {
            MaterialSwitch sw = activity.findViewById(R.id.switchPsiphon);
            sw.setChecked(false);
            assertFalse(new ConfigStorage(activity).isPsiphonEnabled());
            sw.setChecked(true);
            assertTrue(new ConfigStorage(activity).isPsiphonEnabled());
        });
    }

    // ── Back button ───────────────────────────────────────────────────────────

    @Test
    public void backButton_finishesActivity() {
        scenario.onActivity(activity -> {
            activity.findViewById(R.id.btnBack).performClick();
            assertTrue(activity.isFinishing());
        });
    }
}
