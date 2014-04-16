package net.onrc.onos.apps.bgproute;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import com.google.common.net.InetAddresses;

public class Prefix {
    private static final int MAX_BYTES = 4;

    private final int prefixLength;
    private final byte[] address;

    //For verifying the arguments and pretty printing
    private final InetAddress inetAddress;

    public Prefix(byte[] addr, int prefixLength) {
        if (addr == null || addr.length != MAX_BYTES ||
                prefixLength < 0 || prefixLength > MAX_BYTES * Byte.SIZE) {
            throw new IllegalArgumentException();
        }

        address = canonicalizeAddress(addr, prefixLength);
        this.prefixLength = prefixLength;

        try {
            inetAddress = InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Couldn't parse IP address", e);
        }
    }

    public Prefix(String strAddress, int prefixLength) {
        byte[] addr = null;
        addr = InetAddresses.forString(strAddress).getAddress();

        if (addr == null || addr.length != MAX_BYTES ||
                prefixLength < 0 || prefixLength > MAX_BYTES * Byte.SIZE) {
            throw new IllegalArgumentException();
        }

        address = canonicalizeAddress(addr, prefixLength);
        this.prefixLength = prefixLength;

        try {
            inetAddress = InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Couldn't parse IP address", e);
        }
    }

    private byte[] canonicalizeAddress(byte[] addressValue,
                                       int prefixLengthValue) {
        byte[] result = new byte[addressValue.length];

        if (prefixLengthValue == 0) {
            for (int i = 0; i < MAX_BYTES; i++) {
                result[i] = 0;
            }

            return result;
        }

        result = Arrays.copyOf(addressValue, addressValue.length);

        //Set all bytes after the end of the prefix to 0
        int lastByteIndex = (prefixLengthValue - 1) / Byte.SIZE;
        for (int i = lastByteIndex; i < MAX_BYTES; i++) {
            result[i] = 0;
        }

        byte lastByte = addressValue[lastByteIndex];
        byte mask = 0;
        byte msb = (byte) 0x80;
        int lastBit = (prefixLengthValue - 1) % Byte.SIZE;
        for (int i = 0; i < Byte.SIZE; i++) {
            if (i <= lastBit) {
                mask |= (msb >> i);
            }
        }

        result[lastByteIndex] = (byte) (lastByte & mask);

        return result;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public byte[] getAddress() {
        return Arrays.copyOf(address, address.length);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Prefix)) {
            return false;
        }

        Prefix otherPrefix = (Prefix) other;

        return (Arrays.equals(address, otherPrefix.address)) &&
                (prefixLength == otherPrefix.prefixLength);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + prefixLength;
        hash = 31 * hash + Arrays.hashCode(address);
        return hash;
    }

    @Override
    public String toString() {
        return inetAddress.getHostAddress() + "/" + prefixLength;
    }

    public String printAsBits() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < address.length; i++) {
            byte b = address[i];
            for (int j = 0; j < Byte.SIZE; j++) {
                byte mask = (byte) (0x80 >>> j);
                result.append(((b & mask) == 0) ? "0" : "1");
                if (i * Byte.SIZE + j == prefixLength - 1) {
                    return result.toString();
                }
            }
            result.append(' ');
        }
        return result.substring(0, result.length() - 1);
    }
}
