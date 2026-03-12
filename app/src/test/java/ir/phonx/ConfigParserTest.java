package ir.phonx;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class ConfigParserTest {

    // ── Vmess URI fixtures (built with standard JDK Base64 — matches Robolectric shadow) ──

    private static String VMESS_STANDARD;
    private static String VMESS_TLS_TRUE;
    private static String VMESS_TLS_1;
    private static String VMESS_NO_TLS;
    private static String VMESS_GRPC;
    private static String VMESS_URL_SAFE;
    private static String VMESS_EMPTY_UUID;
    private static String VMESS_EMPTY_HOST;
    private static String VMESS_PORT_ZERO;

    @BeforeClass
    public static void setUpClass() {
        VMESS_STANDARD = vmessUri(
            "{\"id\":\"test-uuid\",\"add\":\"vpn.example.com\",\"port\":443," +
            "\"net\":\"ws\",\"tls\":\"tls\",\"path\":\"/ws\",\"host\":\"vpn.example.com\",\"ps\":\"Test\"}");
        VMESS_TLS_TRUE = vmessUri(
            "{\"id\":\"uuid2\",\"add\":\"host.com\",\"port\":443," +
            "\"net\":\"tcp\",\"tls\":\"true\",\"path\":\"/\",\"host\":\"host.com\",\"ps\":\"\"}");
        VMESS_TLS_1 = vmessUri(
            "{\"id\":\"uuid3\",\"add\":\"host.com\",\"port\":443," +
            "\"net\":\"tcp\",\"tls\":\"1\",\"path\":\"/\",\"host\":\"host.com\",\"ps\":\"\"}");
        VMESS_NO_TLS = vmessUri(
            "{\"id\":\"uuid4\",\"add\":\"host.com\",\"port\":80," +
            "\"net\":\"tcp\",\"tls\":\"none\",\"path\":\"/\",\"host\":\"host.com\",\"ps\":\"\"}");
        VMESS_GRPC = vmessUri(
            "{\"id\":\"uuid5\",\"add\":\"host.com\",\"port\":443," +
            "\"net\":\"grpc\",\"tls\":\"tls\",\"path\":\"/svc\",\"host\":\"host.com\",\"ps\":\"\"}");
        // URL-safe base64 uses - and _ instead of + and /
        String raw = "{\"id\":\"uuid6\",\"add\":\"h.com\",\"port\":8080," +
            "\"net\":\"tcp\",\"tls\":\"none\",\"path\":\"/\",\"host\":\"h.com\",\"ps\":\"\"}";
        String b64 = java.util.Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8))
                .replace('+', '-').replace('/', '_').replace("=", "");
        VMESS_URL_SAFE = "vmess://" + b64;
        VMESS_EMPTY_UUID = vmessUri(
            "{\"id\":\"\",\"add\":\"host.com\",\"port\":443,\"net\":\"tcp\",\"tls\":\"none\"," +
            "\"path\":\"/\",\"host\":\"host.com\",\"ps\":\"\"}");
        VMESS_EMPTY_HOST = vmessUri(
            "{\"id\":\"uuid\",\"add\":\"\",\"port\":443,\"net\":\"tcp\",\"tls\":\"none\"," +
            "\"path\":\"/\",\"host\":\"\",\"ps\":\"\"}");
        VMESS_PORT_ZERO = vmessUri(
            "{\"id\":\"uuid\",\"add\":\"host.com\",\"port\":0,\"net\":\"tcp\",\"tls\":\"none\"," +
            "\"path\":\"/\",\"host\":\"host.com\",\"ps\":\"\"}");
    }

    private static String vmessUri(String json) {
        return "vmess://" + java.util.Base64.getEncoder()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // ── parse() dispatch ─────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void parse_null_throws() throws Exception {
        ConfigParser.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_empty_throws() throws Exception {
        ConfigParser.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_whitespace_throws() throws Exception {
        ConfigParser.parse("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_unsupportedProtocol_throws() throws Exception {
        ConfigParser.parse("ss://someconfig");
    }

    @Test
    public void parse_vless_returnsVless() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://test-uuid@example.com:443?security=tls&type=ws");
        assertEquals("vless", cfg.protocol);
    }

    @Test
    public void parse_vmess_returnsVmess() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(VMESS_STANDARD);
        assertEquals("vmess", cfg.protocol);
    }

    // ── parseVless() field extraction ─────────────────────────────────────────

    @Test
    public void parseVless_uuid() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://my-uuid-1234@example.com:443?security=tls");
        assertEquals("my-uuid-1234", cfg.uuid);
    }

    @Test
    public void parseVless_host() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@vpn.server.com:8443?security=tls");
        assertEquals("vpn.server.com", cfg.host);
    }

    @Test
    public void parseVless_port() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:8080?security=tls");
        assertEquals(8080, cfg.port);
    }

    @Test
    public void parseVless_security() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443?security=reality&type=tcp");
        assertEquals("reality", cfg.security);
    }

    @Test
    public void parseVless_network() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443?security=tls&type=ws");
        assertEquals("ws", cfg.network);
    }

    @Test
    public void parseVless_path() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443?security=tls&type=ws&path=/mypath");
        assertEquals("/mypath", cfg.path);
    }

    @Test
    public void parseVless_sniFromHostParam() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443?security=tls&type=ws&host=sni.host.com");
        assertEquals("sni.host.com", cfg.sni);
    }

    @Test
    public void parseVless_sniFromSniParam() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443?security=tls&type=ws&sni=custom.sni.com");
        assertEquals("custom.sni.com", cfg.sni);
    }

    @Test
    public void parseVless_nameFromFragment() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443?security=tls#My%20Server");
        assertEquals("My Server", cfg.name);
    }

    // ── parseVless() defaults ────────────────────────────────────────────────

    @Test
    public void parseVless_defaultSecurity_isNone() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443");
        assertEquals("none", cfg.security);
    }

    @Test
    public void parseVless_defaultNetwork_isTcp() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443");
        assertEquals("tcp", cfg.network);
    }

    @Test
    public void parseVless_defaultPath_isSlash() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443");
        assertEquals("/", cfg.path);
    }

    @Test
    public void parseVless_defaultSni_isHost() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443");
        assertEquals("example.com", cfg.sni);
    }

    // ── parseVless() validation ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void parseVless_emptyUuid_throws() throws Exception {
        ConfigParser.parse("vless://@example.com:443");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseVless_portZero_throws() throws Exception {
        ConfigParser.parse("vless://uuid@example.com:0");
    }

    @Test
    public void parseVless_port1_valid() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse("vless://uuid@example.com:1");
        assertEquals(1, cfg.port);
    }

    @Test
    public void parseVless_port65535_valid() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse("vless://uuid@example.com:65535");
        assertEquals(65535, cfg.port);
    }

    // ── parseVmess() ─────────────────────────────────────────────────────────

    @Test
    public void parseVmess_standardFields() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(VMESS_STANDARD);
        assertEquals("test-uuid", cfg.uuid);
        assertEquals("vpn.example.com", cfg.host);
        assertEquals(443, cfg.port);
        assertEquals("ws", cfg.network);
        assertEquals("tls", cfg.security);
        assertEquals("/ws", cfg.path);
        assertEquals("Test", cfg.name);
    }

    @Test
    public void parseVmess_tlsTrue_normalizedToTls() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(VMESS_TLS_TRUE);
        assertEquals("tls", cfg.security);
    }

    @Test
    public void parseVmess_tls1_normalizedToTls() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(VMESS_TLS_1);
        assertEquals("tls", cfg.security);
    }

    @Test
    public void parseVmess_noTls_securityNone() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(VMESS_NO_TLS);
        assertEquals("none", cfg.security);
    }

    @Test
    public void parseVmess_urlSafeBase64() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(VMESS_URL_SAFE);
        assertEquals("uuid6", cfg.uuid);
        assertEquals("h.com", cfg.host);
        assertEquals(8080, cfg.port);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseVmess_emptyUuid_throws() throws Exception {
        ConfigParser.parse(VMESS_EMPTY_UUID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseVmess_emptyHost_throws() throws Exception {
        ConfigParser.parse(VMESS_EMPTY_HOST);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseVmess_portZero_throws() throws Exception {
        ConfigParser.parse(VMESS_PORT_ZERO);
    }

    // ── ProxyConfig.toString() ───────────────────────────────────────────────

    @Test
    public void proxyConfig_toString_format() throws Exception {
        ConfigParser.ProxyConfig cfg = ConfigParser.parse(
            "vless://uuid@example.com:443?security=tls&type=ws");
        String s = cfg.toString();
        assertTrue(s.contains("vless://"));
        assertTrue(s.contains("example.com:443"));
        assertTrue(s.contains("ws/tls"));
    }
}
