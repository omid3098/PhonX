package ir.phonx;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class ConfigEntryTest {

    private static final String VLESS_URI =
            "vless://test-uuid@example.com:443?security=tls&type=ws#MyServer";

    // vmess base64 of {"id":"test-uuid","add":"vmess.example.com","port":8443,"net":"tcp","tls":"tls","ps":"VmessNode"}
    private static final String VMESS_URI;
    static {
        String json = "{\"id\":\"test-uuid\",\"add\":\"vmess.example.com\",\"port\":8443,"
                + "\"net\":\"tcp\",\"tls\":\"tls\",\"ps\":\"VmessNode\"}";
        VMESS_URI = "vmess://" + android.util.Base64.encodeToString(
                json.getBytes(), android.util.Base64.NO_WRAP);
    }

    @Test
    public void fromUri_vless_extractsAllFields() throws Exception {
        ConfigEntry entry = ConfigEntry.fromUri(VLESS_URI);
        assertNotNull(entry.id);
        assertFalse(entry.id.isEmpty());
        assertEquals(VLESS_URI, entry.rawUri);
        assertEquals("MyServer", entry.displayName);
        assertEquals("vless", entry.protocol);
        assertEquals("example.com", entry.host);
        assertEquals(443, entry.port);
    }

    @Test
    public void fromUri_vmess_extractsAllFields() throws Exception {
        ConfigEntry entry = ConfigEntry.fromUri(VMESS_URI);
        assertNotNull(entry.id);
        assertEquals(VMESS_URI, entry.rawUri);
        assertEquals("VmessNode", entry.displayName);
        assertEquals("vmess", entry.protocol);
        assertEquals("vmess.example.com", entry.host);
        assertEquals(8443, entry.port);
    }

    @Test
    public void fromUri_generatesUniqueIds() throws Exception {
        ConfigEntry a = ConfigEntry.fromUri(VLESS_URI);
        ConfigEntry b = ConfigEntry.fromUri(VLESS_URI);
        assertNotEquals(a.id, b.id);
    }

    @Test(expected = Exception.class)
    public void fromUri_invalidUri_throws() throws Exception {
        ConfigEntry.fromUri("http://not-a-proxy.com");
    }

    @Test
    public void toJson_fromJson_roundtrip() throws Exception {
        ConfigEntry original = ConfigEntry.fromUri(VLESS_URI);
        JSONObject json = original.toJson();
        ConfigEntry restored = ConfigEntry.fromJson(json);

        assertEquals(original.id, restored.id);
        assertEquals(original.rawUri, restored.rawUri);
        assertEquals(original.displayName, restored.displayName);
        assertEquals(original.protocol, restored.protocol);
        assertEquals(original.host, restored.host);
        assertEquals(original.port, restored.port);
    }

    @Test(expected = JSONException.class)
    public void fromJson_missingFields_throws() throws Exception {
        JSONObject incomplete = new JSONObject();
        incomplete.put("id", "some-id");
        // missing all other fields
        ConfigEntry.fromJson(incomplete);
    }

    @Test
    public void displayName_fallsBackToHostPort() throws Exception {
        // vless URI with no fragment (no #name)
        String uri = "vless://test-uuid@fallback.com:8080?security=none&type=tcp";
        ConfigEntry entry = ConfigEntry.fromUri(uri);
        assertEquals("vless://fallback.com:8080", entry.displayName);
    }
}
