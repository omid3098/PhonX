package ir.phonx.shadows;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Robolectric shadow for phonxcore.XrayController (gomobile-generated final class).
 * Intercepts constructor and all instance methods to prevent JNI crashes in JVM tests.
 *
 * Named "ShadowGoXrayController" to avoid confusion with ir.phonx.XrayController.
 */
@Implements(phonxcore.XrayController.class)
public class ShadowGoXrayController {

    /** Prevents phonxcore.XrayController.static{} from loading native libs. */
    @Implementation
    public static void __staticInitializer__() {
        // no-op
    }

    // Static fields for test assertions
    public static String lastConfigJson = null;
    public static int lastTunFd = -1;
    public static boolean startLoopCalled = false;
    public static boolean stopLoopCalled = false;

    private boolean running = false;

    public static void reset() {
        lastConfigJson = null;
        lastTunFd = -1;
        startLoopCalled = false;
        stopLoopCalled = false;
    }

    /** Intercepts default constructor — prevents native init. */
    @Implementation
    protected void __constructor__() {
        // no-op
    }

    @Implementation
    public void startLoop(String configJson, int tunFd) throws Exception {
        lastConfigJson = configJson;
        lastTunFd = tunFd;
        startLoopCalled = true;
        running = true;
    }

    @Implementation
    public void stopLoop() throws Exception {
        stopLoopCalled = true;
        running = false;
    }

    @Implementation
    public boolean getIsRunning() {
        return running;
    }
}
