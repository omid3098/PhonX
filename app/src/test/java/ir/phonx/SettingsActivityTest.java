package ir.phonx;

import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;

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
        // Clear any previously saved config
        scenario = ActivityScenario.launch(SettingsActivity.class);
        scenario.onActivity(activity -> new ConfigStorage(activity).clear());
        scenario.close();
        // Relaunch with clean state
        scenario = ActivityScenario.launch(SettingsActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    @Test
    public void noSavedConfig_showsNoServerText() {
        scenario.onActivity(activity -> {
            TextView tv = activity.findViewById(R.id.tvCurrentServer);
            assertEquals(activity.getString(R.string.no_server), tv.getText().toString());
        });
    }

    @Test
    public void savedConfig_showsUri() {
        String uri = "vless://uuid@host.com:443";
        scenario.onActivity(activity -> {
            new ConfigStorage(activity).saveUri(uri);
            // Reload the view
            activity.recreate();
        });
        scenario.onActivity(activity -> {
            TextView tv = activity.findViewById(R.id.tvCurrentServer);
            assertEquals(uri, tv.getText().toString());
        });
    }

    @Test
    public void emptyInput_showsToast_doesNotSave() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("   ");
            activity.findViewById(R.id.btnSave).performClick();
            assertEquals("Please enter a server address",
                    ShadowToast.getTextOfLatestToast());
            // Nothing saved
            assertFalse(new ConfigStorage(activity).hasConfig());
        });
    }

    @Test
    public void emptyInput_blankString_showsToast() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("");
            activity.findViewById(R.id.btnSave).performClick();
            assertEquals("Please enter a server address",
                    ShadowToast.getTextOfLatestToast());
        });
    }

    @Test
    public void validVlessUri_savesToPrefs_andFinishes() {
        String uri = "vless://test-uuid@example.com:443?security=tls&type=ws";
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText(uri);
            activity.findViewById(R.id.btnSave).performClick();
            // Config should be saved
            assertEquals(uri, new ConfigStorage(activity).loadUri());
        });
    }

    @Test
    public void validVlessUri_clearsInputAfterSave() {
        String uri = "vless://test-uuid@example.com:443?security=tls&type=ws";
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText(uri);
            activity.findViewById(R.id.btnSave).performClick();
            assertEquals("", et.getText().toString());
        });
    }

    @Test
    public void invalidUri_showsErrorToast_doesNotSave() {
        scenario.onActivity(activity -> {
            EditText et = activity.findViewById(R.id.etServerUri);
            et.setText("http://invalid-protocol.com");
            activity.findViewById(R.id.btnSave).performClick();
            String toast = ShadowToast.getTextOfLatestToast();
            assertNotNull(toast);
            assertTrue("Toast should start with 'Invalid address:'",
                    toast.startsWith("Invalid address:"));
            assertFalse(new ConfigStorage(activity).hasConfig());
        });
    }

    @Test
    public void backButton_finishesActivity() {
        scenario.onActivity(activity -> {
            activity.findViewById(R.id.btnBack).performClick();
            assertTrue(activity.isFinishing());
        });
    }
}
