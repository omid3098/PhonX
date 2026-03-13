package ir.phonx;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class ConfigStorageTest {

    private ConfigStorage storage;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        // Clear prefs directly to avoid migration side-effects in setUp
        ctx.getSharedPreferences("phonx_prefs", Context.MODE_PRIVATE).edit().clear().commit();
        storage = new ConfigStorage(ctx);
    }

    // ── Legacy single-config tests (backward compat) ──────────────────────────

    @Test
    public void loadUri_beforeSave_returnsNull() {
        assertNull(storage.loadUri());
    }

    @Test
    public void saveAndLoad_roundtrip() {
        storage.saveUri("vless://uuid@host:443?security=none&type=tcp");
        assertNotNull(storage.loadUri());
        assertTrue(storage.loadUri().contains("host:443"));
    }

    @Test
    public void hasConfig_falseBeforeSave() {
        assertFalse(storage.hasConfig());
    }

    @Test
    public void hasConfig_trueAfterSave() {
        storage.saveUri("vless://uuid@host:443?security=none&type=tcp");
        assertTrue(storage.hasConfig());
    }

    @Test
    public void clear_makesLoadUriNull() {
        storage.saveUri("vless://uuid@host:443?security=none&type=tcp");
        storage.clear();
        assertNull(storage.loadUri());
    }

    @Test
    public void clear_makesHasConfigFalse() {
        storage.saveUri("vless://uuid@host:443?security=none&type=tcp");
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
        Context ctx = ApplicationProvider.getApplicationContext();
        ConfigStorage newStorage = new ConfigStorage(ctx);
        assertFalse(newStorage.isPsiphonEnabled());
    }

    // ── Multi-config: addConfig / loadConfigs ─────────────────────────────────

    @Test
    public void addConfig_singleEntry_makesItActive() throws Exception {
        ConfigEntry entry = ConfigEntry.fromUri("vless://uuid@host1:443?security=none&type=tcp");
        storage.addConfig(entry);
        ConfigEntry active = storage.getActiveConfig();
        assertNotNull(active);
        assertEquals(entry.id, active.id);
    }

    @Test
    public void addConfig_secondEntry_doesNotChangeActive() throws Exception {
        ConfigEntry first = ConfigEntry.fromUri("vless://uuid@host1:443?security=none&type=tcp");
        ConfigEntry second = ConfigEntry.fromUri("vless://uuid@host2:443?security=none&type=tcp");
        storage.addConfig(first);
        storage.addConfig(second);
        assertEquals(first.id, storage.getActiveConfigId());
    }

    @Test
    public void loadConfigs_returnsAllAdded() throws Exception {
        storage.addConfig(ConfigEntry.fromUri("vless://a@h1:443?security=none&type=tcp"));
        storage.addConfig(ConfigEntry.fromUri("vless://b@h2:443?security=none&type=tcp"));
        storage.addConfig(ConfigEntry.fromUri("vless://c@h3:443?security=none&type=tcp"));
        assertEquals(3, storage.loadConfigs().size());
    }

    // ── Multi-config: removeConfig ────────────────────────────────────────────

    @Test
    public void removeConfig_byId_removesCorrectOne() throws Exception {
        ConfigEntry first = ConfigEntry.fromUri("vless://a@h1:443?security=none&type=tcp");
        ConfigEntry second = ConfigEntry.fromUri("vless://b@h2:443?security=none&type=tcp");
        storage.addConfig(first);
        storage.addConfig(second);

        storage.removeConfig(first.id);

        List<ConfigEntry> remaining = storage.loadConfigs();
        assertEquals(1, remaining.size());
        assertEquals(second.id, remaining.get(0).id);
    }

    @Test
    public void removeConfig_activeConfig_autoSelectsFirst() throws Exception {
        ConfigEntry first = ConfigEntry.fromUri("vless://a@h1:443?security=none&type=tcp");
        ConfigEntry second = ConfigEntry.fromUri("vless://b@h2:443?security=none&type=tcp");
        storage.addConfig(first);
        storage.addConfig(second);
        // first is active; remove it
        storage.removeConfig(first.id);
        assertEquals(second.id, storage.getActiveConfigId());
    }

    @Test
    public void removeConfig_lastConfig_emptyList() throws Exception {
        ConfigEntry entry = ConfigEntry.fromUri("vless://a@h1:443?security=none&type=tcp");
        storage.addConfig(entry);
        storage.removeConfig(entry.id);
        assertFalse(storage.hasConfigs());
        assertNull(storage.getActiveConfig());
    }

    // ── Multi-config: setActiveConfigId / getActiveConfig ─────────────────────

    @Test
    public void setActiveConfigId_changesActive() throws Exception {
        ConfigEntry first = ConfigEntry.fromUri("vless://a@h1:443?security=none&type=tcp");
        ConfigEntry second = ConfigEntry.fromUri("vless://b@h2:443?security=none&type=tcp");
        storage.addConfig(first);
        storage.addConfig(second);

        storage.setActiveConfigId(second.id);
        assertEquals(second.id, storage.getActiveConfig().id);
    }

    // ── Multi-config: getOrderedConfigs ───────────────────────────────────────

    @Test
    public void getOrderedConfigs_activeFirst() throws Exception {
        ConfigEntry a = ConfigEntry.fromUri("vless://a@h1:443?security=none&type=tcp");
        ConfigEntry b = ConfigEntry.fromUri("vless://b@h2:443?security=none&type=tcp");
        ConfigEntry c = ConfigEntry.fromUri("vless://c@h3:443?security=none&type=tcp");
        storage.addConfig(a);
        storage.addConfig(b);
        storage.addConfig(c);
        storage.setActiveConfigId(b.id);

        List<ConfigEntry> ordered = storage.getOrderedConfigs();
        assertEquals(3, ordered.size());
        assertEquals(b.id, ordered.get(0).id);
    }

    // ── Multi-config: hasConfigs ──────────────────────────────────────────────

    @Test
    public void hasConfigs_falseWhenEmpty() {
        assertFalse(storage.hasConfigs());
    }

    @Test
    public void hasConfigs_trueAfterAdd() throws Exception {
        storage.addConfig(ConfigEntry.fromUri("vless://a@h1:443?security=none&type=tcp"));
        assertTrue(storage.hasConfigs());
    }

    // ── Try-all toggle ────────────────────────────────────────────────────────

    @Test
    public void isTryAllEnabled_defaultTrue() {
        assertTrue(storage.isTryAllEnabled());
    }

    @Test
    public void setTryAllEnabled_roundtrip() {
        storage.setTryAllEnabled(false);
        assertFalse(storage.isTryAllEnabled());
        storage.setTryAllEnabled(true);
        assertTrue(storage.isTryAllEnabled());
    }

    @Test
    public void setTryAllEnabled_persists() {
        storage.setTryAllEnabled(false);
        Context ctx = ApplicationProvider.getApplicationContext();
        ConfigStorage newStorage = new ConfigStorage(ctx);
        assertFalse(newStorage.isTryAllEnabled());
    }

    // ── Migration ─────────────────────────────────────────────────────────────

    @Test
    public void migrateFromSingleConfig_convertsLegacyUri() {
        Context ctx = ApplicationProvider.getApplicationContext();
        // Write legacy key directly
        SharedPreferences prefs = ctx.getSharedPreferences("phonx_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("server_uri", "vless://legacy@old.host:443?security=none&type=tcp")
                .remove("config_list")
                .commit();

        // Create new storage — triggers migration in constructor
        ConfigStorage migrated = new ConfigStorage(ctx);
        List<ConfigEntry> configs = migrated.loadConfigs();
        assertEquals(1, configs.size());
        assertTrue(configs.get(0).rawUri.contains("old.host"));
        assertNotNull(migrated.getActiveConfig());
    }

    @Test
    public void migrateFromSingleConfig_noLegacy_noOp() {
        // No server_uri key, migration should be a no-op
        Context ctx = ApplicationProvider.getApplicationContext();
        ConfigStorage fresh = new ConfigStorage(ctx);
        assertTrue(fresh.loadConfigs().isEmpty());
    }

    @Test
    public void migrateFromSingleConfig_alreadyMigrated_noOp() throws Exception {
        // Add a config via the new API
        ConfigEntry entry = ConfigEntry.fromUri("vless://new@new.host:443?security=none&type=tcp");
        storage.addConfig(entry);

        // Write a legacy key too (shouldn't be migrated since config_list exists)
        Context ctx = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences("phonx_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("server_uri", "vless://legacy@old.host:443?security=none&type=tcp").commit();

        ConfigStorage reloaded = new ConfigStorage(ctx);
        // Should still only have 1 config (the new one)
        assertEquals(1, reloaded.loadConfigs().size());
        assertTrue(reloaded.loadConfigs().get(0).rawUri.contains("new.host"));
    }

    // ── Backward-compat delegation ────────────────────────────────────────────

    @Test
    public void saveUri_delegatesToAddConfig() {
        storage.saveUri("vless://uuid@host:443?security=none&type=tcp");
        List<ConfigEntry> configs = storage.loadConfigs();
        assertEquals(1, configs.size());
        assertTrue(configs.get(0).rawUri.contains("host:443"));
    }

    @Test
    public void loadUri_delegatesToActiveConfig() throws Exception {
        ConfigEntry entry = ConfigEntry.fromUri("vless://uuid@host:443?security=none&type=tcp");
        storage.addConfig(entry);
        assertEquals(entry.rawUri, storage.loadUri());
    }

    @Test
    public void clear_removesAllConfigs() throws Exception {
        storage.addConfig(ConfigEntry.fromUri("vless://a@h1:443?security=none&type=tcp"));
        storage.addConfig(ConfigEntry.fromUri("vless://b@h2:443?security=none&type=tcp"));
        storage.clear();
        assertFalse(storage.hasConfigs());
    }
}
