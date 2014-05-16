package net.onrc.onos.apps.sdnip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PrefixTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testPrefixByteArray() {
        byte[] b1 = new byte[]{(byte) 0x8f, (byte) 0xa0, (byte) 0x00, (byte) 0x00};
        byte[] b2 = new byte[]{(byte) 0x8f, (byte) 0xa0, (byte) 0xff, (byte) 0xff};
        byte[] b3 = new byte[]{(byte) 0x8f, (byte) 0xac, (byte) 0x00, (byte) 0x00};
        byte[] b4 = new byte[]{(byte) 0x8f, (byte) 0xa0, (byte) 0x00, (byte) 0x00};

        Prefix p1 = new Prefix(b1, 12);
        Prefix p2 = new Prefix(b2, 12);
        Prefix p3 = new Prefix(b3, 12);
        Prefix p4 = new Prefix(b4, 11);

        //Have different byte arrays, but should be equal after construction
        assertTrue(p1.equals(p2));
        assertTrue(p2.equals(p3));

        //Same byte array, but should be false
        assertFalse(p1.equals(p4));

        assertTrue(Arrays.equals(p1.getAddress(), p3.getAddress()));
        assertTrue(p1.toString().equals(p2.toString()));
        assertTrue(Arrays.equals(p1.getAddress(), p4.getAddress()));
        assertFalse(p1.toString().equals(p4.toString()));
    }

    @Test
    public void testPrefixString() {
        Prefix p1 = new Prefix("192.168.166.0", 24);
        Prefix p2 = new Prefix("192.168.166.0", 23);
        Prefix p3 = new Prefix("192.168.166.128", 24);
        Prefix p4 = new Prefix("192.168.166.128", 25);

        assertFalse(p1.equals(p2));
        assertTrue(Arrays.equals(p1.getAddress(), p2.getAddress()));

        assertTrue(p1.equals(p3));
        assertTrue(Arrays.equals(p1.getAddress(), p2.getAddress()));

        assertFalse(p3.equals(p4));
        assertFalse(Arrays.equals(p3.getAddress(), p4.getAddress()));

        assertTrue(p1.toString().equals(p3.toString()));
        assertEquals(p1.hashCode(), p3.hashCode());
    }

    @Test
    public void testPrefixReturnsSame() {
        //Create a prefix of all 1s for each prefix length.
        //Check that Prefix doesn't mangle it
        final int MAX_PREFIX_LENGTH = 32;
        for (int prefixLength = 1; prefixLength <= MAX_PREFIX_LENGTH; prefixLength++) {
            byte[] address = new byte[MAX_PREFIX_LENGTH / Byte.SIZE];

            int lastByte = (prefixLength - 1) / Byte.SIZE;
            int lastBit = (prefixLength - 1) % Byte.SIZE;

            for (int j = 0; j < address.length; j++) {
                if (j < lastByte) {
                    address[j] = (byte) 0xff;
                } else if (j == lastByte) {
                    byte b = 0;
                    byte msb = (byte) 0x80;
                    for (int k = 0; k < Byte.SIZE; k++) {
                        if (k <= lastBit) {
                            b |= (msb >> k);
                        }
                    }
                    address[j] = b;
                } else {
                    address[j] = 0;
                }
            }

            Prefix p = new Prefix(address, prefixLength);
            System.out.println(p.printAsBits());
            assertTrue(Arrays.equals(address, p.getAddress()));
        }
    }
}
