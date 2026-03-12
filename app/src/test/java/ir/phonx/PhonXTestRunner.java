package ir.phonx;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;

/**
 * Custom Robolectric test runner that instruments the libv2ray package,
 * enabling ShadowLibv2ray and ShadowCoreController to intercept native calls.
 */
public class PhonXTestRunner extends RobolectricTestRunner {

    public PhonXTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected InstrumentationConfiguration createClassLoaderConfig(FrameworkMethod method) {
        // Use the copy constructor to inherit parent config, then add libv2ray instrumentation
        return new InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
                .addInstrumentedPackage("libv2ray")
                .build();
    }
}
