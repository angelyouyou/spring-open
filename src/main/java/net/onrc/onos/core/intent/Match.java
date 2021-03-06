package net.onrc.onos.core.intent;

import java.util.Objects;

import net.floodlightcontroller.util.MACAddress;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.match.Match.Builder;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;

/**
 * A class to represent the OpenFlow match.
 * <p>
 * At the moment, the supported match conditions are:
 * <ul>
 * <li>source port
 * <li>source and destination MAC address
 * <li>source and destination IP address
 * </ul>
 * <p>
 * TODO: extend this object to allow more match conditions
 */

public class Match {
    private static final short IPV4_PREFIX_LEN = 32;
    protected long sw;
    protected MACAddress srcMac;
    protected MACAddress dstMac;
    protected int srcIp;
    protected int dstIp;
    protected long srcPort;

    /**
     * Constructor for Ethernet-based matches.
     *
     * @param sw switch's DPID
     * @param srcPort source port on switch
     * @param srcMac source Ethernet MAC address
     * @param dstMac destination Ethernet MAC address
     */
    public Match(long sw, long srcPort,
            MACAddress srcMac, MACAddress dstMac) {
        this(sw, srcPort, srcMac, dstMac,
                ShortestPathIntent.EMPTYIPADDRESS,
                ShortestPathIntent.EMPTYIPADDRESS);
    }

    /**
     * Generic constructor.
     *
     * @param sw switch's DPID
     * @param srcPort source port on switch
     * @param srcMac source Ethernet MAC address
     * @param dstMac destination Ethernet MAC address
     * @param srcIp source IP address
     * @param dstIp destination IP address
     */
    public Match(long sw, long srcPort, MACAddress srcMac, MACAddress dstMac,
            int srcIp, int dstIp) {

        this.sw = sw;
        this.srcPort = srcPort;
        this.srcMac = srcMac;
        this.dstMac = dstMac;
        this.srcIp = srcIp;
        this.dstIp = dstIp;
    }

    /**
     * Matches are equal if all object variables are equal.
     *
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Match) {
            Match other = (Match) obj;
            // TODO: we might consider excluding sw from this comparison
            if (this.sw != other.sw) {
                return false;
            }
            if (!Objects.equals(srcMac, other.srcMac)) {
                return false;
            }
            if (!Objects.equals(dstMac, other.dstMac)) {
                return false;
            }
            if (srcIp != other.srcIp) {
                return false;
            }

            if (dstIp != other.dstIp) {
                return false;
            }
            if (this.srcPort != other.srcPort) {
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    public Builder getOFMatchBuilder(OFFactory factory) {
        Builder matchBuilder = factory.buildMatch();

        if (srcMac != null) {
            matchBuilder.setExact(MatchField.ETH_SRC, MacAddress.of(srcMac.toLong()));
        }
        if (dstMac != null) {
            matchBuilder.setExact(MatchField.ETH_DST, MacAddress.of(dstMac.toLong()));
        }
        if (srcIp != ShortestPathIntent.EMPTYIPADDRESS) {
            matchBuilder.setExact(MatchField.ETH_TYPE, EthType.IPv4)
            .setMasked(MatchField.IPV4_SRC,
                    IPv4Address.of(srcIp).withMaskOfLength(IPV4_PREFIX_LEN));
        }
        if (dstIp != ShortestPathIntent.EMPTYIPADDRESS) {
            matchBuilder.setExact(MatchField.ETH_TYPE, EthType.IPv4)
            .setMasked(MatchField.IPV4_DST,
                    IPv4Address.of(dstIp).withMaskOfLength(IPV4_PREFIX_LEN));
        }
        matchBuilder.setExact(MatchField.IN_PORT, OFPort.of((int) srcPort));

        return matchBuilder;
    }

    /**
     * Returns a String representation of this Match.
     *
     * @return the match as a String
     */
    @Override
    public String toString() {
        return "Sw:" + sw + " (" + srcPort + ","
                + srcMac + "," + dstMac + ","
                + srcIp + "," + dstIp + ")";
    }

    /**
     * Generates hash using Objects.hash() to hash all object variables.
     *
     * @return hashcode
     */
    @Override
    public int hashCode() {
        // TODO: we might consider excluding sw from the hash function
        // to make it easier to compare matches between switches
        return Objects.hash(sw, srcPort, srcMac, dstMac, srcIp, dstIp);
    }
}
