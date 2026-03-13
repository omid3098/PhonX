package ir.phonx;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;

/**
 * Offline country lookup using a bundled DB-IP Lite MMDB database.
 * Loads the database from Android assets on construction; all lookups
 * are performed in-memory with no network calls.
 */
class GeoIpLookup {

    private static final String TAG = "GeoIpLookup";
    private static final String MMDB_ASSET = "dbip-country-lite.mmdb";

    private final MmdbReader reader;

    GeoIpLookup(Context context) {
        MmdbReader r = null;
        try {
            InputStream in = context.getAssets().open(MMDB_ASSET);
            r = new MmdbReader(in);
            in.close();
        } catch (Exception e) {
            Log.w(TAG, "Failed to load GeoIP database: " + e.getMessage());
        }
        reader = r;
    }

    /** Package-private constructor for testing with a pre-built reader. */
    GeoIpLookup(MmdbReader reader) {
        this.reader = reader;
    }

    /** Returns the English country name for the given IP, or "" on any failure. */
    String lookupCountry(String ip) {
        if (reader == null || ip == null || ip.isEmpty()) return "";
        try {
            return reader.lookupCountry(ip);
        } catch (Exception e) {
            Log.w(TAG, "Country lookup failed for " + ip + ": " + e.getMessage());
            return "";
        }
    }
}
