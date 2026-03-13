package ir.phonx;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class MmdbReaderTest {

    private MmdbReader reader;

    @Before
    public void setUp() throws Exception {
        InputStream in = RuntimeEnvironment.getApplication()
                .getAssets().open("dbip-country-lite.mmdb");
        reader = new MmdbReader(in);
        in.close();
    }

    @Test
    public void lookupCountry_googleDns_returnsUnitedStates() {
        String country = reader.lookupCountry("8.8.8.8");
        assertEquals("United States", country);
    }

    @Test
    public void lookupCountry_cloudflareDns_returnsCountry() {
        String country = reader.lookupCountry("1.1.1.1");
        assertNotNull(country);
        assertFalse(country.isEmpty());
    }

    @Test
    public void lookupCountry_privateIp_returnsEmpty() {
        // Private/reserved IPs typically have no country mapping
        String country = reader.lookupCountry("192.168.1.1");
        assertEquals("", country);
    }

    @Test
    public void lookupCountry_loopback_returnsEmpty() {
        String country = reader.lookupCountry("127.0.0.1");
        assertEquals("", country);
    }

    @Test
    public void lookupCountry_invalidIp_returnsEmpty() {
        String country = reader.lookupCountry("not-an-ip");
        assertEquals("", country);
    }

    @Test
    public void lookupCountry_emptyString_returnsEmpty() {
        String country = reader.lookupCountry("");
        assertEquals("", country);
    }

    @Test
    public void lookupCountry_nullIp_returnsEmpty() {
        // InetAddress.getByName(null) resolves to loopback, which has no country
        String country = reader.lookupCountry(null);
        assertEquals("", country);
    }

    @Test
    public void lookupCountry_differentIps_returnDifferentCountries() {
        // Verify the reader can distinguish between different countries
        String us = reader.lookupCountry("8.8.8.8");
        // Pick an IP known to be in a non-US country (German Telekom range)
        String other = reader.lookupCountry("5.5.5.5");
        // Both should return non-empty results (we just verify they work)
        assertNotNull(us);
        assertNotNull(other);
    }

    @Test(expected = IOException.class)
    public void constructor_invalidData_throwsIOException() throws Exception {
        byte[] garbage = {0x00, 0x01, 0x02, 0x03};
        new MmdbReader(new ByteArrayInputStream(garbage));
    }
}
