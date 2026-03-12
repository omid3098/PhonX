package ir.phonx.shadows;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Robolectric shadow for libv2ray.Libv2ray (abstract class with static native methods).
 * Intercepts all static calls; also exercises XrayCoreCallback by calling handler methods.
 */
@Implements(Libv2ray.class)
public class ShadowLibv2ray {

    /** Prevents Libv2ray.static{} from calling go.Seq.touch() + Libv2ray._init() (native). */
    @Implementation
    public static void __staticInitializer__() {
        // no-op
    }

    public static boolean initCalled = false;
    public static String lastAssetsPath = null;
    public static CoreCallbackHandler capturedHandler = null;

    public static void reset() {
        initCalled = false;
        lastAssetsPath = null;
        capturedHandler = null;
        ShadowCoreController.reset();
    }

    @Implementation
    public static void initCoreEnv(String assetsPath, String key) {
        initCalled = true;
        lastAssetsPath = assetsPath;
    }

    /**
     * Returns a new CoreController whose constructor is intercepted by ShadowCoreController.
     * Also calls handler.startup() and handler.onEmitStatus() to cover XrayCoreCallback lines.
     */
    @Implementation
    public static CoreController newCoreController(CoreCallbackHandler handler) {
        capturedHandler = handler;
        if (handler != null) {
            handler.startup();
            handler.onEmitStatus(0L, "test status");
        }
        // ShadowCoreController.__constructor__ intercepts this — no JNI call happens
        return new CoreController(handler);
    }

    @Implementation
    public static String checkVersionX() {
        return "Xray-test-version";
    }
}
