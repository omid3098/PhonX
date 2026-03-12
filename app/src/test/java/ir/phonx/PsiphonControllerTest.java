package ir.phonx;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class PsiphonControllerTest {

    private PsiphonController getController() {
        Context ctx = ApplicationProvider.getApplicationContext();
        return new PsiphonController(ctx);
    }

    @Test
    public void constructor_noException() {
        assertNotNull(getController());
    }

    @Test
    public void isRunning_alwaysFalse() {
        assertFalse(getController().isRunning());
    }

    @Test
    public void start_noException() {
        getController().start("socks5://127.0.0.1:10809");
    }

    @Test
    public void stop_noException() {
        getController().stop();
    }

    @Test
    public void stopBeforeStart_noException() {
        PsiphonController ctrl = getController();
        ctrl.stop(); // should not throw even though never started
        assertFalse(ctrl.isRunning());
    }
}
