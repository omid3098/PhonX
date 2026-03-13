package ir.phonx;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class IpCheckerTest {

    /** Test subclass that overrides fetchIp() to avoid real HTTP calls. */
    private static class FakeIpChecker extends IpChecker {
        IpInfo fakeResult = null;
        Exception fakeError = null;
        final AtomicInteger fetchCallCount = new AtomicInteger(0);

        @Override
        IpInfo fetchIp(int socksPort) throws Exception {
            fetchCallCount.incrementAndGet();
            if (fakeError != null) throw fakeError;
            if (fakeResult == null) throw new Exception("Empty response");
            return fakeResult;
        }
    }

    /** Subclass that fails N times then succeeds, to test retry logic. */
    private static class RetryFakeIpChecker extends IpChecker {
        final int failCount;
        final AtomicInteger fetchCallCount = new AtomicInteger(0);

        RetryFakeIpChecker(int failCount) {
            this.failCount = failCount;
        }

        @Override
        IpInfo fetchIp(int socksPort) throws Exception {
            int attempt = fetchCallCount.incrementAndGet();
            if (attempt <= failCount) throw new Exception("Attempt " + attempt + " failed");
            return new IpInfo("5.6.7.8", "Germany");
        }
    }

    private FakeIpChecker ipChecker;

    @Before
    public void setUp() {
        ipChecker = new FakeIpChecker();
    }

    @Test
    public void checkIp_success_callsOnIpResult() throws InterruptedException {
        ipChecker.fakeResult = new IpChecker.IpInfo("1.2.3.4", "Netherlands");
        IpChecker.IpInfo[] result = {null};
        ipChecker.checkIp(0, new IpChecker.Callback() {
            @Override public void onIpResult(IpChecker.IpInfo info) { result[0] = info; }
            @Override public void onIpError(String error) { fail("Should not error"); }
        });
        Thread.sleep(200);
        ShadowLooper.idleMainLooper();
        assertNotNull(result[0]);
        assertEquals("1.2.3.4", result[0].ip);
        assertEquals("Netherlands", result[0].country);
    }

    @Test
    public void checkIp_failure_callsOnIpError() throws InterruptedException {
        ipChecker.fakeError = new Exception("Network unreachable");
        String[] error = {null};
        ipChecker.checkIp(0, new IpChecker.Callback() {
            @Override public void onIpResult(IpChecker.IpInfo info) { fail("Should not succeed"); }
            @Override public void onIpError(String e) { error[0] = e; }
        });
        Thread.sleep(5500);
        ShadowLooper.idleMainLooper();
        assertEquals("Network unreachable", error[0]);
    }

    @Test
    public void checkIp_emptyResponse_callsOnIpError() throws InterruptedException {
        // fakeResult is null → fetchIp throws "Empty response"
        String[] error = {null};
        ipChecker.checkIp(0, new IpChecker.Callback() {
            @Override public void onIpResult(IpChecker.IpInfo info) { fail("Should not succeed"); }
            @Override public void onIpError(String e) { error[0] = e; }
        });
        Thread.sleep(5500);
        ShadowLooper.idleMainLooper();
        assertNotNull(error[0]);
    }

    @Test
    public void checkIp_retriesOnFailure() throws InterruptedException {
        ipChecker.fakeError = new Exception("fail");
        String[] error = {null};
        ipChecker.checkIp(0, new IpChecker.Callback() {
            @Override public void onIpResult(IpChecker.IpInfo info) { fail("Should not succeed"); }
            @Override public void onIpError(String e) { error[0] = e; }
        });
        Thread.sleep(5500);
        ShadowLooper.idleMainLooper();
        // Should have been called 3 times (MAX_RETRIES)
        assertEquals(3, ipChecker.fetchCallCount.get());
    }

    @Test
    public void checkIp_succeedsAfterRetries() throws InterruptedException {
        RetryFakeIpChecker retryChecker = new RetryFakeIpChecker(2);
        IpChecker.IpInfo[] result = {null};
        retryChecker.checkIp(0, new IpChecker.Callback() {
            @Override public void onIpResult(IpChecker.IpInfo info) { result[0] = info; }
            @Override public void onIpError(String error) { fail("Should succeed on third try"); }
        });
        Thread.sleep(5500);
        ShadowLooper.idleMainLooper();
        assertNotNull(result[0]);
        assertEquals("5.6.7.8", result[0].ip);
        assertEquals("Germany", result[0].country);
        assertEquals(3, retryChecker.fetchCallCount.get());
    }

    @Test
    public void checkIp_passesSocksPort() throws InterruptedException {
        int[] capturedPort = {-1};
        IpChecker portChecker = new IpChecker() {
            @Override
            IpInfo fetchIp(int socksPort) throws Exception {
                capturedPort[0] = socksPort;
                return new IpInfo("9.8.7.6", "Japan");
            }
        };
        IpChecker.IpInfo[] result = {null};
        portChecker.checkIp(10809, new IpChecker.Callback() {
            @Override public void onIpResult(IpChecker.IpInfo info) { result[0] = info; }
            @Override public void onIpError(String error) { fail("Should not error"); }
        });
        // socksPort > 0 triggers a 1s initial delay in checkIp
        Thread.sleep(1500);
        ShadowLooper.idleMainLooper();
        assertEquals(10809, capturedPort[0]);
        assertEquals("9.8.7.6", result[0].ip);
    }

    @Test
    public void ipInfo_holdsIpAndCountry() {
        IpChecker.IpInfo info = new IpChecker.IpInfo("1.1.1.1", "Australia");
        assertEquals("1.1.1.1", info.ip);
        assertEquals("Australia", info.country);
    }
}
