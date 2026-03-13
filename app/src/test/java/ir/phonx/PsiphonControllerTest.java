package ir.phonx;

import android.content.Context;
import android.net.VpnService;

import androidx.test.core.app.ApplicationProvider;

import ir.phonx.shadows.ShadowGoPsiphonController;
import ir.phonx.shadows.ShadowPhonxcore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
@Config(shadows = {ShadowPhonxcore.class, ShadowGoPsiphonController.class})
public class PsiphonControllerTest {

    private PsiphonController controller;
    private VpnService vpnService;

    @Before
    public void setUp() {
        ShadowPhonxcore.reset();
        Context ctx = ApplicationProvider.getApplicationContext();
        controller = new PsiphonController(ctx);
        // Use a simple Robolectric VpnService for the protect() call
        vpnService = Robolectric.buildService(PhonXVpnService.class).create().get();
    }

    @Test
    public void constructor_noException() {
        assertNotNull(controller);
    }

    @Test
    public void start_callsGoController_returnsPort() throws Exception {
        ShadowGoPsiphonController.simulatedSocksPort = 1081;
        int port = controller.start(vpnService);
        assertEquals(1081, port);
    }

    @Test
    public void start_passesConfigAndDataDir() throws Exception {
        controller.start(vpnService);
        assertNotNull(ShadowGoPsiphonController.lastConfigJson);
        assertFalse(ShadowGoPsiphonController.lastConfigJson.isEmpty());
        assertNotNull(ShadowGoPsiphonController.lastDataDir);
        assertTrue(ShadowGoPsiphonController.lastDataDir.contains("psiphon"));
    }

    @Test(expected = Exception.class)
    public void start_whenGoControllerFails_throwsException() throws Exception {
        ShadowGoPsiphonController.simulateFailure = true;
        controller.start(vpnService);
    }

    @Test
    public void stop_callsGoControllerStop() throws Exception {
        controller.start(vpnService);
        controller.stop();
        assertTrue(ShadowGoPsiphonController.stopCalled);
    }

    @Test
    public void stop_beforeStart_noException() {
        controller.stop(); // should not throw
        assertFalse(controller.isRunning());
    }

    @Test
    public void getSocksPort_afterStart() throws Exception {
        ShadowGoPsiphonController.simulatedSocksPort = 9999;
        controller.start(vpnService);
        assertEquals(9999, controller.getSocksPort());
    }

    @Test
    public void getSocksPort_afterStop_isZero() throws Exception {
        controller.start(vpnService);
        controller.stop();
        assertEquals(0, controller.getSocksPort());
    }

    @Test
    public void statusListener_receivesCallbacks() throws Exception {
        boolean[] connected = {false};
        int[] receivedPort = {0};

        controller.setStatusListener(new PsiphonController.StatusListener() {
            @Override public void onConnecting() {}
            @Override public void onConnected(int socksPort) {
                connected[0] = true;
                receivedPort[0] = socksPort;
            }
            @Override public void onDisconnected() {}
            @Override public void onError(String message) {}
        });

        ShadowGoPsiphonController.simulatedSocksPort = 2222;
        controller.start(vpnService);

        assertTrue(connected[0]);
        assertEquals(2222, receivedPort[0]);
    }

    @Test
    public void start_afterStop_canRestartCleanly() throws Exception {
        controller.start(vpnService);
        controller.stop();

        ShadowGoPsiphonController.reset();
        ShadowGoPsiphonController.simulatedSocksPort = 3333;

        int port = controller.start(vpnService);
        assertEquals(3333, port);
        assertTrue(controller.isRunning());
    }

    @Test
    public void isRunning_correctStates() throws Exception {
        assertFalse(controller.isRunning());
        controller.start(vpnService);
        assertTrue(controller.isRunning());
        controller.stop();
        assertFalse(controller.isRunning());
    }
}
