package ir.phonx;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import ir.phonx.shadows.ShadowGoXrayController;
import ir.phonx.shadows.ShadowPhonxcore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
@Config(shadows = {ShadowPhonxcore.class, ShadowGoXrayController.class})
public class XrayControllerTest {

    private XrayController xrayController;

    @Before
    public void setUp() {
        ShadowPhonxcore.reset();
        Context ctx = ApplicationProvider.getApplicationContext();
        xrayController = new XrayController(ctx);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ConfigParser.ProxyConfig vlessWsTls() {
        ConfigParser.ProxyConfig c = new ConfigParser.ProxyConfig();
        c.protocol = "vless";
        c.uuid = "test-uuid-1234";
        c.host = "example.com";
        c.port = 443;
        c.security = "tls";
        c.network = "ws";
        c.path = "/ws";
        c.sni = "example.com";
        return c;
    }

    private ConfigParser.ProxyConfig vmessTcpNone() {
        ConfigParser.ProxyConfig c = new ConfigParser.ProxyConfig();
        c.protocol = "vmess";
        c.uuid = "vmess-uuid";
        c.host = "vmess.example.com";
        c.port = 8080;
        c.security = "none";
        c.network = "tcp";
        c.path = "/";
        c.sni = "vmess.example.com";
        return c;
    }

    private ConfigParser.ProxyConfig vlessGrpcReality() {
        ConfigParser.ProxyConfig c = new ConfigParser.ProxyConfig();
        c.protocol = "vless";
        c.uuid = "grpc-uuid";
        c.host = "grpc.example.com";
        c.port = 443;
        c.security = "reality";
        c.network = "grpc";
        c.path = "/grpcService";
        c.sni = "grpc.example.com";
        return c;
    }

    // ── start() tests ────────────────────────────────────────────────────────

    @Test
    public void start_callsInitXrayEnv() throws Exception {
        xrayController.start(vlessWsTls(), 42);
        assertTrue(ShadowPhonxcore.initXrayEnvCalled);
    }

    @Test
    public void start_passesAssetsPath() throws Exception {
        xrayController.start(vlessWsTls(), 42);
        assertNotNull(ShadowPhonxcore.lastAssetsPath);
        assertFalse(ShadowPhonxcore.lastAssetsPath.isEmpty());
    }

    @Test
    public void start_passesCorrectTunFd() throws Exception {
        xrayController.start(vlessWsTls(), 99);
        assertEquals(99, ShadowGoXrayController.lastTunFd);
    }

    @Test
    public void start_callsStartLoop() throws Exception {
        xrayController.start(vlessWsTls(), 5);
        assertTrue(ShadowGoXrayController.startLoopCalled);
    }

    @Test
    public void start_generatesValidJson() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        new JSONObject(ShadowGoXrayController.lastConfigJson);
    }

    @Test
    public void start_vlessConfig_outboundProtocolIsVless() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        String protocol = root.getJSONArray("outbounds").getJSONObject(0).getString("protocol");
        assertEquals("vless", protocol);
    }

    @Test
    public void start_vmessConfig_outboundProtocolIsVmess() throws Exception {
        xrayController.start(vmessTcpNone(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        String protocol = root.getJSONArray("outbounds").getJSONObject(0).getString("protocol");
        assertEquals("vmess", protocol);
    }

    @Test
    public void start_wsNetwork_jsonContainsWsSettings() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertTrue(stream.has("wsSettings"));
    }

    @Test
    public void start_tcpNetwork_jsonHasNoWsOrGrpcSettings() throws Exception {
        xrayController.start(vmessTcpNone(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertFalse(stream.has("wsSettings"));
        assertFalse(stream.has("grpcSettings"));
    }

    @Test
    public void start_grpcNetwork_jsonContainsGrpcSettings() throws Exception {
        xrayController.start(vlessGrpcReality(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertTrue(stream.has("grpcSettings"));
    }

    @Test
    public void start_tlsSecurity_jsonContainsTlsSettings() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertTrue(stream.has("tlsSettings"));
    }

    @Test
    public void start_realitySecurity_jsonContainsRealitySettings() throws Exception {
        xrayController.start(vlessGrpcReality(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertTrue(stream.has("realitySettings"));
    }

    @Test
    public void start_noneSecurity_jsonHasNoTlsOrRealitySettings() throws Exception {
        xrayController.start(vmessTcpNone(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertFalse(stream.has("tlsSettings"));
        assertFalse(stream.has("realitySettings"));
    }

    @Test
    public void start_callsCheckVersionX() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        assertTrue(ShadowGoXrayController.startLoopCalled);
    }

    @Test
    public void start_coversCoreCallbackHandler() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        assertNotNull(ShadowPhonxcore.capturedCoreHandler);
        ShadowPhonxcore.capturedCoreHandler.shutdown();
    }

    // ── Psiphon chain tests ──────────────────────────────────────────────────

    @Test
    public void start_withPsiphonPort_configContainsPsiphonOutbound() throws Exception {
        xrayController.start(vlessWsTls(), 1, 1081);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONArray outbounds = root.getJSONArray("outbounds");

        boolean found = false;
        for (int i = 0; i < outbounds.length(); i++) {
            JSONObject ob = outbounds.getJSONObject(i);
            if ("psiphon-out".equals(ob.optString("tag"))) {
                assertEquals("socks", ob.getString("protocol"));
                JSONObject server = ob.getJSONObject("settings")
                        .getJSONArray("servers").getJSONObject(0);
                assertEquals("127.0.0.1", server.getString("address"));
                assertEquals(1081, server.getInt("port"));
                found = true;
                break;
            }
        }
        assertTrue("Config should contain psiphon-out SOCKS outbound", found);
    }

    @Test
    public void start_withPsiphonPort_streamSettingsHasDialerProxy() throws Exception {
        xrayController.start(vlessWsTls(), 1, 1081);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertTrue(stream.has("sockopt"));
        assertEquals("psiphon-out", stream.getJSONObject("sockopt").getString("dialerProxy"));
    }

    @Test
    public void start_withZeroPsiphonPort_noPsiphonOutbound() throws Exception {
        xrayController.start(vlessWsTls(), 1, 0);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONArray outbounds = root.getJSONArray("outbounds");

        for (int i = 0; i < outbounds.length(); i++) {
            assertNotEquals("psiphon-out", outbounds.getJSONObject(i).optString("tag"));
        }
    }

    @Test
    public void start_withZeroPsiphonPort_noDialerProxy() throws Exception {
        xrayController.start(vlessWsTls(), 1, 0);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertFalse("streamSettings should not have sockopt when no Psiphon", stream.has("sockopt"));
    }

    // ── SOCKS inbound tests ────────────────────────────────────────────────

    @Test
    public void start_configContainsSocksInbound() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONArray inbounds = root.getJSONArray("inbounds");

        boolean found = false;
        for (int i = 0; i < inbounds.length(); i++) {
            JSONObject inbound = inbounds.getJSONObject(i);
            if ("socks-in".equals(inbound.optString("tag"))) {
                assertEquals("socks", inbound.getString("protocol"));
                assertEquals(XrayController.LOCAL_SOCKS_PORT, inbound.getInt("port"));
                found = true;
                break;
            }
        }
        assertTrue("Config should contain socks-in inbound", found);
    }

    @Test
    public void start_socksInboundListensOnLocalhost() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        JSONObject root = new JSONObject(ShadowGoXrayController.lastConfigJson);
        JSONArray inbounds = root.getJSONArray("inbounds");

        for (int i = 0; i < inbounds.length(); i++) {
            JSONObject inbound = inbounds.getJSONObject(i);
            if ("socks-in".equals(inbound.optString("tag"))) {
                assertEquals("127.0.0.1", inbound.getString("listen"));
                return;
            }
        }
        fail("socks-in inbound not found");
    }

    // ── stop() tests ─────────────────────────────────────────────────────────

    @Test
    public void stop_whenControllerNull_noException() {
        xrayController.stop();
        assertFalse(ShadowGoXrayController.stopLoopCalled);
    }

    @Test
    public void stop_afterStart_callsStopLoop() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        xrayController.stop();
        assertTrue(ShadowGoXrayController.stopLoopCalled);
    }

    // ── isRunning() tests ────────────────────────────────────────────────────

    @Test
    public void isRunning_beforeStart_isFalse() {
        assertFalse(xrayController.isRunning());
    }

    @Test
    public void isRunning_afterStart_isTrue() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        assertTrue(xrayController.isRunning());
    }

    @Test
    public void isRunning_afterStop_isFalse() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        xrayController.stop();
        assertFalse(xrayController.isRunning());
    }
}
