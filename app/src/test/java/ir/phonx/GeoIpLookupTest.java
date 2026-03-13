package ir.phonx;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

@RunWith(PhonXTestRunner.class)
public class GeoIpLookupTest {

    private GeoIpLookup lookup;

    @Before
    public void setUp() {
        lookup = new GeoIpLookup(RuntimeEnvironment.getApplication());
    }

    @Test
    public void lookupCountry_knownIp_returnsCountry() {
        String country = lookup.lookupCountry("8.8.8.8");
        assertEquals("United States", country);
    }

    @Test
    public void lookupCountry_nullIp_returnsEmpty() {
        assertEquals("", lookup.lookupCountry(null));
    }

    @Test
    public void lookupCountry_emptyIp_returnsEmpty() {
        assertEquals("", lookup.lookupCountry(""));
    }

    @Test
    public void lookupCountry_privateIp_returnsEmpty() {
        assertEquals("", lookup.lookupCountry("10.0.0.1"));
    }

    @Test
    public void lookupCountry_nullReader_returnsEmpty() {
        GeoIpLookup nullLookup = new GeoIpLookup((MmdbReader) null);
        assertEquals("", nullLookup.lookupCountry("8.8.8.8"));
    }
}
