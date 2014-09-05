package net.onrc.onos.core.main.config;

import java.net.InetAddress;
import java.util.Set;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.util.MACAddress;
import net.onrc.onos.apps.sdnip.Interface;
import net.onrc.onos.core.util.SwitchPort;

/**
 * Provides information about the layer 3 properties of the network.
 * This is based on IP addresses configured on ports in the network.
 */
public interface IConfigInfoService extends IFloodlightService {
    public boolean isInterfaceAddress(InetAddress address);

    public boolean inConnectedNetwork(InetAddress address);

    public boolean fromExternalNetwork(long inDpid, short inPort);

    /**
     * Retrieves the {@link Interface} object for the interface that packets
     * to dstIpAddress will be sent out of. Returns null if dstIpAddress is not
     * in a directly connected network, or if no interfaces are configured.
     *
     * @param dstIpAddress Destination IP address that we want to match to
     *                     an outgoing interface
     * @return The {@link Interface} object if found, null if not
     */
    public Interface getOutgoingInterface(InetAddress dstIpAddress);

    /**
     * Returns whether this controller has a layer 3 configuration
     * (i.e. interfaces and IP addresses)
     *
     * @return True if IP addresses are configured, false if not
     */
    public boolean hasLayer3Configuration();

    public MACAddress getRouterMacAddress();

    /**
     * We currently have basic vlan support for the situation when the contr
     * is running within a single vlan. In this case, packets sent from the
     * controller (e.g. ARP) need to be tagged with that vlan.
     *
     * @return The vlan id configured in the config file,
     * or 0 if no vlan is configured.
     */
    public short getVlan();

    /**
     * Gets the external-facing switch ports in the network.
     * <p/>
     * We treat the switch ports (in SDN network) connected to other networks
     * as external networks switch ports.
     *
     * @return all the switch ports connected to external networks
     */
    public Set<SwitchPort> getExternalSwitchPorts();



}
