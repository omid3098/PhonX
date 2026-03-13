package ir.phonx;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ConfigStorage {

    private static final String TAG = "ConfigStorage";
    private static final String PREFS_NAME = "phonx_prefs";
    private static final String KEY_SERVER_URI = "server_uri";
    private static final String KEY_PSIPHON_ENABLED = "psiphon_enabled";
    private static final String KEY_CONFIG_LIST = "config_list";
    private static final String KEY_ACTIVE_CONFIG_ID = "active_config_id";
    private static final String KEY_TRY_ALL_ENABLED = "try_all_enabled";

    private final SharedPreferences prefs;

    public ConfigStorage(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        migrateFromSingleConfig();
    }

    // ── Multi-config API ──────────────────────────────────────────────────────

    public List<ConfigEntry> loadConfigs() {
        List<ConfigEntry> result = new ArrayList<>();
        String json = prefs.getString(KEY_CONFIG_LIST, null);
        if (json == null) return result;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(ConfigEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load configs: " + e.getMessage());
        }
        return result;
    }

    public void saveConfigs(List<ConfigEntry> configs) {
        try {
            JSONArray arr = new JSONArray();
            for (ConfigEntry entry : configs) {
                arr.put(entry.toJson());
            }
            prefs.edit().putString(KEY_CONFIG_LIST, arr.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save configs: " + e.getMessage());
        }
    }

    public void addConfig(ConfigEntry entry) {
        List<ConfigEntry> configs = loadConfigs();
        configs.add(entry);
        saveConfigs(configs);
        // Auto-select as active if it's the first config
        if (configs.size() == 1) {
            setActiveConfigId(entry.id);
        }
    }

    public void removeConfig(String configId) {
        List<ConfigEntry> configs = loadConfigs();
        configs.removeIf(c -> c.id.equals(configId));
        saveConfigs(configs);
        // If we removed the active config, auto-select the first remaining
        if (configId.equals(getActiveConfigId())) {
            if (!configs.isEmpty()) {
                setActiveConfigId(configs.get(0).id);
            } else {
                prefs.edit().remove(KEY_ACTIVE_CONFIG_ID).apply();
            }
        }
    }

    public boolean hasConfigs() {
        return !loadConfigs().isEmpty();
    }

    // ── Active config ─────────────────────────────────────────────────────────

    public void setActiveConfigId(String configId) {
        prefs.edit().putString(KEY_ACTIVE_CONFIG_ID, configId).apply();
    }

    public String getActiveConfigId() {
        return prefs.getString(KEY_ACTIVE_CONFIG_ID, null);
    }

    public ConfigEntry getActiveConfig() {
        String activeId = getActiveConfigId();
        if (activeId == null) return null;
        for (ConfigEntry entry : loadConfigs()) {
            if (entry.id.equals(activeId)) return entry;
        }
        return null;
    }

    /** Returns configs with the active one first, then the rest in original order. */
    public List<ConfigEntry> getOrderedConfigs() {
        List<ConfigEntry> all = loadConfigs();
        String activeId = getActiveConfigId();
        if (activeId == null || all.isEmpty()) return all;

        List<ConfigEntry> ordered = new ArrayList<>();
        ConfigEntry active = null;
        for (ConfigEntry entry : all) {
            if (entry.id.equals(activeId)) {
                active = entry;
            } else {
                ordered.add(entry);
            }
        }
        if (active != null) {
            ordered.add(0, active);
        }
        return ordered;
    }

    // ── Try-all toggle ────────────────────────────────────────────────────────

    /** Default: true (try all configs on failure). */
    public boolean isTryAllEnabled() {
        return prefs.getBoolean(KEY_TRY_ALL_ENABLED, true);
    }

    public void setTryAllEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRY_ALL_ENABLED, enabled).apply();
    }

    // ── Backward-compatible API (delegates to multi-config) ───────────────────

    public void saveUri(String uri) {
        try {
            ConfigEntry entry = ConfigEntry.fromUri(uri);
            addConfig(entry);
        } catch (Exception e) {
            Log.w(TAG, "saveUri failed to parse: " + e.getMessage());
            // Fallback: store raw for legacy compat
            prefs.edit().putString(KEY_SERVER_URI, uri).apply();
        }
    }

    public String loadUri() {
        ConfigEntry active = getActiveConfig();
        return active != null ? active.rawUri : null;
    }

    public boolean hasConfig() {
        return hasConfigs();
    }

    public void clear() {
        prefs.edit()
                .remove(KEY_SERVER_URI)
                .remove(KEY_CONFIG_LIST)
                .remove(KEY_ACTIVE_CONFIG_ID)
                .apply();
    }

    // ── Psiphon toggle ────────────────────────────────────────────────────────

    /** Default: true (Psiphon ON). */
    public boolean isPsiphonEnabled() {
        return prefs.getBoolean(KEY_PSIPHON_ENABLED, true);
    }

    public void setPsiphonEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PSIPHON_ENABLED, enabled).apply();
    }

    // ── Migration ─────────────────────────────────────────────────────────────

    void migrateFromSingleConfig() {
        // Only migrate if legacy key exists and new config_list doesn't
        if (!prefs.contains(KEY_SERVER_URI) || prefs.contains(KEY_CONFIG_LIST)) return;

        String legacyUri = prefs.getString(KEY_SERVER_URI, null);
        if (legacyUri == null || legacyUri.trim().isEmpty()) return;

        try {
            ConfigEntry entry = ConfigEntry.fromUri(legacyUri);
            JSONArray arr = new JSONArray();
            arr.put(entry.toJson());
            prefs.edit()
                    .putString(KEY_CONFIG_LIST, arr.toString())
                    .putString(KEY_ACTIVE_CONFIG_ID, entry.id)
                    .remove(KEY_SERVER_URI)
                    .apply();
            Log.i(TAG, "Migrated legacy server_uri to config list");
        } catch (Exception e) {
            Log.w(TAG, "Migration failed, removing legacy key: " + e.getMessage());
            prefs.edit().remove(KEY_SERVER_URI).apply();
        }
    }
}
