package ir.phonx;

import android.content.Context;
import android.content.SharedPreferences;

public class ConfigStorage {

    private static final String PREFS_NAME = "phonx_prefs";
    private static final String KEY_SERVER_URI = "server_uri";
    private static final String KEY_PSIPHON_ENABLED = "psiphon_enabled";

    private final SharedPreferences prefs;

    public ConfigStorage(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveUri(String uri) {
        prefs.edit().putString(KEY_SERVER_URI, uri).apply();
    }

    public String loadUri() {
        return prefs.getString(KEY_SERVER_URI, null);
    }

    public boolean hasConfig() {
        String uri = loadUri();
        return uri != null && !uri.trim().isEmpty();
    }

    public void clear() {
        prefs.edit().remove(KEY_SERVER_URI).apply();
    }

    /** Default: true (Psiphon ON). */
    public boolean isPsiphonEnabled() {
        return prefs.getBoolean(KEY_PSIPHON_ENABLED, true);
    }

    public void setPsiphonEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PSIPHON_ENABLED, enabled).apply();
    }
}
