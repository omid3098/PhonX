package ir.phonx.shadows;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Robolectric shadow for phonxcore.PsiphonController (gomobile-generated final class).
 * Intercepts constructor and all instance methods to prevent JNI crashes in JVM tests.
 *
 * By default, simulates a successful connection on port 1081.
 * Set {@link #simulateFailure} to true to simulate start() throwing an error.
 */
@Implements(phonxcore.PsiphonController.class)
public class ShadowGoPsiphonController {

    /** Prevents phonxcore.PsiphonController.static{} from loading native libs. */
    @Implementation
    public static void __staticInitializer__() {
        // no-op
    }

    // Configuration
    public static int simulatedSocksPort = 1081;
    public static boolean simulateFailure = false;
    public static String simulateFailureMessage = "Simulated Psiphon failure";

    // Capture fields for assertions
    public static String lastConfigJson = null;
    public static String lastDataDir = null;
    public static boolean startCalled = false;
    public static boolean stopCalled = false;

    private boolean running = false;

    public static void reset() {
        simulatedSocksPort = 1081;
        simulateFailure = false;
        simulateFailureMessage = "Simulated Psiphon failure";
        lastConfigJson = null;
        lastDataDir = null;
        startCalled = false;
        stopCalled = false;
    }

    /** Intercepts default constructor — prevents native init. */
    @Implementation
    protected void __constructor__() {
        // no-op
    }

    @Implementation
    public void start(String configJson, String dataDir) throws Exception {
        lastConfigJson = configJson;
        lastDataDir = dataDir;
        startCalled = true;

        if (simulateFailure) {
            throw new Exception(simulateFailureMessage);
        }

        running = true;

        // Simulate the callback: notify the handler that Psiphon connected.
        // The PsiphonCallbackHandler was captured in ShadowPhonxcore.newPsiphonController().
        // We call onConnected() to unblock the CountDownLatch in PsiphonController.start().
        if (ShadowPhonxcore.capturedPsiphonHandler != null) {
            ShadowPhonxcore.capturedPsiphonHandler.onConnected(simulatedSocksPort);
        }
    }

    @Implementation
    public void stop() {
        stopCalled = true;
        running = false;
    }

    @Implementation
    public boolean getIsRunning() {
        return running;
    }

    @Implementation
    public int getSOCKSPort() {
        return running ? simulatedSocksPort : 0;
    }
}
