package ir.phonx;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class ConfigStorageTest {

    private ConfigStorage storage;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        storage = new ConfigStorage(ctx);
        storage.clear(); // Start clean
    }

    @Test
    public void loadUri_beforeSave_returnsNull() {
        assertNull(storage.loadUri());
    }

    @Test
    public void saveAndLoad_roundtrip() {
        storage.saveUri("vless://uuid@host:443");
        assertEquals("vless://uuid@host:443", storage.loadUri());
    }

    @Test
    public void saveOverwrite_returnsLatest() {
        storage.saveUri("vless://first@host:443");
        storage.saveUri("vless://second@host:443");
        assertEquals("vless://second@host:443", storage.loadUri());
    }

    @Test
    public void hasConfig_falseBeforeSave() {
        assertFalse(storage.hasConfig());
    }

    @Test
    public void hasConfig_trueAfterSave() {
        storage.saveUri("vless://uuid@host:443");
        assertTrue(storage.hasConfig());
    }

    @Test
    public void hasConfig_falseAfterWhitespaceOnly() {
        storage.saveUri("   ");
        assertFalse(storage.hasConfig());
    }

    @Test
    public void clear_makesLoadUriNull() {
        storage.saveUri("vless://uuid@host:443");
        storage.clear();
        assertNull(storage.loadUri());
    }

    @Test
    public void clear_makesHasConfigFalse() {
        storage.saveUri("vless://uuid@host:443");
        storage.clear();
        assertFalse(storage.hasConfig());
    }

    // ── Psiphon toggle tests ─────────────────────────────────────────────────

    @Test
    public void isPsiphonEnabled_defaultTrue() {
        assertTrue(storage.isPsiphonEnabled());
    }

    @Test
    public void setPsiphonEnabled_roundtrip() {
        storage.setPsiphonEnabled(false);
        assertFalse(storage.isPsiphonEnabled());
        storage.setPsiphonEnabled(true);
        assertTrue(storage.isPsiphonEnabled());
    }

    @Test
    public void setPsiphonEnabled_false_persistsCorrectly() {
        storage.setPsiphonEnabled(false);
        // Re-create storage to verify persistence
        Context ctx = ApplicationProvider.getApplicationContext();
        ConfigStorage newStorage = new ConfigStorage(ctx);
        assertFalse(newStorage.isPsiphonEnabled());
    }
}
