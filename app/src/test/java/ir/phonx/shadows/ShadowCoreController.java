package ir.phonx.shadows;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Robolectric shadow for libv2ray.CoreController (final class with native methods).
 * Intercepts constructor and all instance methods to prevent JNI crashes in JVM tests.
 */
@Implements(CoreController.class)
public class ShadowCoreController {

    /** Prevents CoreController.static{} from calling Libv2ray.touch() (which loads natives). */
    @Implementation
    public static void __staticInitializer__() {
        // no-op
    }

    // Static fields — easy to assert in tests; reset via ShadowLibv2ray.reset()
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

    /** Intercepts public CoreController(CoreCallbackHandler) — prevents native __NewCoreController call */
    @Implementation
    protected void __constructor__(CoreCallbackHandler handler) {
        // no-op: do NOT call the real constructor (it calls a native method)
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
