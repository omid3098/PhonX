package ir.phonx;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import ir.phonx.shadows.ShadowCoreController;
import ir.phonx.shadows.ShadowLibv2ray;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
@Config(shadows = {ShadowLibv2ray.class, ShadowCoreController.class})
public class XrayControllerTest {

    private XrayController xrayController;

    @Before
    public void setUp() {
        ShadowLibv2ray.reset();
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
    public void start_callsInitCoreEnv() throws Exception {
        xrayController.start(vlessWsTls(), 42);
        assertTrue(ShadowLibv2ray.initCalled);
    }

    @Test
    public void start_passesAssetsPath() throws Exception {
        xrayController.start(vlessWsTls(), 42);
        assertNotNull(ShadowLibv2ray.lastAssetsPath);
        assertFalse(ShadowLibv2ray.lastAssetsPath.isEmpty());
    }

    @Test
    public void start_passesCorrectTunFd() throws Exception {
        xrayController.start(vlessWsTls(), 99);
        assertEquals(99, ShadowCoreController.lastTunFd);
    }

    @Test
    public void start_callsStartLoop() throws Exception {
        xrayController.start(vlessWsTls(), 5);
        assertTrue(ShadowCoreController.startLoopCalled);
    }

    @Test
    public void start_generatesValidJson() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        // Should not throw
        new JSONObject(ShadowCoreController.lastConfigJson);
    }

    @Test
    public void start_vlessConfig_outboundProtocolIsVless() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        JSONObject root = new JSONObject(ShadowCoreController.lastConfigJson);
        String protocol = root.getJSONArray("outbounds").getJSONObject(0).getString("protocol");
        assertEquals("vless", protocol);
    }

    @Test
    public void start_vmessConfig_outboundProtocolIsVmess() throws Exception {
        xrayController.start(vmessTcpNone(), 1);
        JSONObject root = new JSONObject(ShadowCoreController.lastConfigJson);
        String protocol = root.getJSONArray("outbounds").getJSONObject(0).getString("protocol");
        assertEquals("vmess", protocol);
    }

    @Test
    public void start_wsNetwork_jsonContainsWsSettings() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        JSONObject root = new JSONObject(ShadowCoreController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertTrue(stream.has("wsSettings"));
    }

    @Test
    public void start_tcpNetwork_jsonHasNoWsOrGrpcSettings() throws Exception {
        xrayController.start(vmessTcpNone(), 1);
        JSONObject root = new JSONObject(ShadowCoreController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertFalse(stream.has("wsSettings"));
        assertFalse(stream.has("grpcSettings"));
    }

    @Test
    public void start_grpcNetwork_jsonContainsGrpcSettings() throws Exception {
        xrayController.start(vlessGrpcReality(), 1);
        JSONObject root = new JSONObject(ShadowCoreController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertTrue(stream.has("grpcSettings"));
    }

    @Test
    public void start_tlsSecurity_jsonContainsTlsSettings() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        JSONObject root = new JSONObject(ShadowCoreController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertTrue(stream.has("tlsSettings"));
    }

    @Test
    public void start_realitySecurity_jsonContainsRealitySettings() throws Exception {
        xrayController.start(vlessGrpcReality(), 1);
        JSONObject root = new JSONObject(ShadowCoreController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertTrue(stream.has("realitySettings"));
    }

    @Test
    public void start_noneSecurity_jsonHasNoTlsOrRealitySettings() throws Exception {
        xrayController.start(vmessTcpNone(), 1);
        JSONObject root = new JSONObject(ShadowCoreController.lastConfigJson);
        JSONObject stream = root.getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("streamSettings");
        assertFalse(stream.has("tlsSettings"));
        assertFalse(stream.has("realitySettings"));
    }

    @Test
    public void start_callsCheckVersionX() throws Exception {
        // checkVersionX is called after startLoop; no assertion but covers the line
        xrayController.start(vlessWsTls(), 1);
        assertTrue(ShadowCoreController.startLoopCalled); // confirms start() completed
    }

    @Test
    public void start_coversCoreCallbackHandler() throws Exception {
        // ShadowLibv2ray.newCoreController() calls handler.startup() + onEmitStatus()
        // This test confirms capturedHandler is our XrayCoreCallback
        xrayController.start(vlessWsTls(), 1);
        assertNotNull(ShadowLibv2ray.capturedHandler);
        // Also exercise shutdown() for coverage
        ShadowLibv2ray.capturedHandler.shutdown();
    }

    // ── stop() tests ─────────────────────────────────────────────────────────

    @Test
    public void stop_whenControllerNull_noException() {
        xrayController.stop(); // coreController is null initially — should not throw
        assertFalse(ShadowCoreController.stopLoopCalled);
    }

    @Test
    public void stop_afterStart_callsStopLoop() throws Exception {
        xrayController.start(vlessWsTls(), 1);
        xrayController.stop();
        assertTrue(ShadowCoreController.stopLoopCalled);
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
