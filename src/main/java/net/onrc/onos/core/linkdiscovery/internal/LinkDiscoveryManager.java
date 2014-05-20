/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by David Erickson, Stanford University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.onrc.onos.core.linkdiscovery.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.OFSwitchImpl;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.EventHistory;
import net.floodlightcontroller.util.EventHistory.EvAction;
import net.onrc.onos.core.linkdiscovery.ILinkDiscovery;
import net.onrc.onos.core.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.onrc.onos.core.linkdiscovery.ILinkDiscovery.UpdateOperation;
import net.onrc.onos.core.linkdiscovery.ILinkDiscoveryListener;
import net.onrc.onos.core.linkdiscovery.ILinkDiscoveryService;
import net.onrc.onos.core.linkdiscovery.Link;
import net.onrc.onos.core.linkdiscovery.LinkInfo;
import net.onrc.onos.core.linkdiscovery.NodePortTuple;
import net.onrc.onos.core.linkdiscovery.web.LinkDiscoveryWebRoutable;
import net.onrc.onos.core.main.IOnosRemoteSwitch;
import net.onrc.onos.core.packet.BSN;
import net.onrc.onos.core.packet.Ethernet;
import net.onrc.onos.core.packet.IPv4;
import net.onrc.onos.core.packet.LLDP;
import net.onrc.onos.core.packet.LLDPTLV;
import net.onrc.onos.core.registry.IControllerRegistryService;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class sends out LLDP messages containing the sending switch's datapath
 * id as well as the outgoing port number.  Received LLrescDP messages that
 * match a known switch cause a new LinkTuple to be created according to the
 * invariant rules listed below.  This new LinkTuple is also passed to routing
 * if it exists to trigger updates.
 * <p/>
 * This class also handles removing links that are associated to switch ports
 * that go down, and switches that are disconnected.
 * <p/>
 * Invariants:
 * -portLinks and switchLinks will not contain empty Sets outside of
 * critical sections.
 * -portLinks contains LinkTuples where one of the src or dst
 * SwitchPortTuple matches the map key.
 * -switchLinks contains LinkTuples where one of the src or dst
 * SwitchPortTuple's id matches the switch id.
 * -Each LinkTuple will be indexed into switchLinks for both
 * src.id and dst.id, and portLinks for each src and dst.
 * -The updates queue is only added to from within a held write lock.
 */
@LogMessageCategory("Network Topology")
public class LinkDiscoveryManager
        implements IOFMessageListener, IOFSwitchListener,
        ILinkDiscoveryService, IFloodlightModule {
    protected IFloodlightProviderService controller;
    private static final Logger log = LoggerFactory.getLogger(LinkDiscoveryManager.class);

    protected IFloodlightProviderService floodlightProvider;
    protected IThreadPoolService threadPool;
    protected IRestApiService restApi;
    // Registry Service for ONOS
    protected IControllerRegistryService registryService;


    // LLDP and BDDP fields
    private static final byte[] LLDP_STANDARD_DST_MAC_STRING =
            HexString.fromHexString("01:80:c2:00:00:0e");
    private static final long LINK_LOCAL_MASK = 0xfffffffffff0L;
    private static final long LINK_LOCAL_VALUE = 0x0180c2000000L;

    // BigSwitch OUI is 5C:16:C7, so 5D:16:C7 is the multicast version
    // private static final String LLDP_BSN_DST_MAC_STRING = "5d:16:c7:00:00:01";
    private static final String LLDP_BSN_DST_MAC_STRING = "ff:ff:ff:ff:ff:ff";


    // Direction TLVs are used to indicate if the LLDPs were sent
    // periodically or in response to a recieved LLDP
    private static final byte TLV_DIRECTION_TYPE = 0x73;
    private static final short TLV_DIRECTION_LENGTH = 1;  // 1 byte
    private static final byte[] TLV_DIRECTION_VALUE_FORWARD = {0x01};
    private static final byte[] TLV_DIRECTION_VALUE_REVERSE = {0x02};
    private static final LLDPTLV FORWARD_TLV
            = new LLDPTLV().
            setType(TLV_DIRECTION_TYPE).
            setLength(TLV_DIRECTION_LENGTH).
            setValue(TLV_DIRECTION_VALUE_FORWARD);

    private static final LLDPTLV REVERSE_TLV
            = new LLDPTLV().
            setType(TLV_DIRECTION_TYPE).
            setLength(TLV_DIRECTION_LENGTH).
            setValue(TLV_DIRECTION_VALUE_REVERSE);

    // Link discovery task details.
    protected SingletonTask discoveryTask;
    protected static final int DISCOVERY_TASK_INTERVAL = 1;
    protected static final int LINK_TIMEOUT = 35; // original 35 secs, aggressive 5 secs
    protected static final int LLDP_TO_ALL_INTERVAL = 15; //original 15 seconds, aggressive 2 secs.
    protected long lldpClock = 0;
    // This value is intentionally kept higher than LLDP_TO_ALL_INTERVAL.
    // If we want to identify link failures faster, we could decrease this
    // value to a small number, say 1 or 2 sec.
    protected static final int LLDP_TO_KNOWN_INTERVAL = 20; // LLDP frequency for known links

    protected LLDPTLV controllerTLV;
    protected ReentrantReadWriteLock lock;
    int lldpTimeCount = 0;

    /**
     * Flag to indicate if automatic port fast is enabled or not.
     * Default is set to false -- Initialized in the init method as well.
     */
    boolean autoPortFastFeature = false;

    /**
     * Map of remote switches that are not connected to this controller. This
     * is used to learn remote switches in a distributed controller ONOS.
     */
    protected Map<Long, IOnosRemoteSwitch> remoteSwitches;

    /**
     * Map from link to the most recent time it was verified functioning.
     */
    protected Map<Link, LinkInfo> links;

    /**
     * Map from switch id to a set of all links with it as an endpoint.
     */
    protected Map<Long, Set<Link>> switchLinks;

    /**
     * Map from a id:port to the set of links containing it as an endpoint.
     */
    protected Map<NodePortTuple, Set<Link>> portLinks;

    /**
     * Set of link tuples over which multicast LLDPs are received
     * and unicast LLDPs are not received.
     */
    protected Map<NodePortTuple, Set<Link>> portBroadcastDomainLinks;

    protected volatile boolean shuttingDown = false;

    /**
     * Topology aware components are called in the order they were added to the
     * the array.
     */
    protected ArrayList<ILinkDiscoveryListener> linkDiscoveryAware;

    protected class LinkUpdate extends LDUpdate {

        public LinkUpdate(LDUpdate old) {
            super(old);
        }

        @LogMessageDoc(level = "ERROR",
                message = "Error in link discovery updates loop",
                explanation = "An unknown error occured while dispatching " +
                        "link update notifications",
                recommendation = LogMessageDoc.GENERIC_ACTION)
        @Override
        public void dispatch() {
            if (linkDiscoveryAware != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Dispatching link discovery update {} {} {} {} {} for {}",
                            new Object[]{this.getOperation(),
                                    HexString.toHexString(this.getSrc()), this.getSrcPort(),
                                    HexString.toHexString(this.getDst()), this.getDstPort(),
                                    linkDiscoveryAware});
                }
                try {
                    for (ILinkDiscoveryListener lda : linkDiscoveryAware) { // order maintained
                        lda.linkDiscoveryUpdate(this);
                    }
                } catch (Exception e) {
                    log.error("Error in link discovery updates loop", e);
                }
            }
        }
    }

    /**
     * List of ports through which LLDP/BDDPs are not sent.
     */
    protected Set<NodePortTuple> suppressLinkDiscovery;

    /**
     * A list of ports that are quarantined for discovering links through
     * them.  Data traffic from these ports are not allowed until the ports
     * are released from quarantine.
     */
    protected LinkedBlockingQueue<NodePortTuple> quarantineQueue;
    protected LinkedBlockingQueue<NodePortTuple> maintenanceQueue;
    /**
     * Quarantine task.
     */
    protected SingletonTask bddpTask;
    protected static final int BDDP_TASK_INTERVAL = 100; // 100 ms.
    protected static final int BDDP_TASK_SIZE = 5;       // # of ports per iteration

    /**
     * Map of broadcast domain ports and the last time a BDDP was either
     * sent or received on that port.
     */
    protected Map<NodePortTuple, Long> broadcastDomainPortTimeMap;

    /**
     * Get the LLDP sending period in seconds.
     *
     * @return LLDP sending period in seconds.
     */
    public int getLldpFrequency() {
        return LLDP_TO_KNOWN_INTERVAL;
    }

    /**
     * Get the LLDP timeout value in seconds.
     *
     * @return LLDP timeout value in seconds
     */
    public int getLldpTimeout() {
        return LINK_TIMEOUT;
    }

    public Map<NodePortTuple, Set<Link>> getPortLinks() {
        return portLinks;
    }

    @Override
    public Set<NodePortTuple> getSuppressLLDPsInfo() {
        return suppressLinkDiscovery;
    }

    /**
     * Add a switch port to the suppressed LLDP list.
     * Remove any known links on the switch port.
     */
    @Override
    public void addToSuppressLLDPs(long sw, short port) {
        NodePortTuple npt = new NodePortTuple(sw, port);
        this.suppressLinkDiscovery.add(npt);
        deleteLinksOnPort(npt, "LLDP suppressed.");
    }

    /**
     * Remove a switch port from the suppressed LLDP list.
     * Discover links on that switchport.
     */
    @Override
    public void removeFromSuppressLLDPs(long sw, short port) {
        NodePortTuple npt = new NodePortTuple(sw, port);
        this.suppressLinkDiscovery.remove(npt);
        discover(npt);
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public boolean isFastPort(long sw, short port) {
        return false;
    }

    @Override
    public ILinkDiscovery.LinkType getLinkType(Link lt, LinkInfo info) {
        if (info.getUnicastValidTime() != null) {
            return ILinkDiscovery.LinkType.DIRECT_LINK;
        } else if (info.getMulticastValidTime() != null) {
            return ILinkDiscovery.LinkType.MULTIHOP_LINK;
        }
        return ILinkDiscovery.LinkType.INVALID_LINK;
    }


    private boolean isLinkDiscoverySuppressed(long sw, short portNumber) {
        return this.suppressLinkDiscovery.contains(new NodePortTuple(sw, portNumber));
    }

    protected void discoverLinks() {

        // timeout known links.
        timeoutLinks();

        //increment LLDP clock
        lldpClock = (lldpClock + 1) % LLDP_TO_ALL_INTERVAL;

        if (lldpClock == 0) {
            log.debug("Sending LLDP out on all ports.");
            discoverOnAllPorts();
        }
    }


    /**
     * Quarantine Ports.
     */
    protected class QuarantineWorker implements Runnable {
        @Override
        public void run() {
            try {
                processBDDPLists();
            } catch (Exception e) {
                log.error("Error in quarantine worker thread", e);
            } finally {
                bddpTask.reschedule(BDDP_TASK_INTERVAL,
                        TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Add a switch port to the quarantine queue. Schedule the
     * quarantine task if the quarantine queue was empty before adding
     * this switch port.
     *
     * @param npt
     */
    protected void addToQuarantineQueue(NodePortTuple npt) {
        if (!quarantineQueue.contains(npt)) {
            quarantineQueue.add(npt);
        }
    }

    /**
     * Remove a switch port from the quarantine queue.
     */
    protected void removeFromQuarantineQueue(NodePortTuple npt) {
        // Remove all occurrences of the node port tuple from the list.
        boolean removedSomething;

        do {
            removedSomething = quarantineQueue.remove(npt);
        } while (removedSomething);
    }

    /**
     * Add a switch port to maintenance queue.
     *
     * @param npt
     */
    protected void addToMaintenanceQueue(NodePortTuple npt) {
        // TODO We are not checking if the switch port tuple is already
        // in the maintenance list or not.  This will be an issue for
        // really large number of switch ports in the network.
        if (!maintenanceQueue.contains(npt)) {
            maintenanceQueue.add(npt);
        }
    }

    /**
     * Remove a switch port from maintenance queue.
     *
     * @param npt
     */
    protected void removeFromMaintenanceQueue(NodePortTuple npt) {
        // Remove all occurrences of the node port tuple from the queue.
        boolean removedSomething;
        do {
            removedSomething = maintenanceQueue.remove(npt);
        } while (removedSomething);
    }

    /**
     * This method processes the quarantine list in bursts.  The task is
     * at most once per BDDP_TASK_INTERVAL.
     * One each call, BDDP_TASK_SIZE number of switch ports are processed.
     * Once the BDDP packets are sent out through the switch ports, the ports
     * are removed from the quarantine list.
     */

    protected void processBDDPLists() {
        int count = 0;
        Set<NodePortTuple> nptList = new HashSet<NodePortTuple>();

        while (count < BDDP_TASK_SIZE && quarantineQueue.peek() != null) {
            NodePortTuple npt;
            npt = quarantineQueue.remove();
            sendDiscoveryMessage(npt.getNodeId(), npt.getPortId(), false, false);
            nptList.add(npt);
            count++;
        }

        count = 0;
        while (count < BDDP_TASK_SIZE && maintenanceQueue.peek() != null) {
            NodePortTuple npt;
            npt = maintenanceQueue.remove();
            sendDiscoveryMessage(npt.getNodeId(), npt.getPortId(), false, false);
            count++;
        }

        for (NodePortTuple npt : nptList) {
            generateSwitchPortStatusUpdate(npt.getNodeId(), npt.getPortId());
        }
    }

    @Override
    public Set<Short> getQuarantinedPorts(long sw) {
        Set<Short> qPorts = new HashSet<Short>();

        Iterator<NodePortTuple> iter = quarantineQueue.iterator();
        while (iter.hasNext()) {
            NodePortTuple npt = iter.next();
            if (npt.getNodeId() == sw) {
                qPorts.add(npt.getPortId());
            }
        }
        return qPorts;
    }

    private void generateSwitchPortStatusUpdate(long sw, short port) {
        UpdateOperation operation;

        IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
        if (iofSwitch == null) {
            return;
        }

        OFPhysicalPort ofp = iofSwitch.getPort(port);
        if (ofp == null) {
            return;
        }

        int srcPortState = ofp.getState();
        boolean portUp = ((srcPortState &
                OFPortState.OFPPS_STP_MASK.getValue()) !=
                OFPortState.OFPPS_STP_BLOCK.getValue());

        if (portUp) {
            operation = UpdateOperation.PORT_UP;
        } else {
            operation = UpdateOperation.PORT_DOWN;
        }

        LinkUpdate update = new LinkUpdate(new LDUpdate(sw, port, operation));


        controller.publishUpdate(update);
    }

    /**
     * Send LLDP on known ports.
     */
    protected void discoverOnKnownLinkPorts() {
        // Copy the port set.
        Set<NodePortTuple> nptSet = new HashSet<NodePortTuple>();
        nptSet.addAll(portLinks.keySet());

        // Send LLDP from each of them.
        for (NodePortTuple npt : nptSet) {
            discover(npt);
        }
    }

    protected void discover(NodePortTuple npt) {
        discover(npt.getNodeId(), npt.getPortId());
    }

    protected void discover(long sw, short port) {
        sendDiscoveryMessage(sw, port, true, false);
    }

    /**
     * Learn remote switches when running as a distributed controller ONOS.
     */
    protected IOFSwitch addRemoteSwitch(long sw, short port) {
        IOnosRemoteSwitch remotesw = null;

        // add a switch if we have not seen it before
        remotesw = remoteSwitches.get(sw);

        if (remotesw == null) {
            remotesw = new OFSwitchImpl();
            remotesw.setupRemoteSwitch(sw);
            remoteSwitches.put(remotesw.getId(), remotesw);
            log.debug("addRemoteSwitch(): added fake remote sw {}", remotesw);
        }

        // add the port if we have not seen it before
        if (remotesw.getPort(port) == null) {
            OFPhysicalPort remoteport = new OFPhysicalPort();
            remoteport.setPortNumber(port);
            remoteport.setName("fake_" + port);
            remoteport.setConfig(0);
            remoteport.setState(0);
            remotesw.setPort(remoteport);
            log.debug("addRemoteSwitch(): added fake remote port {} to sw {}", remoteport, remotesw.getId());
        }

        return remotesw;
    }

    /**
     * Send link discovery message out of a given switch port.
     * The discovery message may be a standard LLDP or a modified
     * LLDP, where the dst mac address is set to :ff.
     * <p/>
     * TODO: The modified LLDP will updated in the future and may
     * use a different eth-type.
     *
     * @param sw
     * @param port
     * @param isStandard indicates standard or modified LLDP
     * @param isReverse  indicates whether the LLDP was sent as a response
     */
    @LogMessageDoc(level = "ERROR",
            message = "Failure sending LLDP out port {port} on switch {switch}",
            explanation = "An I/O error occured while sending LLDP message " +
                    "to the switch.",
            recommendation = LogMessageDoc.CHECK_SWITCH)
    protected void sendDiscoveryMessage(long sw, short port,
                                        boolean isStandard,
                                        boolean isReverse) {

        IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
        if (iofSwitch == null) {
            return;
        }

        if (port == OFPort.OFPP_LOCAL.getValue()) {
            return;
        }

        OFPhysicalPort ofpPort = iofSwitch.getPort(port);

        if (ofpPort == null) {
            if (log.isTraceEnabled()) {
                log.trace("Null physical port. sw={}, port={}", sw, port);
            }
            return;
        }

        if (isLinkDiscoverySuppressed(sw, port)) {
            /* Dont send LLDPs out of this port as suppressLLDPs set
             *
             */
            return;
        }

        // For fast ports, do not send forward LLDPs or BDDPs.
        if (!isReverse && autoPortFastFeature && isFastPort(sw, port)) {
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP packet out of swich: {}, port: {}",
                    sw, port);
        }

        // using "nearest customer bridge" MAC address for broadest possible propagation
        // through provider and TPMR bridges (see IEEE 802.1AB-2009 and 802.1Q-2011),
        // in particular the Linux bridge which behaves mostly like a provider bridge
        byte[] chassisId = new byte[]{4, 0, 0, 0, 0, 0, 0}; // filled in later
        byte[] portId = new byte[]{2, 0, 0}; // filled in later
        byte[] ttlValue = new byte[]{0, 0x78};
        // OpenFlow OUI - 00-26-E1
        byte[] dpidTLVValue = new byte[]{0x0, 0x26, (byte) 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        byte[] dpidArray = new byte[8];
        ByteBuffer dpidBB = ByteBuffer.wrap(dpidArray);
        ByteBuffer portBB = ByteBuffer.wrap(portId, 1, 2);

        Long dpid = sw;
        dpidBB.putLong(dpid);
        // set the ethernet source mac to last 6 bytes of dpid
        byte[] hardwareAddress = new byte[6];
        System.arraycopy(dpidArray, 2, hardwareAddress, 0, 6);
        ofpPort.setHardwareAddress(hardwareAddress);
        // set the chassis id's value to last 6 bytes of dpid
        System.arraycopy(dpidArray, 2, chassisId, 1, 6);
        // set the optional tlv to the full dpid
        System.arraycopy(dpidArray, 0, dpidTLVValue, 4, 8);
        LLDPTLV dpidTLV = new LLDPTLV().setType((byte) 127)
                .setLength((short) dpidTLVValue.length).setValue(dpidTLVValue);

        // set the portId to the outgoing port
        portBB.putShort(port);
        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP out of interface: {}/{}",
                    HexString.toHexString(sw), port);
        }

        LLDP lldp = new LLDP();
        lldp.setChassisId(new LLDPTLV().setType((byte) 1).setLength((short) chassisId.length).setValue(chassisId));
        lldp.setPortId(new LLDPTLV().setType((byte) 2).setLength((short) portId.length).setValue(portId));
        lldp.setTtl(new LLDPTLV().setType((byte) 3).setLength((short) ttlValue.length).setValue(ttlValue));
        lldp.getOptionalTLVList().add(dpidTLV);

        // Add the controller identifier to the TLV value.
        lldp.getOptionalTLVList().add(controllerTLV);
        if (isReverse) {
            lldp.getOptionalTLVList().add(REVERSE_TLV);
        } else {
            lldp.getOptionalTLVList().add(FORWARD_TLV);
        }

        Ethernet ethernet;
        if (isStandard) {
            ethernet = new Ethernet()
                    .setSourceMACAddress(ofpPort.getHardwareAddress())
                    .setDestinationMACAddress(LLDP_STANDARD_DST_MAC_STRING)
                    .setEtherType(Ethernet.TYPE_LLDP);
            ethernet.setPayload(lldp);
        } else {
            BSN bsn = new BSN(BSN.BSN_TYPE_BDDP);
            bsn.setPayload(lldp);

            ethernet = new Ethernet()
                    .setSourceMACAddress(ofpPort.getHardwareAddress())
                    .setDestinationMACAddress(LLDP_BSN_DST_MAC_STRING)
                    .setEtherType(Ethernet.TYPE_BSN);
            ethernet.setPayload(bsn);
        }


        // serialize and wrap in a packet out
        byte[] data = ethernet.serialize();
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(OFPort.OFPP_NONE);

        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(port, (short) 0));
        po.setActions(actions);
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // set data
        po.setLengthU(OFPacketOut.MINIMUM_LENGTH + po.getActionsLength() + data.length);
        po.setPacketData(data);

        // send
        try {
            iofSwitch.write(po, null);
            iofSwitch.flush();
        } catch (IOException e) {
            log.error("Failure sending LLDP out port " + port + " on switch " + iofSwitch.getStringId(), e);
        }

    }

    /**
     * Send LLDPs to all switch-ports.
     */
    protected void discoverOnAllPorts() {
        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP packets out of all the enabled ports on switch");
        }
        Set<Long> switches = floodlightProvider.getSwitches().keySet();
        // Send standard LLDPs
        for (long sw : switches) {
            IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
            if (iofSwitch == null) {
                continue;
            }
            if (iofSwitch.getEnabledPorts() != null) {
                for (OFPhysicalPort ofp : iofSwitch.getEnabledPorts()) {
                    if (isLinkDiscoverySuppressed(sw, ofp.getPortNumber())) {
                        continue;
                    }
                    if (autoPortFastFeature && isFastPort(sw, ofp.getPortNumber())) {
                        continue;
                    }

                    // sends forward LLDP only non-fastports.
                    sendDiscoveryMessage(sw, ofp.getPortNumber(), true, false);

                    // If the switch port is not alreayd in the maintenance
                    // queue, add it.
                    NodePortTuple npt = new NodePortTuple(sw, ofp.getPortNumber());
                    addToMaintenanceQueue(npt);
                }
            }
        }
    }

    protected void setControllerTLV() {
        //Setting the controllerTLVValue based on current nano time,
        //controller's IP address, and the network interface object hash
        //the corresponding IP address.

        final int prime = 7867;
        InetAddress localIPAddress = null;
        NetworkInterface localInterface = null;

        byte[] controllerTLVValue = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};  // 8 byte value.
        ByteBuffer bb = ByteBuffer.allocate(10);

        try {
            localIPAddress = java.net.InetAddress.getLocalHost();
            localInterface = NetworkInterface.getByInetAddress(localIPAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }

        long result = System.nanoTime();
        if (localIPAddress != null) {
            result = result * prime + IPv4.toIPv4Address(localIPAddress.getHostAddress());
        }
        if (localInterface != null) {
            result = result * prime + localInterface.hashCode();
        }
        // set the first 4 bits to 0.
        result = result & (0x0fffffffffffffffL);

        bb.putLong(result);

        bb.rewind();
        bb.get(controllerTLVValue, 0, 8);

        this.controllerTLV = new LLDPTLV().setType((byte) 0x0c).setLength((short) controllerTLVValue.length).setValue(controllerTLVValue);
    }

    @Override
    public String getName() {
        return "linkdiscovery";
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                if (msg instanceof OFPacketIn) {
                    return this.handlePacketIn(sw.getId(), (OFPacketIn) msg,
                                               cntx);
                }
                break;
            case PORT_STATUS:
                if (msg instanceof OFPortStatus) {
                    return this.handlePortStatus(sw.getId(), (OFPortStatus) msg);
                }
                break;
            default:
                break;
        }
        return Command.CONTINUE;
    }

    private Command handleLldp(LLDP lldp, long sw, OFPacketIn pi, boolean isStandard, FloodlightContext cntx) {
        // If LLDP is suppressed on this port, ignore received packet as well
        IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
        if (iofSwitch == null) {
            return Command.STOP;
        }

        if (isLinkDiscoverySuppressed(sw, pi.getInPort())) {
            return Command.STOP;
        }

        // If this is a malformed LLDP, or not from us, exit
        if (lldp.getPortId() == null || lldp.getPortId().getLength() != 3) {
            return Command.CONTINUE;
        }

        long myId = ByteBuffer.wrap(controllerTLV.getValue()).getLong();
        long otherId = 0;
        boolean myLLDP = false;
        Boolean isReverse = null;

        ByteBuffer portBB = ByteBuffer.wrap(lldp.getPortId().getValue());
        portBB.position(1);

        Short remotePort = portBB.getShort();
        IOFSwitch remoteSwitch = null;

        // Verify this LLDP packet matches what we're looking for
        for (LLDPTLV lldptlv : lldp.getOptionalTLVList()) {
            if (lldptlv.getType() == 127 && lldptlv.getLength() == 12 &&
                    lldptlv.getValue()[0] == 0x0 && lldptlv.getValue()[1] == 0x26 &&
                    lldptlv.getValue()[2] == (byte) 0xe1 && lldptlv.getValue()[3] == 0x0) {
                ByteBuffer dpidBB = ByteBuffer.wrap(lldptlv.getValue());
                remoteSwitch = floodlightProvider.getSwitches().get(dpidBB.getLong(4));
                if (remoteSwitch == null) {
                    // Added by ONOS
                    // floodlight LLDP coming from a remote switch connected to a different controller
                    // add it to our cache of unconnected remote switches
                    remoteSwitch = addRemoteSwitch(dpidBB.getLong(4), remotePort);
                }
            } else if (lldptlv.getType() == 12 && lldptlv.getLength() == 8) {
                otherId = ByteBuffer.wrap(lldptlv.getValue()).getLong();
                if (myId == otherId) {
                    myLLDP = true;
                }
            } else if (lldptlv.getType() == TLV_DIRECTION_TYPE &&
                    lldptlv.getLength() == TLV_DIRECTION_LENGTH) {
                if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_FORWARD[0]) {
                    isReverse = false;
                } else if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_REVERSE[0]) {
                    isReverse = true;
                }
            }
        }

        if (!myLLDP) {
            // This is not the LLDP sent by this controller.
            // If the LLDP message has multicast bit set, then we need to broadcast
            // the packet as a regular packet.
            if (isStandard) {
                if (log.isTraceEnabled()) {
                    log.trace("Getting standard LLDP from a different controller and quelching it.");
                }
                return Command.STOP;
            } else if (sw <= remoteSwitch.getId()) {
                if (log.isTraceEnabled()) {
                    log.trace("Getting BBDP from a different controller. myId {}: remoteId {}", myId, otherId);
                    log.trace("and my controller id is smaller than the other, so quelching it. myPort {}: rPort {}", pi.getInPort(), remotePort);
                }
                //XXX ONOS: Fix the BDDP broadcast issue
                //return Command.CONTINUE;
                return Command.STOP;
            }
            /*
            else if (myId < otherId)  {
                if (log.isTraceEnabled()) {
                    log.trace("Getting BDDP packets from a different controller" +
                            "and letting it go through normal processing chain.");
                }
                //XXX ONOS: Fix the BDDP broadcast issue
                //return Command.CONTINUE;
                return Command.STOP;
            }
            */
        }


        if (remoteSwitch == null) {
            // Ignore LLDPs not generated by Floodlight, or from a switch that has recently
            // disconnected, or from a switch connected to another Floodlight instance
            if (log.isTraceEnabled()) {
                log.trace("Received LLDP from remote switch not connected to the controller");
            }
            return Command.STOP;
        }

        if (!remoteSwitch.portEnabled(remotePort)) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring link with disabled source port: switch {} port {}", remoteSwitch, remotePort);
            }
            return Command.STOP;
        }
        if (suppressLinkDiscovery.contains(new NodePortTuple(remoteSwitch.getId(),
                remotePort))) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring link with suppressed src port: switch {} port {}",
                        remoteSwitch, remotePort);
            }
            return Command.STOP;
        }
        if (!iofSwitch.portEnabled(pi.getInPort())) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring link with disabled dest port: switch {} port {}", sw, pi.getInPort());
            }
            return Command.STOP;
        }

        OFPhysicalPort physicalPort = remoteSwitch.getPort(remotePort);
        int srcPortState = (physicalPort != null) ? physicalPort.getState() : 0;
        physicalPort = iofSwitch.getPort(pi.getInPort());
        int dstPortState = (physicalPort != null) ? physicalPort.getState() : 0;

        // Store the time of update to this link, and push it out to routingEngine
        Link lt = new Link(remoteSwitch.getId(), remotePort, iofSwitch.getId(), pi.getInPort());


        Long lastLldpTime = null;
        Long lastBddpTime = null;

        Long firstSeenTime = System.currentTimeMillis();

        if (isStandard) {
            lastLldpTime = System.currentTimeMillis();
        } else {
            lastBddpTime = System.currentTimeMillis();
        }

        LinkInfo newLinkInfo =
                new LinkInfo(firstSeenTime, lastLldpTime, lastBddpTime,
                        srcPortState, dstPortState);

        addOrUpdateLink(lt, newLinkInfo);

        // Check if reverse link exists.
        // If it doesn't exist and if the forward link was seen
        // first seen within a small interval, send probe on the
        // reverse link.

        newLinkInfo = links.get(lt);
        if (newLinkInfo != null && isStandard && !isReverse) {
            Link reverseLink = new Link(lt.getDst(), lt.getDstPort(),
                    lt.getSrc(), lt.getSrcPort());
            LinkInfo reverseInfo = links.get(reverseLink);
            if (reverseInfo == null) {
                // the reverse link does not exist.
                if (newLinkInfo.getFirstSeenTime() > System.currentTimeMillis() - LINK_TIMEOUT) {
                    this.sendDiscoveryMessage(lt.getDst(), lt.getDstPort(), isStandard, true);
                }
            }
        }

        // If the received packet is a BDDP packet, then create a reverse BDDP
        // link as well.
        if (!isStandard) {
            Link reverseLink = new Link(lt.getDst(), lt.getDstPort(),
                    lt.getSrc(), lt.getSrcPort());

            // srcPortState and dstPort state are reversed.
            LinkInfo reverseInfo =
                    new LinkInfo(firstSeenTime, lastLldpTime, lastBddpTime,
                            dstPortState, srcPortState);

            addOrUpdateLink(reverseLink, reverseInfo);
        }

        // Remove the node ports from the quarantine and maintenance queues.
        NodePortTuple nptSrc = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
        NodePortTuple nptDst = new NodePortTuple(lt.getDst(), lt.getDstPort());
        removeFromQuarantineQueue(nptSrc);
        removeFromMaintenanceQueue(nptSrc);
        removeFromQuarantineQueue(nptDst);
        removeFromMaintenanceQueue(nptDst);

        // Consume this message
        return Command.STOP;
    }

    protected Command handlePacketIn(long sw, OFPacketIn pi,
                                     FloodlightContext cntx) {
        Ethernet eth =
                IFloodlightProviderService.bcStore.get(cntx,
                        IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        if (eth.getEtherType() == Ethernet.TYPE_BSN) {
            BSN bsn = (BSN) eth.getPayload();
            if (bsn == null) {
                return Command.STOP;
            }
            if (bsn.getPayload() == null) {
                return Command.STOP;
            }
            // It could be a packet other than BSN LLDP, therefore
            // continue with the regular processing.
            if (!(bsn.getPayload() instanceof LLDP)) {
                return Command.CONTINUE;
            }
            return handleLldp((LLDP) bsn.getPayload(), sw, pi, false, cntx);
        } else if (eth.getEtherType() == Ethernet.TYPE_LLDP) {
            return handleLldp((LLDP) eth.getPayload(), sw, pi, true, cntx);
        } else if (eth.getEtherType() < 1500) {
            long destMac = eth.getDestinationMAC().toLong();
            if ((destMac & LINK_LOCAL_MASK) == LINK_LOCAL_VALUE) {
                if (log.isTraceEnabled()) {
                    log.trace("Ignoring packet addressed to 802.1D/Q " +
                            "reserved address.");
                }
                return Command.STOP;
            }
        }

        // If packet-in is from a quarantine port, stop processing.
        NodePortTuple npt = new NodePortTuple(sw, pi.getInPort());
        if (quarantineQueue.contains(npt)) {
            return Command.STOP;
        }

        return Command.CONTINUE;
    }

    protected UpdateOperation getUpdateOperation(int srcPortState,
                                                 int dstPortState) {
        boolean added =
                (((srcPortState &
                        OFPortState.OFPPS_STP_MASK.getValue()) !=
                        OFPortState.OFPPS_STP_BLOCK.getValue()) &&
                        ((dstPortState &
                                OFPortState.OFPPS_STP_MASK.getValue()) !=
                                OFPortState.OFPPS_STP_BLOCK.getValue()));

        if (added) {
            return UpdateOperation.LINK_UPDATED;
        }
        return UpdateOperation.LINK_REMOVED;
    }


    protected UpdateOperation getUpdateOperation(int srcPortState) {
        boolean portUp = ((srcPortState &
                OFPortState.OFPPS_STP_MASK.getValue()) !=
                OFPortState.OFPPS_STP_BLOCK.getValue());

        if (portUp) {
            return UpdateOperation.PORT_UP;
        } else {
            return UpdateOperation.PORT_DOWN;
        }
    }

    protected boolean addOrUpdateLink(Link lt, LinkInfo newInfo) {

        NodePortTuple srcNpt, dstNpt;
        boolean linkChanged = false;

        lock.writeLock().lock();
        try {
            // put the new info.  if an old info exists, it will be returned.
            LinkInfo oldInfo = links.put(lt, newInfo);
            if (oldInfo != null &&
                    oldInfo.getFirstSeenTime() < newInfo.getFirstSeenTime()) {
                newInfo.setFirstSeenTime(oldInfo.getFirstSeenTime());
            }

            if (log.isTraceEnabled()) {
                log.trace("addOrUpdateLink: {} {}",
                        lt,
                        (newInfo.getMulticastValidTime() != null) ? "multicast" : "unicast");
            }

            UpdateOperation updateOperation = null;
            linkChanged = false;

            srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
            dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

            if (oldInfo == null) {
                // index it by switch source
                if (!switchLinks.containsKey(lt.getSrc())) {
                    switchLinks.put(lt.getSrc(), new HashSet<Link>());
                }
                switchLinks.get(lt.getSrc()).add(lt);

                // index it by switch dest
                if (!switchLinks.containsKey(lt.getDst())) {
                    switchLinks.put(lt.getDst(), new HashSet<Link>());
                }
                switchLinks.get(lt.getDst()).add(lt);

                // index both ends by switch:port
                if (!portLinks.containsKey(srcNpt)) {
                    portLinks.put(srcNpt, new HashSet<Link>());
                }
                portLinks.get(srcNpt).add(lt);

                if (!portLinks.containsKey(dstNpt)) {
                    portLinks.put(dstNpt, new HashSet<Link>());
                }
                portLinks.get(dstNpt).add(lt);

                // Add to portNOFLinks if the unicast valid time is null
                if (newInfo.getUnicastValidTime() == null) {
                    addLinkToBroadcastDomain(lt);
                }

                // ONOS: Distinguish added event separately from updated event
                updateOperation = UpdateOperation.LINK_ADDED;
                linkChanged = true;

                // Add to event history
                evHistTopoLink(lt.getSrc(),
                        lt.getDst(),
                        lt.getSrcPort(),
                        lt.getDstPort(),
                        newInfo.getSrcPortState(), newInfo.getDstPortState(),
                        getLinkType(lt, newInfo),
                        EvAction.LINK_ADDED, "LLDP Recvd");
            } else {
                // Since the link info is already there, we need to
                // update the right fields.
                if (newInfo.getUnicastValidTime() == null) {
                    // This is due to a multicast LLDP, so copy the old unicast
                    // value.
                    if (oldInfo.getUnicastValidTime() != null) {
                        newInfo.setUnicastValidTime(oldInfo.getUnicastValidTime());
                    }
                } else if (newInfo.getMulticastValidTime() == null) {
                    // This is due to a unicast LLDP, so copy the old multicast
                    // value.
                    if (oldInfo.getMulticastValidTime() != null) {
                        newInfo.setMulticastValidTime(oldInfo.getMulticastValidTime());
                    }
                }

                Long oldTime = oldInfo.getUnicastValidTime();
                Long newTime = newInfo.getUnicastValidTime();
                // the link has changed its state between openflow and non-openflow
                // if the unicastValidTimes are null or not null
                if (oldTime != null & newTime == null) {
                    // openflow -> non-openflow transition
                    // we need to add the link tuple to the portNOFLinks
                    addLinkToBroadcastDomain(lt);
                    linkChanged = true;
                } else if (oldTime == null & newTime != null) {
                    // non-openflow -> openflow transition
                    // we need to remove the link from the portNOFLinks
                    removeLinkFromBroadcastDomain(lt);
                    linkChanged = true;
                }

                // Only update the port states if they've changed
                if (newInfo.getSrcPortState().intValue() !=
                        oldInfo.getSrcPortState().intValue() ||
                        newInfo.getDstPortState().intValue() !=
                                oldInfo.getDstPortState().intValue()) {
                    linkChanged = true;
                }

                if (linkChanged) {
                    updateOperation = getUpdateOperation(newInfo.getSrcPortState(),
                            newInfo.getDstPortState());
                    if (log.isTraceEnabled()) {
                        log.trace("Updated link {}", lt);
                    }
                    // Add to event history
                    evHistTopoLink(lt.getSrc(),
                            lt.getDst(),
                            lt.getSrcPort(),
                            lt.getDstPort(),
                            newInfo.getSrcPortState(), newInfo.getDstPortState(),
                            getLinkType(lt, newInfo),
                            EvAction.LINK_PORT_STATE_UPDATED,
                            "LLDP Recvd");
                }
            }

            if (linkChanged) {
                // find out if the link was added or removed here.
                LinkUpdate update = new LinkUpdate(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                        lt.getDst(), lt.getDstPort(),
                        getLinkType(lt, newInfo),
                        updateOperation));
                controller.publishUpdate(update);
            }
        } finally {
            lock.writeLock().unlock();
        }

        return linkChanged;
    }

    @Override
    public Map<Long, Set<Link>> getSwitchLinks() {
        return this.switchLinks;
    }

    /**
     * Removes links from memory and storage.
     *
     * @param linksToDelete The List of @LinkTuple to delete.
     */
    protected void deleteLinks(List<Link> linksToDelete, String reason) {
        NodePortTuple srcNpt, dstNpt;

        lock.writeLock().lock();
        try {
            for (Link lt : linksToDelete) {
                srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
                dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

                switchLinks.get(lt.getSrc()).remove(lt);
                switchLinks.get(lt.getDst()).remove(lt);
                if (switchLinks.containsKey(lt.getSrc()) &&
                        switchLinks.get(lt.getSrc()).isEmpty()) {
                    this.switchLinks.remove(lt.getSrc());
                }
                if (this.switchLinks.containsKey(lt.getDst()) &&
                        this.switchLinks.get(lt.getDst()).isEmpty()) {
                    this.switchLinks.remove(lt.getDst());
                }

                if (this.portLinks.get(srcNpt) != null) {
                    this.portLinks.get(srcNpt).remove(lt);
                    if (this.portLinks.get(srcNpt).isEmpty()) {
                        this.portLinks.remove(srcNpt);
                    }
                }
                if (this.portLinks.get(dstNpt) != null) {
                    this.portLinks.get(dstNpt).remove(lt);
                    if (this.portLinks.get(dstNpt).isEmpty()) {
                        this.portLinks.remove(dstNpt);
                    }
                }

                LinkInfo info = this.links.remove(lt);
                LinkUpdate update = new LinkUpdate(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                        lt.getDst(), lt.getDstPort(),
                        getLinkType(lt, info),
                        UpdateOperation.LINK_REMOVED));
                controller.publishUpdate(update);

                // Update Event History
                evHistTopoLink(lt.getSrc(),
                        lt.getDst(),
                        lt.getSrcPort(),
                        lt.getDstPort(),
                        0, 0, // Port states
                        ILinkDiscovery.LinkType.INVALID_LINK,
                        EvAction.LINK_DELETED, reason);

                // TODO  Whenever link is removed, it has to checked if
                // the switchports must be added to quarantine.

                if (log.isTraceEnabled()) {
                    log.trace("Deleted link {}", lt);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Handles an OFPortStatus message from a switch. We will add or
     * delete LinkTupes as well re-compute the topology if needed.
     *
     * @param sw The IOFSwitch that sent the port status message
     * @param ps The OFPortStatus message
     * @return The Command to continue or stop after we process this message
     */
    protected Command handlePortStatus(long sw, OFPortStatus ps) {

        IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
        if (iofSwitch == null) {
            return Command.CONTINUE;
        }

        // ONOS: If we do not control this switch, then we should not process its port status messages
        if (!registryService.hasControl(iofSwitch.getId())) {
            return Command.CONTINUE;
        }

        if (log.isTraceEnabled()) {
            log.trace("handlePortStatus: Switch {} port #{} reason {}; " +
                    "config is {} state is {}",
                    new Object[]{iofSwitch.getStringId(),
                            ps.getDesc().getPortNumber(),
                            ps.getReason(),
                            ps.getDesc().getConfig(),
                            ps.getDesc().getState()});
        }

        short port = ps.getDesc().getPortNumber();
        NodePortTuple npt = new NodePortTuple(sw, port);
        boolean linkDeleted = false;
        boolean linkInfoChanged = false;

        lock.writeLock().lock();
        try {
            // if ps is a delete, or a modify where the port is down or
            // configured down
            if ((byte) OFPortReason.OFPPR_DELETE.ordinal() == ps.getReason() ||
                    ((byte) OFPortReason.OFPPR_MODIFY.ordinal() ==
                            ps.getReason() && !portEnabled(ps.getDesc()))) {
                deleteLinksOnPort(npt, "Port Status Changed");
                LinkUpdate update = new LinkUpdate(new LDUpdate(sw, port, UpdateOperation.PORT_DOWN));
                controller.publishUpdate(update);
                linkDeleted = true;
            } else if (ps.getReason() ==
                    (byte) OFPortReason.OFPPR_MODIFY.ordinal()) {
                // If ps is a port modification and the port state has changed
                // that affects links in the topology

                if (this.portLinks.containsKey(npt)) {
                    for (Link lt : this.portLinks.get(npt)) {
                        LinkInfo linkInfo = links.get(lt);
                        assert (linkInfo != null);
                        Integer updatedSrcPortState = null;
                        Integer updatedDstPortState = null;
                        if (lt.getSrc() == npt.getNodeId() &&
                                lt.getSrcPort() == npt.getPortId() &&
                                (linkInfo.getSrcPortState() !=
                                        ps.getDesc().getState())) {
                            updatedSrcPortState = ps.getDesc().getState();
                            linkInfo.setSrcPortState(updatedSrcPortState);
                        }
                        if (lt.getDst() == npt.getNodeId() &&
                                lt.getDstPort() == npt.getPortId() &&
                                (linkInfo.getDstPortState() !=
                                        ps.getDesc().getState())) {
                            updatedDstPortState = ps.getDesc().getState();
                            linkInfo.setDstPortState(updatedDstPortState);
                        }
                        if ((updatedSrcPortState != null) ||
                                (updatedDstPortState != null)) {
                            // The link is already known to link discovery
                            // manager and the status has changed, therefore
                            // send an LinkUpdate.
                            UpdateOperation operation =
                                    getUpdateOperation(linkInfo.getSrcPortState(),
                                            linkInfo.getDstPortState());
                            LinkUpdate update = new LinkUpdate(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                                    lt.getDst(), lt.getDstPort(),
                                    getLinkType(lt, linkInfo),
                                    operation));
                            controller.publishUpdate(update);

                            linkInfoChanged = true;
                        }
                    }
                }

                UpdateOperation operation =
                        getUpdateOperation(ps.getDesc().getState());
                LinkUpdate update = new LinkUpdate(new LDUpdate(sw, port, operation));
                controller.publishUpdate(update);
            }

            if (!linkDeleted && !linkInfoChanged) {
                if (log.isTraceEnabled()) {
                    log.trace("handlePortStatus: Switch {} port #{} reason {};" +
                            " no links to update/remove",
                            new Object[]{HexString.toHexString(sw),
                                    ps.getDesc().getPortNumber(),
                                    ps.getReason()});
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (!linkDeleted) {
            // Send LLDP right away when port state is changed for faster
            // cluster-merge. If it is a link delete then there is not need
            // to send the LLDPs right away and instead we wait for the LLDPs
            // to be sent on the timer as it is normally done
            // do it outside the write-lock
            // sendLLDPTask.reschedule(1000, TimeUnit.MILLISECONDS);
            processNewPort(npt.getNodeId(), npt.getPortId());
        }
        return Command.CONTINUE;
    }

    /**
     * Process a new port.
     * If link discovery is disabled on the port, then do nothing.
     * If autoportfast feature is enabled and the port is a fast port, then
     * do nothing.
     * Otherwise, send LLDP message.  Add the port to quarantine.
     *
     * @param sw
     * @param p
     */
    private void processNewPort(long sw, short p) {
        if (isLinkDiscoverySuppressed(sw, p)) {
            // Do nothing as link discovery is suppressed.
            return;
        } else if (autoPortFastFeature && isFastPort(sw, p)) {
            // Do nothing as the port is a fast port.
            return;
        } else {
            NodePortTuple npt = new NodePortTuple(sw, p);
            discover(sw, p);
            // if it is not a fast port, add it to quarantine.
            if (!isFastPort(sw, p)) {
                addToQuarantineQueue(npt);
            } else {
                // Add to maintenance queue to ensure that BDDP packets
                // are sent out.
                addToMaintenanceQueue(npt);
            }
        }
    }

    /**
     * We send out LLDP messages when a switch is added to discover the topology.
     *
     * @param sw The IOFSwitch that connected to the controller
     */
    @Override
    public void addedSwitch(IOFSwitch sw) {

        if (sw.getEnabledPorts() != null) {
            for (Short p : sw.getEnabledPortNumbers()) {
                processNewPort(sw.getId(), p);
            }
        }
        // Update event history
        evHistTopoSwitch(sw, EvAction.SWITCH_CONNECTED, "None");
        LinkUpdate update = new LinkUpdate(new LDUpdate(sw.getId(), null,
                UpdateOperation.SWITCH_UPDATED));
        controller.publishUpdate(update);
    }

    /**
     * When a switch disconnects we remove any links from our map and notify.
     */
    @Override
    public void removedSwitch(IOFSwitch iofSwitch) {
        // Update event history
        long sw = iofSwitch.getId();
        evHistTopoSwitch(iofSwitch, EvAction.SWITCH_DISCONNECTED, "None");
        List<Link> eraseList = new ArrayList<Link>();
        lock.writeLock().lock();
        try {
            if (switchLinks.containsKey(sw)) {
                if (log.isTraceEnabled()) {
                    log.trace("Handle switchRemoved. Switch {}; removing links {}",
                            HexString.toHexString(sw), switchLinks.get(sw));
                }
                // add all tuples with an endpoint on this switch to erase list
                eraseList.addAll(switchLinks.get(sw));
                deleteLinks(eraseList, "Switch Removed");

                // Send a switch removed update
                LinkUpdate update = new LinkUpdate(new LDUpdate(sw, null, UpdateOperation.SWITCH_REMOVED));
                controller.publishUpdate(update);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * We don't react the port changed notifications here. we listen for
     * OFPortStatus messages directly. Might consider using this notifier
     * instead
     */
    @Override
    public void switchPortChanged(Long switchId) {
        // no-op
    }

    /**
     * Delete links incident on a given switch port.
     *
     * @param npt
     * @param reason
     */
    protected void deleteLinksOnPort(NodePortTuple npt, String reason) {
        List<Link> eraseList = new ArrayList<Link>();
        if (this.portLinks.containsKey(npt)) {
            if (log.isTraceEnabled()) {
                log.trace("handlePortStatus: Switch {} port #{} " +
                        "removing links {}",
                        new Object[]{HexString.toHexString(npt.getNodeId()),
                                npt.getPortId(),
                                this.portLinks.get(npt)});
            }
            eraseList.addAll(this.portLinks.get(npt));
            deleteLinks(eraseList, reason);
        }
    }

    /**
     * Iterates through the list of links and deletes if the
     * last discovery message reception time exceeds timeout values.
     */
    protected void timeoutLinks() {
        List<Link> eraseList = new ArrayList<Link>();
        Long curTime = System.currentTimeMillis();
        boolean linkChanged = false;

        // reentrant required here because deleteLink also write locks
        lock.writeLock().lock();
        try {
            Iterator<Entry<Link, LinkInfo>> it =
                    this.links.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Link, LinkInfo> entry = it.next();
                Link lt = entry.getKey();
                LinkInfo info = entry.getValue();

                // Timeout the unicast and multicast LLDP valid times
                // independently.
                if ((info.getUnicastValidTime() != null) &&
                        (info.getUnicastValidTime() + (1000L * LINK_TIMEOUT) < curTime)) {
                    info.setUnicastValidTime(null);

                    if (info.getMulticastValidTime() != null) {
                        addLinkToBroadcastDomain(lt);
                    }
                    // Note that even if mTime becomes null later on,
                    // the link would be deleted, which would trigger updateClusters().
                    linkChanged = true;
                }
                if ((info.getMulticastValidTime() != null) &&
                        (info.getMulticastValidTime() + (1000L * LINK_TIMEOUT) < curTime)) {
                    info.setMulticastValidTime(null);
                    // if uTime is not null, then link will remain as openflow
                    // link. If uTime is null, it will be deleted.  So, we
                    // don't care about linkChanged flag here.
                    removeLinkFromBroadcastDomain(lt);
                    linkChanged = true;
                }
                // Add to the erase list only if the unicast
                // time is null.
                if (info.getUnicastValidTime() == null &&
                        info.getMulticastValidTime() == null) {
                    eraseList.add(entry.getKey());
                } else if (linkChanged) {
                    UpdateOperation operation;
                    operation = getUpdateOperation(info.getSrcPortState(),
                            info.getDstPortState());
                    LinkUpdate update = new LinkUpdate(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                            lt.getDst(), lt.getDstPort(),
                            getLinkType(lt, info),
                            operation));
                    controller.publishUpdate(update);
                }
            }

            // if any link was deleted or any link was changed.
            if ((eraseList.size() > 0) || linkChanged) {
                deleteLinks(eraseList, "LLDP timeout");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean portEnabled(OFPhysicalPort port) {
        if (port == null) {
            return false;
        }
        if ((OFPortConfig.OFPPC_PORT_DOWN.getValue() & port.getConfig()) > 0) {
            return false;
        }
        if ((OFPortState.OFPPS_LINK_DOWN.getValue() & port.getState()) > 0) {
            return false;
        }
        // Port STP state doesn't work with multiple VLANs, so ignore it for now
        // if ((port.getState() & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue())
        //    return false;
        return true;
    }

    public Map<NodePortTuple, Set<Link>> getPortBroadcastDomainLinks() {
        return portBroadcastDomainLinks;
    }

    @Override
    public Map<Link, LinkInfo> getLinks() {
        lock.readLock().lock();
        Map<Link, LinkInfo> result;
        try {
            result = new HashMap<Link, LinkInfo>(links);
        } finally {
            lock.readLock().unlock();
        }
        return result;
    }

    protected void addLinkToBroadcastDomain(Link lt) {

        NodePortTuple srcNpt, dstNpt;
        srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
        dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

        if (!portBroadcastDomainLinks.containsKey(srcNpt)) {
            portBroadcastDomainLinks.put(srcNpt, new HashSet<Link>());
        }
        portBroadcastDomainLinks.get(srcNpt).add(lt);

        if (!portBroadcastDomainLinks.containsKey(dstNpt)) {
            portBroadcastDomainLinks.put(dstNpt, new HashSet<Link>());
        }
        portBroadcastDomainLinks.get(dstNpt).add(lt);
    }

    protected void removeLinkFromBroadcastDomain(Link lt) {

        NodePortTuple srcNpt, dstNpt;
        srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
        dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

        if (portBroadcastDomainLinks.containsKey(srcNpt)) {
            portBroadcastDomainLinks.get(srcNpt).remove(lt);
            if (portBroadcastDomainLinks.get(srcNpt).isEmpty()) {
                portBroadcastDomainLinks.remove(srcNpt);
            }
        }

        if (portBroadcastDomainLinks.containsKey(dstNpt)) {
            portBroadcastDomainLinks.get(dstNpt).remove(lt);
            if (portBroadcastDomainLinks.get(dstNpt).isEmpty()) {
                portBroadcastDomainLinks.remove(dstNpt);
            }
        }
    }

    @Override
    public void addListener(ILinkDiscoveryListener listener) {
        linkDiscoveryAware.add(listener);
    }

    /**
     * Register a link discovery aware component.
     *
     * @param linkDiscoveryAwareComponent
     */
    public void addLinkDiscoveryAware(ILinkDiscoveryListener linkDiscoveryAwareComponent) {
        // TODO make this a copy on write set or lock it somehow
        this.linkDiscoveryAware.add(linkDiscoveryAwareComponent);
    }

    /**
     * Deregister a link discovery aware component.
     *
     * @param linkDiscoveryAwareComponent
     */
    public void removeLinkDiscoveryAware(ILinkDiscoveryListener linkDiscoveryAwareComponent) {
        // TODO make this a copy on write set or lock it somehow
        this.linkDiscoveryAware.remove(linkDiscoveryAwareComponent);
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    // IFloodlightModule classes

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ILinkDiscoveryService.class);
        //l.add(ITopologyService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
    getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
                IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>,
                        IFloodlightService>();
        // We are the class that implements the service
        m.put(ILinkDiscoveryService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IThreadPoolService.class);
        l.add(IRestApiService.class);
        // Added by ONOS
        l.add(IControllerRegistryService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
        // Added by ONOS
        registryService = context.getServiceImpl(IControllerRegistryService.class);

        // Set the autoportfast feature to false.
        this.autoPortFastFeature = false;

        // We create this here because there is no ordering guarantee
        this.linkDiscoveryAware = new ArrayList<ILinkDiscoveryListener>();
        this.lock = new ReentrantReadWriteLock();
        this.links = new HashMap<Link, LinkInfo>();
        this.portLinks = new HashMap<NodePortTuple, Set<Link>>();
        this.suppressLinkDiscovery =
                Collections.synchronizedSet(new HashSet<NodePortTuple>());
        this.portBroadcastDomainLinks = new HashMap<NodePortTuple, Set<Link>>();
        this.switchLinks = new HashMap<Long, Set<Link>>();
        this.quarantineQueue = new LinkedBlockingQueue<NodePortTuple>();
        this.maintenanceQueue = new LinkedBlockingQueue<NodePortTuple>();
        // Added by ONOS
        this.remoteSwitches = new HashMap<Long, IOnosRemoteSwitch>();

        this.evHistTopologySwitch =
                new EventHistory<EventHistoryTopologySwitch>("Topology: Switch");
        this.evHistTopologyLink =
                new EventHistory<EventHistoryTopologyLink>("Topology: Link");
        this.evHistTopologyCluster =
                new EventHistory<EventHistoryTopologyCluster>("Topology: Cluster");
    }

    @Override
    @LogMessageDocs({
            @LogMessageDoc(level = "ERROR",
                    message = "No storage source found.",
                    explanation = "Storage source was not initialized; cannot initialize " +
                            "link discovery.",
                    recommendation = LogMessageDoc.REPORT_CONTROLLER_BUG),
            @LogMessageDoc(level = "ERROR",
                    message = "Error in installing listener for " +
                            "switch config table {table}",
                    explanation = "Failed to install storage notification for the " +
                            "switch config table",
                    recommendation = LogMessageDoc.REPORT_CONTROLLER_BUG),
            @LogMessageDoc(level = "ERROR",
                    message = "No storage source found.",
                    explanation = "Storage source was not initialized; cannot initialize " +
                            "link discovery.",
                    recommendation = LogMessageDoc.REPORT_CONTROLLER_BUG),
            @LogMessageDoc(level = "ERROR",
                    message = "Exception in LLDP send timer.",
                    explanation = "An unknown error occured while sending LLDP " +
                            "messages to switches.",
                    recommendation = LogMessageDoc.CHECK_SWITCH)
    })
    public void startUp(FloodlightModuleContext context) {
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        controller =
                context.getServiceImpl(IFloodlightProviderService.class);

        // To be started by the first switch connection
        discoveryTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                try {
                    discoverLinks();
                } catch (Exception e) {
                    log.error("Exception in LLDP send timer.", e);
                } finally {
                    if (!shuttingDown) {
                        // Always reschedule link discovery if we're not
                        // shutting down (no chance of SLAVE role now)
                        log.trace("Rescheduling discovery task");
                        discoveryTask.reschedule(DISCOVERY_TASK_INTERVAL,
                                TimeUnit.SECONDS);
                    }
                }
            }
        });

        // Always reschedule link discovery as we are never in SLAVE role now
        discoveryTask.reschedule(DISCOVERY_TASK_INTERVAL, TimeUnit.SECONDS);

        // Setup the BDDP task.  It is invoked whenever switch port tuples
        // are added to the quarantine list.
        bddpTask = new SingletonTask(ses, new QuarantineWorker());
        bddpTask.reschedule(BDDP_TASK_INTERVAL, TimeUnit.MILLISECONDS);


        // Register for the OpenFlow messages we want to receive
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
        // Register for switch updates
        floodlightProvider.addOFSwitchListener(this);
        if (restApi != null) {
            restApi.addRestletRoutable(new LinkDiscoveryWebRoutable());
        }
        setControllerTLV();
    }

    // ****************************************************
    // Topology Manager's Event History members and methods
    // ****************************************************

    // Topology Manager event history
    public EventHistory<EventHistoryTopologySwitch> evHistTopologySwitch;
    public EventHistory<EventHistoryTopologyLink> evHistTopologyLink;
    public EventHistory<EventHistoryTopologyCluster> evHistTopologyCluster;
    public EventHistoryTopologySwitch evTopoSwitch;
    public EventHistoryTopologyLink evTopoLink;
    public EventHistoryTopologyCluster evTopoCluster;

    // Switch Added/Deleted
    private void evHistTopoSwitch(IOFSwitch sw, EvAction actn, String reason) {
        if (evTopoSwitch == null) {
            evTopoSwitch = new EventHistoryTopologySwitch();
        }
        evTopoSwitch.dpid = sw.getId();
        if ((sw.getChannel() != null) &&
                (SocketAddress.class.isInstance(
                        sw.getChannel().getRemoteAddress()))) {
            evTopoSwitch.ipv4Addr =
                    IPv4.toIPv4Address(((InetSocketAddress) (sw.getChannel().
                            getRemoteAddress())).getAddress().getAddress());
            evTopoSwitch.l4Port =
                    ((InetSocketAddress) (sw.getChannel().
                            getRemoteAddress())).getPort();
        } else {
            evTopoSwitch.ipv4Addr = 0;
            evTopoSwitch.l4Port = 0;
        }
        evTopoSwitch.reason = reason;
        evTopoSwitch = evHistTopologySwitch.put(evTopoSwitch, actn);
    }

    // CHECKSTYLE:OFF suppress warning about too many parameters
    private void evHistTopoLink(long srcDpid, long dstDpid, short srcPort,
                                short dstPort, int srcPortState, int dstPortState,
                                ILinkDiscovery.LinkType linkType,
                                EvAction actn, String reason) {
    // CHECKSTYLE:ON

        if (evTopoLink == null) {
            evTopoLink = new EventHistoryTopologyLink();
        }
        evTopoLink.srcSwDpid = srcDpid;
        evTopoLink.dstSwDpid = dstDpid;
        evTopoLink.srcSwport = srcPort & 0xffff;
        evTopoLink.dstSwport = dstPort & 0xffff;
        evTopoLink.srcPortState = srcPortState;
        evTopoLink.dstPortState = dstPortState;
        evTopoLink.reason = reason;
        switch (linkType) {
            case DIRECT_LINK:
                evTopoLink.linkType = "DIRECT_LINK";
                break;
            case MULTIHOP_LINK:
                evTopoLink.linkType = "MULTIHOP_LINK";
                break;
            case TUNNEL:
                evTopoLink.linkType = "TUNNEL";
                break;
            case INVALID_LINK:
            default:
                evTopoLink.linkType = "Unknown";
                break;
        }
        evTopoLink = evHistTopologyLink.put(evTopoLink, actn);
    }

    public void evHistTopoCluster(long dpid, long clusterIdOld,
                                  long clusterIdNew, EvAction action, String reason) {
        if (evTopoCluster == null) {
            evTopoCluster = new EventHistoryTopologyCluster();
        }
        evTopoCluster.dpid = dpid;
        evTopoCluster.clusterIdOld = clusterIdOld;
        evTopoCluster.clusterIdNew = clusterIdNew;
        evTopoCluster.reason = reason;
        evTopoCluster = evHistTopologyCluster.put(evTopoCluster, action);
    }

    @Override
    public boolean isAutoPortFastFeature() {
        return autoPortFastFeature;
    }

    @Override
    public void setAutoPortFastFeature(boolean autoPortFastFeature) {
        this.autoPortFastFeature = autoPortFastFeature;
    }
}
