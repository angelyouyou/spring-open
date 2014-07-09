package net.onrc.onos.apps.sdnip;

import java.net.InetAddress;
import java.util.Objects;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.net.InetAddresses;

/**
 * Configuration details for a BGP peer. It contains the peer's IP address and
 * an interface name which maps to the interface they are attached at.
 */
public class BgpPeer {
    private final String interfaceName;
    private final InetAddress ipAddress;

    /**
     * Class constructor, taking the interface name and IP address of the peer.
     *
     * @param interfaceName the String name of the interface which can be used
     * to look up the interface this peer is attached at
     * @param ipAddress the IP address of the peer as a String
     */
    public BgpPeer(@JsonProperty("interface") String interfaceName,
                   @JsonProperty("ipAddress") String ipAddress) {
        this.interfaceName = interfaceName;
        this.ipAddress = InetAddresses.forString(ipAddress);
    }

    /**
     * Gets the interface name.
     *
     * @return the interface name as a String
     */
    public String getInterfaceName() {
        return interfaceName;
    }

    /**
     * Gets the IP address of the peer.
     *
     * @return the IP address as an {@link InetAddress} object
     */
    public InetAddress getIpAddress() {
        return ipAddress;
    }

    @Override
    public int hashCode() {
        return Objects.hash(interfaceName, ipAddress);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof BgpPeer)) {
            return false;
        }

        BgpPeer that = (BgpPeer) obj;
        return Objects.equals(this.interfaceName, that.interfaceName)
                && Objects.equals(this.ipAddress, that.ipAddress);
    }
}
