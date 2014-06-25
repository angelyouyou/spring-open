package net.onrc.onos.core.util;

import net.floodlightcontroller.util.MACAddress;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * The class representing the Flow Entry Matching filter.
 * <p/>
 * The Flow Entry matching filter that is used to specify
 * the network data that would be forwarded on the data path from
 * the source to the destination. Examples: source or destination MAC address,
 * IP prefix that includes the destination's IP address, etc.
 */
public class FlowEntryMatch {
    /**
     * A class for storing a value to match.
     */
    public static class Field<T> {
        /**
         * Default constructor.
         */
        public Field() {
            this.enabled = false;
        }

        /**
         * Constructor for a given value to match.
         *
         * @param value the value to match.
         */
        public Field(T value) {
            this.value = value;
            this.enabled = true;
        }

        /**
         * Get the value.
         *
         * @return the value.
         */
        public T value() {
            return this.value;
        }

        /**
         * Enable the matching for a given value.
         *
         * @param newValue the value to set.
         */
        public void enableMatch(T newValue) {
            this.value = newValue;
            this.enabled = true;
        }

        /**
         * Disable the matching.
         */
        public void disableMatch() {
            this.enabled = false;
        }

        /**
         * Test whether matching is enabled.
         *
         * @return true if matching is enabled, otherwise false.
         */
        public boolean enabled() {
            return this.enabled;
        }

        private T value;        // The value to match
        private boolean enabled;    // Set to true, if matching is enabled
    }

    private Field<PortNumber> inPort;        // Matching input switch port
    private Field<MACAddress> srcMac;    // Matching source MAC address
    private Field<MACAddress> dstMac;    // Matching destination MAC address
    private Field<Short> ethernetFrameType; // Matching Ethernet frame type
    private Field<Short> vlanId;    // Matching VLAN ID
    private Field<Byte> vlanPriority;    // Matching VLAN priority
    private Field<IPv4Net> srcIPv4Net;    // Matching source IPv4 prefix
    private Field<IPv4Net> dstIPv4Net;    // Matching destination IPv4 prefix
    private Field<Byte> ipProto;    // Matching IP protocol
    private Field<Byte> ipToS;        // Matching IP ToS (DSCP field, 6 bits)
    private Field<Short> srcTcpUdpPort;    // Matching source TCP/UDP port
    private Field<Short> dstTcpUdpPort;    // Matching destination TCP/UDP port

    /**
     * Default constructor.
     */
    public FlowEntryMatch() {
    }

    /**
     * Copy constructor.
     *
     * @param other the object to copy from.
     */
    public FlowEntryMatch(FlowEntryMatch other) {
        if ((other.inPort != null) && other.inPort.enabled()) {
            this.enableInPort(other.inPort.value());
        }
        if ((other.srcMac != null) && other.srcMac.enabled()) {
            this.enableSrcMac(other.srcMac.value());
        }
        if ((other.dstMac != null) && other.dstMac.enabled()) {
            this.enableDstMac(other.dstMac.value());
        }
        if ((other.ethernetFrameType != null) && other.ethernetFrameType.enabled()) {
            this.enableEthernetFrameType(other.ethernetFrameType.value());
        }
        if ((other.vlanId != null) && other.vlanId.enabled()) {
            this.enableVlanId(other.vlanId.value());
        }
        if ((other.vlanPriority != null) && other.vlanPriority.enabled()) {
            this.enableVlanPriority(other.vlanPriority.value());
        }
        if ((other.srcIPv4Net != null) && other.srcIPv4Net.enabled()) {
            this.enableSrcIPv4Net(other.srcIPv4Net.value());
        }
        if ((other.dstIPv4Net != null) && other.dstIPv4Net.enabled()) {
            this.enableDstIPv4Net(other.dstIPv4Net.value());
        }
        if ((other.ipProto != null) && other.ipProto.enabled()) {
            this.enableIpProto(other.ipProto.value());
        }
        if ((other.ipToS != null) && other.ipToS.enabled()) {
            this.enableIpToS(other.ipToS.value());
        }
        if ((other.srcTcpUdpPort != null) && other.srcTcpUdpPort.enabled()) {
            this.enableSrcTcpUdpPort(other.srcTcpUdpPort.value());
        }
        if ((other.dstTcpUdpPort != null) && other.dstTcpUdpPort.enabled()) {
            this.enableDstTcpUdpPort(other.dstTcpUdpPort.value());
        }
    }

    /**
     * Get the matching input switch port.
     *
     * @return the matching input switch port.
     */
    @JsonProperty("inPort")
    public PortNumber inPort() {
        if (inPort != null) {
            return inPort.value();
        }
        return null;
    }

    /**
     * Enable the matching on input switch port.
     *
     * @param newInPort the input switch port value to enable for matching.
     */
    @JsonProperty("inPort")
    public void enableInPort(PortNumber newInPort) {
        this.inPort = new Field<PortNumber>(newInPort);
    }

    /**
     * Disable the matching on input switch port.
     */
    public void disableInPort() {
        this.inPort = null;
    }

    /**
     * Test if matching on input switch port is enabled.
     *
     * @return true if matching on input switch port is enabled.
     */
    @JsonProperty("matchInPort")
    public boolean matchInPort() {
        if (inPort != null) {
            return inPort.enabled();
        }
        return false;
    }

    /**
     * Get the matching source MAC address.
     *
     * @return the matching source MAC address.
     */
    @JsonProperty("srcMac")
    public MACAddress srcMac() {
        if (srcMac != null) {
            return srcMac.value();
        }
        return null;
    }

    /**
     * Enable the matching on source MAC address.
     *
     * @param newSrcMac the source MAC address value to enable for matching.
     */
    @JsonProperty("srcMac")
    public void enableSrcMac(MACAddress newSrcMac) {
        this.srcMac = new Field<MACAddress>(newSrcMac);
    }

    /**
     * Disable the matching on source MAC address.
     */
    public void disableSrcMac() {
        this.srcMac = null;
    }

    /**
     * Test if matching on source MAC address is enabled.
     *
     * @return true if matching on source MAC address is enabled.
     */
    @JsonProperty("matchSrcMac")
    public boolean matchSrcMac() {
        if (srcMac != null) {
            return srcMac.enabled();
        }
        return false;
    }

    /**
     * Get the matching destination MAC address.
     *
     * @return the matching destination MAC address.
     */
    @JsonProperty("dstMac")
    public MACAddress dstMac() {
        if (dstMac != null) {
            return dstMac.value();
        }
        return null;
    }

    /**
     * Enable the matching on destination MAC address.
     *
     * @param newDstMac the destination MAC address value to enable for matching.
     */
    @JsonProperty("dstMac")
    public void enableDstMac(MACAddress newDstMac) {
        this.dstMac = new Field<MACAddress>(newDstMac);
    }

    /**
     * Disable the matching on destination MAC address.
     */
    public void disableDstMac() {
        this.dstMac = null;
    }

    /**
     * Test if matching on destination MAC address is enabled.
     *
     * @return true if matching on destination MAC address is enabled.
     */
    @JsonProperty("matchDstMac")
    public boolean matchDstMac() {
        if (dstMac != null) {
            return dstMac.enabled();
        }
        return false;
    }

    /**
     * Get the matching Ethernet frame type.
     *
     * @return the matching Ethernet frame type.
     */
    @JsonProperty("ethernetFrameType")
    public Short ethernetFrameType() {
        if (ethernetFrameType != null) {
            return ethernetFrameType.value();
        }
        return null;
    }

    /**
     * Enable the matching on Ethernet frame type.
     *
     * @param newEthernetFrameType the Ethernet frame type value to enable for
     *                          matching.
     */
    @JsonProperty("ethernetFrameType")
    public void enableEthernetFrameType(Short newEthernetFrameType) {
        this.ethernetFrameType = new Field<Short>(newEthernetFrameType);
    }

    /**
     * Disable the matching on Ethernet frame type.
     */
    public void disableEthernetFrameType() {
        this.ethernetFrameType = null;
    }

    /**
     * Test if matching on Ethernet frame type is enabled.
     *
     * @return true if matching on Ethernet frame type is enabled.
     */
    @JsonProperty("matchEthernetFrameType")
    public boolean matchEthernetFrameType() {
        if (ethernetFrameType != null) {
            return ethernetFrameType.enabled();
        }
        return false;
    }

    /**
     * Get the matching VLAN ID.
     *
     * @return the matching VLAN ID.
     */
    @JsonProperty("vlanId")
    public Short vlanId() {
        if (vlanId != null) {
            return vlanId.value();
        }
        return null;
    }

    /**
     * Enable the matching on VLAN ID.
     *
     * @param newVlanId the VLAN ID value to enable for matching.
     */
    @JsonProperty("vlanId")
    public void enableVlanId(Short newVlanId) {
        this.vlanId = new Field<Short>(newVlanId);
    }

    /**
     * Disable the matching on VLAN ID.
     */
    public void disableVlanId() {
        this.vlanId = null;
    }

    /**
     * Test if matching on VLAN ID is enabled.
     *
     * @return true if matching on VLAN ID is enabled.
     */
    @JsonProperty("matchVlanId")
    public boolean matchVlanId() {
        if (vlanId != null) {
            return vlanId.enabled();
        }
        return false;
    }

    /**
     * Get the matching VLAN priority.
     *
     * @return the matching VLAN priority.
     */
    @JsonProperty("vlanPriority")
    public Byte vlanPriority() {
        if (vlanPriority != null) {
            return vlanPriority.value();
        }
        return null;
    }

    /**
     * Enable the matching on VLAN priority.
     *
     * @param newVlanPriority the VLAN priority value to enable for matching.
     */
    @JsonProperty("vlanPriority")
    public void enableVlanPriority(Byte newVlanPriority) {
        this.vlanPriority = new Field<Byte>(newVlanPriority);
    }

    /**
     * Disable the matching on VLAN priority.
     */
    public void disableVlanPriority() {
        this.vlanPriority = null;
    }

    /**
     * Test if matching on VLAN priority is enabled.
     *
     * @return true if matching on VLAN priority is enabled.
     */
    @JsonProperty("matchVlanPriority")
    public boolean matchVlanPriority() {
        if (vlanPriority != null) {
            return vlanPriority.enabled();
        }
        return false;
    }

    /**
     * Get the matching source IPv4 prefix.
     *
     * @return the matching source IPv4 prefix.
     */
    @JsonProperty("srcIPv4Net")
    public IPv4Net srcIPv4Net() {
        if (srcIPv4Net != null) {
            return srcIPv4Net.value();
        }
        return null;
    }

    /**
     * Enable the matching on source IPv4 prefix.
     *
     * @param newSrcIPv4Net the source IPv4 prefix value to enable for matching.
     */
    @JsonProperty("srcIPv4Net")
    public void enableSrcIPv4Net(IPv4Net newSrcIPv4Net) {
        this.srcIPv4Net = new Field<IPv4Net>(newSrcIPv4Net);
    }

    /**
     * Disable the matching on source IPv4 prefix.
     */
    public void disableSrcIPv4Net() {
        this.srcIPv4Net = null;
    }

    /**
     * Test if matching on source IPv4 prefix is enabled.
     *
     * @return true if matching on source IPv4 prefix is enabled.
     */
    @JsonProperty("matchSrcIPv4Net")
    public boolean matchSrcIPv4Net() {
        if (srcIPv4Net != null) {
            return srcIPv4Net.enabled();
        }
        return false;
    }

    /**
     * Get the matching destination IPv4 prefix.
     *
     * @return the matching destination IPv4 prefix.
     */
    @JsonProperty("dstIPv4Net")
    public IPv4Net dstIPv4Net() {
        if (dstIPv4Net != null) {
            return dstIPv4Net.value();
        }
        return null;
    }

    /**
     * Enable the matching on destination IPv4 prefix.
     *
     * @param newDstIPv4Net the destination IPv4 prefix value to enable for
     *                   matching.
     */
    @JsonProperty("dstIPv4Net")
    public void enableDstIPv4Net(IPv4Net newDstIPv4Net) {
        this.dstIPv4Net = new Field<IPv4Net>(newDstIPv4Net);
    }

    /**
     * Disable the matching on destination IPv4 prefix.
     */
    public void disableDstIPv4Net() {
        this.dstIPv4Net = null;
    }

    /**
     * Test if matching on destination IPv4 prefix is enabled.
     *
     * @return true if matching on destination IPv4 prefix is enabled.
     */
    @JsonProperty("matchDstIPv4Net")
    public boolean matchDstIPv4Net() {
        if (dstIPv4Net != null) {
            return dstIPv4Net.enabled();
        }
        return false;
    }

    /**
     * Get the matching IP protocol.
     *
     * @return the matching IP protocol.
     */
    @JsonProperty("ipProto")
    public Byte ipProto() {
        if (ipProto != null) {
            return ipProto.value();
        }
        return null;
    }

    /**
     * Enable the matching on IP protocol.
     *
     * @param newIpProto the IP protocol value to enable for matching.
     */
    @JsonProperty("ipProto")
    public void enableIpProto(Byte newIpProto) {
        this.ipProto = new Field<Byte>(newIpProto);
    }

    /**
     * Disable the matching on IP protocol.
     */
    public void disableIpProto() {
        this.ipProto = null;
    }

    /**
     * Test if matching on IP protocol is enabled.
     *
     * @return true if matching on IP protocol is enabled.
     */
    @JsonProperty("matchIpProto")
    public boolean matchIpProto() {
        if (ipProto != null) {
            return ipProto.enabled();
        }
        return false;
    }

    /**
     * Get the matching IP ToS (DSCP field, 6 bits).
     *
     * @return the matching IP ToS.
     */
    @JsonProperty("ipToS")
    public Byte ipToS() {
        if (ipToS != null) {
            return ipToS.value();
        }
        return null;
    }

    /**
     * Enable the matching on IP ToS (DSCP field, 6 bits).
     *
     * @param newIpToS the IP ToS value to enable for matching.
     */
    @JsonProperty("ipToS")
    public void enableIpToS(Byte newIpToS) {
        this.ipToS = new Field<Byte>(newIpToS);
    }

    /**
     * Disable the matching on IP ToS (DSCP field, 6 bits).
     */
    public void disableIpToS() {
        this.ipToS = null;
    }

    /**
     * Test if matching on IP ToS (DSCP field, 6 bits) is enabled.
     *
     * @return true if matching on IP ToS is enabled.
     */
    @JsonProperty("matchIpToS")
    public boolean matchIpToS() {
        if (ipToS != null) {
            return ipToS.enabled();
        }
        return false;
    }

    /**
     * Get the matching source TCP/UDP port.
     *
     * @return the matching source TCP/UDP port.
     */
    @JsonProperty("srcTcpUdpPort")
    public Short srcTcpUdpPort() {
        if (srcTcpUdpPort != null) {
            return srcTcpUdpPort.value();
        }
        return null;
    }

    /**
     * Enable the matching on source TCP/UDP port.
     *
     * @param newSrcTcpUdpPort the source TCP/UDP port to enable for matching.
     */
    @JsonProperty("srcTcpUdpPort")
    public void enableSrcTcpUdpPort(Short newSrcTcpUdpPort) {
        this.srcTcpUdpPort = new Field<Short>(newSrcTcpUdpPort);
    }

    /**
     * Disable the matching on source TCP/UDP port.
     */
    public void disableSrcTcpUdpPort() {
        this.srcTcpUdpPort = null;
    }

    /**
     * Test if matching on source TCP/UDP port is enabled.
     *
     * @return true if matching on source TCP/UDP port is enabled.
     */
    @JsonProperty("matchSrcTcpUdpPort")
    public boolean matchSrcTcpUdpPort() {
        if (srcTcpUdpPort != null) {
            return srcTcpUdpPort.enabled();
        }
        return false;
    }

    /**
     * Get the matching destination TCP/UDP port.
     *
     * @return the matching destination TCP/UDP port.
     */
    @JsonProperty("dstTcpUdpPort")
    public Short dstTcpUdpPort() {
        if (dstTcpUdpPort != null) {
            return dstTcpUdpPort.value();
        }
        return null;
    }

    /**
     * Enable the matching on destination TCP/UDP port.
     *
     * @param newDstTcpUdpPort the destination TCP/UDP port to enable for
     *                         matching.
     */
    @JsonProperty("dstTcpUdpPort")
    public void enableDstTcpUdpPort(Short newDstTcpUdpPort) {
        this.dstTcpUdpPort = new Field<Short>(newDstTcpUdpPort);
    }

    /**
     * Disable the matching on destination TCP/UDP port.
     */
    public void disableDstTcpUdpPort() {
        this.dstTcpUdpPort = null;
    }

    /**
     * Test if matching on destination TCP/UDP port is enabled.
     *
     * @return true if matching on destination TCP/UDP port is enabled.
     */
    @JsonProperty("matchDstTcpUdpPort")
    public boolean matchDstTcpUdpPort() {
        if (dstTcpUdpPort != null) {
            return dstTcpUdpPort.enabled();
        }
        return false;
    }

    /**
     * Convert the matching filter to a string.
     * <p/>
     * The string has the following form:
     * [srcMac=XXX dstMac=XXX srcIPv4Net=XXX dstIPv4Net=XXX]
     *
     * @return the matching filter as a string.
     */
    @Override
    public String toString() {
        String ret = "[";
        boolean addSpace = false;

        //
        // Conditionally add only those matching fields that are enabled
        //
        if (matchInPort()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "inPort=" + this.inPort().toString();
        }
        if (matchSrcMac()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "srcMac=" + this.srcMac().toString();
        }
        if (matchDstMac()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "dstMac=" + this.dstMac().toString();
        }
        if (matchEthernetFrameType()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "ethernetFrameType=" + this.ethernetFrameType().toString();
        }
        if (matchVlanId()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "vlanId=" + this.vlanId().toString();
        }
        if (matchVlanPriority()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "vlanPriority=" + this.vlanPriority().toString();
        }
        if (matchSrcIPv4Net()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "srcIPv4Net=" + this.srcIPv4Net().toString();
        }
        if (matchDstIPv4Net()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "dstIPv4Net=" + this.dstIPv4Net().toString();
        }
        if (matchIpProto()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "ipProto=" + this.ipProto().toString();
        }
        if (matchIpToS()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "ipToS=" + this.ipToS().toString();
        }
        if (matchSrcTcpUdpPort()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "srcTcpUdpPort=" + this.srcTcpUdpPort().toString();
        }
        if (matchDstTcpUdpPort()) {
            if (addSpace) {
                ret += " ";
            }
            addSpace = true;
            ret += "dstTcpUdpPort=" + this.dstTcpUdpPort().toString();
        }

        ret += "]";

        return ret;
    }
}
