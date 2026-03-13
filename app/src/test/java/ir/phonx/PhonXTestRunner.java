package ir.phonx;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;

/**
 * Custom Robolectric test runner that instruments the phonxcore package,
 * enabling ShadowPhonxcore, ShadowGoXrayController, and ShadowGoPsiphonController
 * to intercept native calls from the combined Go module.
 */
public class PhonXTestRunner extends RobolectricTestRunner {

    public PhonXTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected InstrumentationConfiguration createClassLoaderConfig(FrameworkMethod method) {
        return new InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
                .addInstrumentedPackage("phonxcore")
                .build();
    }
}
