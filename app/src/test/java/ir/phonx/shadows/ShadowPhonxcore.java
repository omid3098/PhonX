package ir.phonx.shadows;

import phonxcore.CoreCallbackHandler;
import phonxcore.PsiphonCallbackHandler;
import phonxcore.Phonxcore;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;

/**
 * Robolectric shadow for phonxcore.Phonxcore (gomobile-generated static utility class).
 * Intercepts all static calls; also exercises callback handlers for coverage.
 */
@Implements(Phonxcore.class)
public class ShadowPhonxcore {

    /** Prevents Phonxcore.static{} from calling go.Seq.touch() + native init. */
    @Implementation
    public static void __staticInitializer__() {
        // no-op
    }

    public static boolean initXrayEnvCalled = false;
    public static String lastAssetsPath = null;
    public static CoreCallbackHandler capturedCoreHandler = null;
    public static PsiphonCallbackHandler capturedPsiphonHandler = null;

    public static void reset() {
        initXrayEnvCalled = false;
        lastAssetsPath = null;
        capturedCoreHandler = null;
        capturedPsiphonHandler = null;
        ShadowGoXrayController.reset();
        ShadowGoPsiphonController.reset();
    }

    @Implementation
    public static void initXrayEnv(String assetsPath, String key) {
        initXrayEnvCalled = true;
        lastAssetsPath = assetsPath;
    }

    /**
     * Returns a new phonxcore.XrayController whose constructor is intercepted by ShadowGoXrayController.
     * Calls handler.startup() and handler.onEmitStatus() to cover callback lines.
     */
    @Implementation
    public static phonxcore.XrayController newXrayController(CoreCallbackHandler handler) {
        capturedCoreHandler = handler;
        if (handler != null) {
            handler.startup();
            handler.onEmitStatus(0L, "test status");
        }
        // Use Shadow.newInstanceOf to bypass native constructor
        return Shadow.newInstanceOf(phonxcore.XrayController.class);
    }

    @Implementation
    public static phonxcore.PsiphonController newPsiphonController(PsiphonCallbackHandler handler) {
        capturedPsiphonHandler = handler;
        // Use Shadow.newInstanceOf to bypass native constructor
        return Shadow.newInstanceOf(phonxcore.PsiphonController.class);
    }

    @Implementation
    public static String checkVersionX() {
        return "Xray-test-version";
    }
}
