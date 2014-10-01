package net.onrc.onos.apps.segmentrouting;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOF13Switch;
import net.floodlightcontroller.core.IOF13Switch.NeighborSet;
import net.floodlightcontroller.core.internal.OFBarrierReplyFuture;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.onrc.onos.api.packet.IPacketListener;
import net.onrc.onos.api.packet.IPacketService;
import net.onrc.onos.core.flowprogrammer.IFlowPusherService;
import net.onrc.onos.core.main.config.IConfigInfoService;
import net.onrc.onos.core.matchaction.MatchAction;
import net.onrc.onos.core.matchaction.MatchActionId;
import net.onrc.onos.core.matchaction.MatchActionOperationEntry;
import net.onrc.onos.core.matchaction.action.Action;
import net.onrc.onos.core.matchaction.action.CopyTtlInAction;
import net.onrc.onos.core.matchaction.action.CopyTtlOutAction;
import net.onrc.onos.core.matchaction.action.DecMplsTtlAction;
import net.onrc.onos.core.matchaction.action.DecNwTtlAction;
import net.onrc.onos.core.matchaction.action.GroupAction;
import net.onrc.onos.core.matchaction.action.PopMplsAction;
import net.onrc.onos.core.matchaction.action.PushMplsAction;
import net.onrc.onos.core.matchaction.action.SetMplsIdAction;
import net.onrc.onos.core.matchaction.match.Ipv4Match;
import net.onrc.onos.core.matchaction.match.Match;
import net.onrc.onos.core.matchaction.match.MplsMatch;
import net.onrc.onos.core.packet.ARP;
import net.onrc.onos.core.packet.Ethernet;
import net.onrc.onos.core.packet.IPv4;
import net.onrc.onos.core.topology.ITopologyListener;
import net.onrc.onos.core.topology.ITopologyService;
import net.onrc.onos.core.topology.LinkData;
import net.onrc.onos.core.topology.MutableTopology;
import net.onrc.onos.core.topology.Port;
import net.onrc.onos.core.topology.PortData;
import net.onrc.onos.core.topology.Switch;
import net.onrc.onos.core.topology.TopologyEvents;
import net.onrc.onos.core.util.Dpid;
import net.onrc.onos.core.util.IPv4Net;
import net.onrc.onos.core.util.SwitchPort;

import org.json.JSONArray;
import org.json.JSONException;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SegmentRoutingManager implements IFloodlightModule,
						ITopologyListener, IPacketListener {

    private static final Logger log = LoggerFactory
            .getLogger(SegmentRoutingManager.class);
    private ITopologyService topologyService;
    private IPacketService packetService;
    private MutableTopology mutableTopology;
    private ConcurrentLinkedQueue<IPv4> ipPacketQueue;

    private List<ArpEntry> arpEntries;
    private ArpHandler arpHandler;
    private GenericIpHandler ipHandler;
    private IcmpHandler icmpHandler;
    private IThreadPoolService threadPool;
    private SingletonTask discoveryTask;
    private IFloodlightProviderService floodlightProvider;

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();

        l.add(IFloodlightProviderService.class);
        l.add(IConfigInfoService.class);
        l.add(ITopologyService.class);
        l.add(IPacketService.class);
        l.add(IFlowPusherService.class);
        l.add(ITopologyService.class);

        return l;

    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        arpHandler = new ArpHandler(context, this);
        icmpHandler = new IcmpHandler(context, this);
        ipHandler = new GenericIpHandler(context, this);
        arpEntries = new ArrayList<ArpEntry>();
        topologyService = context.getServiceImpl(ITopologyService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        mutableTopology = topologyService.getTopology();
        topologyService.addListener(this, false);
        ipPacketQueue = new ConcurrentLinkedQueue<IPv4>();

        this.packetService = context.getServiceImpl(IPacketService.class);
        packetService.registerPacketListener(this);

    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();

        discoveryTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                populateEcmpRoutingRules();
            }
        });
    }

    @Override
    public void receive(Switch sw, Port inPort, Ethernet payload) {
    	if (payload.getEtherType() == Ethernet.TYPE_ARP)
    		arpHandler.processPacketIn(sw, inPort, payload);
        if (payload.getEtherType() == Ethernet.TYPE_IPV4) {
            addPacket((IPv4)payload.getPayload());
        	if (((IPv4)payload.getPayload()).getProtocol() == IPv4.PROTOCOL_ICMP)
        		icmpHandler.processPacketIn(sw, inPort, payload);
        	else
        		ipHandler.processPacketIn(sw, inPort, payload);
        }
        else {
            log.debug("{}", payload.toString());
        }
    }
    /**
     * Update ARP Cache using ARP packets
     * It is used to set destination MAC address to forward packets to known hosts.
     * But, it will be replace with Host information of Topology service later.
     *
     * @param arp APR packets to use for updating ARP entries
     */
    public void updateArpCache(ARP arp) {

        ArpEntry arpEntry = new ArpEntry(arp.getSenderHardwareAddress(), arp.getSenderProtocolAddress());
        // TODO: Need to check the duplication
        arpEntries.add(arpEntry);
    }

    /**
     * Get MAC address to known hosts
     *
     * @param destinationAddress IP address to get MAC address
     * @return MAC Address to given IP address
     */
    public byte[] getMacAddressFromIpAddress(int destinationAddress) {

        // Can't we get the host IP address from the TopologyService ??

        Iterator<ArpEntry> iterator = arpEntries.iterator();

        IPv4Address ipAddress = IPv4Address.of(destinationAddress);
        byte[] ipAddressInByte = ipAddress.getBytes();

        while (iterator.hasNext() ) {
            ArpEntry arpEntry = iterator.next();
            byte[] address = arpEntry.targetIpAddress;

            IPv4Address a = IPv4Address.of(address);
            IPv4Address b = IPv4Address.of(ipAddressInByte);

            if ( a.equals(b)) {
                log.debug("Found an arp entry");
                return arpEntry.targetMacAddress;
            }
        }

        return null;
    }

    /**
     * Send an ARP request via ArpHandler
     * @param destinationAddress
     * @param sw
     * @param inPort
     *
     */
    public void sendArpRequest(Switch sw, int destinationAddress, Port inPort) {
        arpHandler.sendArpRequest(sw, destinationAddress, inPort);
    }

    /**
     * Temporary class to to keep ARP entry
     *
     */
    private class ArpEntry {

        byte[] targetMacAddress;
        byte[] targetIpAddress;

        private ArpEntry(byte[] macAddress, byte[] ipAddress) {
            this.targetMacAddress = macAddress;
            this.targetIpAddress = ipAddress;
        }
    }

    /**
     * Topology events that have been generated.
     *
     * @param topologyEvents the generated Topology Events
     * @see TopologyEvents
     */
    public void topologyEvents(TopologyEvents topologyEvents)
    {
        if (topologyEvents.getAddedLinkDataEntries() != null)
        {
            discoveryTask.reschedule(1, TimeUnit.SECONDS);
        }

        Collection<PortData> portEntries =
                topologyEvents.getRemovedPortDataEntries();
        if (!portEntries.isEmpty()) {
            processPortRemoval(portEntries);
        }

        Collection<LinkData> linkEntries =
                topologyEvents.getRemovedLinkDataEntries();
        if (!linkEntries.isEmpty()) {
            processLinkRemoval(linkEntries);
        }
    }

    /**
     * Check if all links are gone b/w the two switches.
     * If all links are gone, then we need to recalculate the path.
     * Otherwise, just report link failure to the driver.
     *
     * @param linkEntries
     */
    private void processLinkRemoval(Collection<LinkData> linkEntries) {
        for (LinkData link: linkEntries) {
            Dpid srcSwDpid = link.getSrc().getDpid();
            Dpid dstSwDpid = link.getDst().getDpid();

            Switch srcSwitch = mutableTopology.getSwitch(srcSwDpid);
            if (srcSwitch.getLinkToNeighbor(dstSwDpid) == null) {
                discoveryTask.reschedule(1, TimeUnit.SECONDS);
                log.debug("All links are gone b/w {} and {}",srcSwDpid,
                        dstSwDpid);
            }
        }
    }

    /**
     * report ports removed to the driver
     *
     * @param portEntries
     */
    private void processPortRemoval(Collection<PortData> portEntries) {
        for (PortData port: portEntries) {
            Dpid dpid = port.getDpid();
            int portNo = (int) port.getPortNumber().value();

            IOF13Switch sw13 = (IOF13Switch)floodlightProvider.getMasterSwitch(
                    getSwId(port.getDpid().toString()));
            //sw13.removePort(portNo);
            log.debug("Remove port {} from switch {}", portNo, dpid.toString());
        }
    }

    /**
     * Populate routing rules walking through the ECMP shortest paths
     *
     */
    private void populateEcmpRoutingRules() {

        Iterable<Switch> switches= mutableTopology.getSwitches();
        for (Switch sw : switches) {
            ECMPShortestPathGraph ecmpSPG = new ECMPShortestPathGraph(sw);
            log.debug("ECMPShortestPathGraph is computed for switch {}",
                    HexString.toHexString(sw.getDpid().value()));

            HashMap<Integer, HashMap<Switch,ArrayList<ArrayList<Dpid>>>> switchVia =
                    ecmpSPG.getAllLearnedSwitchesAndVia();
            for (Integer itrIdx: switchVia.keySet()){
                log.debug("ECMPShortestPathGraph:Switches learned in "
                        + "Iteration{} from switch {}:",
                        itrIdx,
                        HexString.toHexString(sw.getDpid().value()));
                HashMap<Switch, ArrayList<ArrayList<Dpid>>> swViaMap =
                                switchVia.get(itrIdx);
                for (Switch targetSw: swViaMap.keySet()){
                    log.debug("ECMPShortestPathGraph:****switch {} via:",
                            HexString.toHexString(targetSw.getDpid().value()));
                    String destSw = sw.getDpid().toString();
                    List<String> fwdToSw = new ArrayList<String>();

                    int i=0;
                    for (ArrayList<Dpid> via:swViaMap.get(targetSw)){
                        log.debug("ECMPShortestPathGraph:******{}) {}",++i,via);
                        if (via.isEmpty()) {
                            fwdToSw.add(destSw);
                        }
                        else {
                            fwdToSw.add(via.get(0).toString());
                        }
                    }
                    setRoutingRule(targetSw, destSw, fwdToSw);
                }

                // Send Barrier Message and make sure all rules are set
                // before we set the rules to next routers
                IOF13Switch sw13 = (IOF13Switch)floodlightProvider.getMasterSwitch(
                        getSwId(sw.getDpid().toString()));
                try {
                    OFBarrierReplyFuture replyFuture = sw13.sendBarrier();
                    replyFuture.get(10, TimeUnit.SECONDS);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                catch (InterruptedException | ExecutionException | TimeoutException e) {
                    log.error("Barrier message not received for sw: {}", sw.getDpid());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     *
     * Set routing rules in targetSw
     * {forward packets to fwdToSw switches in order to send packets to destSw}
     * - If the target switch is an edge router and final destnation switch is also
     *   an edge router, then set IP forwarding rules to subnets
     * - If only the target switch is an edge router, then set IP forwarding rule to
     *   the transit router loopback IP address
     * - If the target is a transit router, then just set the MPLS forwarding rule
     *
     * @param targetSw Switch to set the rules
     * @param destSw  Final destination switches
     * @param fwdToSw next hop switches
     */
    private void setRoutingRule(Switch targetSw, String destSw, List<String> fwdToSw) {


        if (fwdToSw.isEmpty()) {
            fwdToSw.add(destSw);
        }

        // if both target SW and dest SW are an edge router, then set IP table
        if (IsEdgeRouter(targetSw.getDpid().toString()) &&
                IsEdgeRouter(destSw)) {
            // We assume that there is at least one transit router b/w edge routers
            Switch destSwitch = mutableTopology.getSwitch(new Dpid(destSw));
            String subnets = destSwitch.getStringAttribute("subnets");
            setIpTableRouterSubnet(targetSw, subnets, getMplsLabel(destSw)
                    ,fwdToSw);

            String routerIp = destSwitch.getStringAttribute("routerIp");
            setIpTableRouter(targetSw, routerIp, getMplsLabel(destSw), fwdToSw, null);
        }
        // Only if the target switch is the edge router, then set the IP rules
        else if (IsEdgeRouter(targetSw.getDpid().toString())) {
            // We assume that there is at least one transit router b/w edge routers
            Switch destSwitch = mutableTopology.getSwitch(new Dpid(destSw));
            String routerIp = destSwitch.getStringAttribute("routerIp");
            setIpTableRouter(targetSw, routerIp, getMplsLabel(destSw), fwdToSw, null);
        }
        // if it is a transit router, then set rules in the MPLS table
        else {
            setMplsTable(targetSw, getMplsLabel(destSw), fwdToSw);
        }

    }

    private void setIpTableRouterSubnet(Switch targetSw, String subnets,
            String mplsLabel, List<String> fwdToSw) {

        Collection <MatchActionOperationEntry> entries =
                new ArrayList<MatchActionOperationEntry>();

        try {
            JSONArray arry = new JSONArray(subnets);
            for (int i = 0; i < arry.length(); i++) {
                String subnetIp = (String) arry.getJSONObject(i).get("subnetIp");
                setIpTableRouter(targetSw, subnetIp, mplsLabel, fwdToSw, entries);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (!entries.isEmpty()) {
            IOF13Switch sw13 = (IOF13Switch)floodlightProvider.getMasterSwitch(
                    getSwId(targetSw.getDpid().toString()));

            try {
                sw13.pushFlows(entries);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Check if the switch is the edge router or not
     * If any subnet information is defined in the config file, the we assume
     * it is an edge router
     *
     * @param dpid  Dpid of the switch to check
     * @return true if it is an edge router, otherwise false
     */
    private boolean IsEdgeRouter(String dpid) {

        for (Switch sw: mutableTopology.getSwitches()) {
            String dpidStr = sw.getDpid().toString();
            if (dpid.equals(dpidStr)) {
                String subnetInfo = sw.getStringAttribute("subnets");
                if (subnetInfo == null || subnetInfo.equals("[]")) {
                    return false;
                }
                else
                    return true;
            }
        }

        return false;
    }

    /**
     * Set IP forwarding rule
     *  - If the destination is the next hop, then do not push MPLS,
     *    just decrease the NW TTL
     *  - Otherwise, push MPLS label and set the MPLS ID
     *
     * @param sw  target switch to set rules
     * @param subnetIp Match IP address
     * @param mplsLabel MPLS label of final destination router
     * @param fwdToSws next hop routers
     * @param entries
     */
    private void setIpTableRouter(Switch sw, String subnetIp, String mplsLabel,
            List<String> fwdToSws, Collection<MatchActionOperationEntry> entries) {

        Ipv4Match ipMatch = new Ipv4Match(subnetIp);
        List<Action> actions = new ArrayList<>();

        // If destination SW is the same as the fwd SW, then do not push MPLS label

        if (fwdToSws.size() > 1) {
            PushMplsAction pushMplsAction = new PushMplsAction();
            SetMplsIdAction setIdAction = new SetMplsIdAction(Integer.parseInt(mplsLabel));
            CopyTtlOutAction copyTtlOutAction = new CopyTtlOutAction();
            DecMplsTtlAction decMplsTtlAction = new DecMplsTtlAction(1);

            actions.add(pushMplsAction);
            actions.add(copyTtlOutAction);
            actions.add(decMplsTtlAction);
            actions.add(setIdAction);
        }
        else {
            String fwdToSw = fwdToSws.get(0);
            if (getMplsLabel(fwdToSw).equals(mplsLabel)) {
                DecNwTtlAction decTtlAction = new DecNwTtlAction(1);
                actions.add(decTtlAction);
            }
            else {
                PushMplsAction pushMplsAction = new PushMplsAction();
                SetMplsIdAction setIdAction = new SetMplsIdAction(Integer.parseInt(mplsLabel));
                CopyTtlOutAction copyTtlOutAction = new CopyTtlOutAction();
                DecMplsTtlAction decMplsTtlAction = new DecMplsTtlAction(1);

                actions.add(pushMplsAction);
                actions.add(copyTtlOutAction);
                actions.add(decMplsTtlAction);
                actions.add(setIdAction);
            }
        }

        GroupAction groupAction = new GroupAction();

        for (String fwdSw : fwdToSws) {
            groupAction.addSwitch(new Dpid(fwdSw));
        }
        actions.add(groupAction);

        // TODO: Mactch Action Id should be set correctly
        MatchAction matchAction = new MatchAction(new MatchActionId(0),
                new SwitchPort((long)0,(short)0), ipMatch, actions);

        MatchActionOperationEntry maEntry =
            new MatchActionOperationEntry(
                    net.onrc.onos.core.matchaction.MatchActionOperations.Operator.ADD,
                    matchAction);

        IOF13Switch sw13 = (IOF13Switch)floodlightProvider.getMasterSwitch(
                getSwId(sw.getDpid().toString()));

        try {
            printMatchActionOperationEntry(sw, maEntry);
            if (entries != null)
                entries.add(maEntry);
            else
                sw13.pushFlow(maEntry);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    /**
     * Convert a string DPID to its Switch Id (integer)
     *
     * @param dpid
     * @return
     */
    private long getSwId(String dpid) {

        long swId = 0;

        String swIdStr = dpid.substring(dpid.lastIndexOf(":")+1);
        if (swIdStr != null)
            swId = Integer.parseInt(swIdStr);

        return swId;
    }

    /**
     * Set MPLS forwarding rules to MPLS table
     *   - If the destination is the same as the next hop to forward packets
     *     then, pop the MPLS label according to PHP rule
     *   - Otherwise, just forward packets to next hops using Group action
     *
     * @param sw  Switch to set the rules
     * @param mplsLabel destination MPLS label
     * @param fwdSws  next hop switches
     */
    private void setMplsTable(Switch sw, String mplsLabel, List<String> fwdSws) {

        MplsMatch mplsMatch = new MplsMatch(Integer.parseInt(mplsLabel));

        List<Action> actions = new ArrayList<Action>();

        // If the destination is the same as the next hop, then pop MPLS
        // Otherwise, just decrease the MPLS TTL.
        if (fwdSws.size() == 1) {
            String fwdMplsId = getMplsLabel(fwdSws.get(0));
            if (fwdMplsId.equals(mplsLabel)) {
                String fwdSw = fwdSws.get(0);
                if (mplsLabel.equals(getMplsLabel(fwdSw))) {
                    PopMplsAction popAction = new PopMplsAction(EthType.IPv4);
                    CopyTtlInAction copyTtlInAction = new CopyTtlInAction();
                    DecNwTtlAction decNwTtlAction = new DecNwTtlAction(1);

                    actions.add(copyTtlInAction);
                    actions.add(popAction);
                    actions.add(decNwTtlAction);
                }
            }
            else {
                DecMplsTtlAction decMplsTtlAction = new DecMplsTtlAction(1);
                actions.add(decMplsTtlAction);
            }
        }
        GroupAction groupAction = new GroupAction();
        for (String fwdSw: fwdSws)
            groupAction.addSwitch(new Dpid(fwdSw));
        actions.add(groupAction);

        MatchAction matchAction = new MatchAction(new MatchActionId(0),
                new SwitchPort((long)0,(short)0), mplsMatch, actions);

        MatchActionOperationEntry maEntry =
            new MatchActionOperationEntry(
                    net.onrc.onos.core.matchaction.MatchActionOperations.Operator.ADD,
                    matchAction);

        IOF13Switch sw13 = (IOF13Switch)floodlightProvider.getMasterSwitch(
                getSwId(sw.getDpid().toString()));

        try {
            printMatchActionOperationEntry(sw, maEntry);
            sw13.pushFlow(maEntry);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    /**
     * Debugging function to print out the Match Action Entry
     *
     * @param maEntry
     */
    private void printMatchActionOperationEntry(Switch sw,
            MatchActionOperationEntry maEntry) {

        StringBuilder logStr = new StringBuilder("In switch " + sw.getDpid() + ", ");

        MatchAction ma = maEntry.getTarget();
        Match m = ma.getMatch();
        List<Action> actions = ma.getActions();

        if (m instanceof Ipv4Match) {
            logStr.append("If the IP matches with ");
            IPv4Net ip = ((Ipv4Match) m).getDestination();
            logStr.append(ip.toString());
            logStr.append(" then ");
        }
        else if (m instanceof MplsMatch) {
            logStr.append("If the MPLS label matches with ");
            int mplsLabel = ((MplsMatch) m).getMplsLabel();
            logStr.append(mplsLabel);
            logStr.append(" then ");
        }

        logStr.append(" do { ");
        for (Action action: actions) {
            if (action instanceof CopyTtlInAction) {
                logStr.append("copy ttl In, ");
            }
            else if (action instanceof CopyTtlOutAction) {
                logStr.append("copy ttl Out, ");
            }
            else if (action instanceof DecMplsTtlAction) {
                logStr.append("Dec MPLS TTL , ");
            }
            else if (action instanceof GroupAction) {
                logStr.append("Forward packet to < ");
                NeighborSet dpids = ((GroupAction)action).getDpids();
                logStr.append(dpids.toString() + ",");

            }
            else if (action instanceof PopMplsAction) {
                logStr.append("Pop MPLS label, ");
            }
            else if (action instanceof PushMplsAction) {
                logStr.append("Push MPLS label, ");
            }
            else if (action instanceof SetMplsIdAction) {
                int id = ((SetMplsIdAction)action).getMplsId();
                logStr.append("Set MPLS ID as " + id + ", ");

            }
        }

        log.debug(logStr.toString());

    }

    /**
     * Get MPLS label reading the config file
     *
     * @param dipid  DPID of the switch
     * @return MPLS label for the switch
     */

    private String getMplsLabel(String dpid) {

        String mplsLabel = null;
        for (Switch sw: mutableTopology.getSwitches()) {
            String dpidStr = sw.getDpid().toString();
            if (dpid.equals(dpidStr)) {
                mplsLabel = sw.getStringAttribute("nodeSid");
                break;
            }
        }

        return mplsLabel;
    }



    /**
     * The function checks if given IP matches to the given subnet mask
     *
     * @param addr - subnet address to match
     * @param addr1 - IP address to check
     * @return true if the IP address matches to the subnet, otherwise false
     */
    public boolean netMatch(String addr, String addr1){ //addr is subnet address and addr1 is ip address. Function will return true, if addr1 is within addr(subnet)

        String[] parts = addr.split("/");
        String ip = parts[0];
        int prefix;

        if (parts.length < 2) {
            prefix = 0;
        } else {
            prefix = Integer.parseInt(parts[1]);
        }

        Inet4Address a =null;
        Inet4Address a1 =null;
        try {
            a = (Inet4Address) InetAddress.getByName(ip);
            a1 = (Inet4Address) InetAddress.getByName(addr1);
        } catch (UnknownHostException e){}

        byte[] b = a.getAddress();
        int ipInt = ((b[0] & 0xFF) << 24) |
                         ((b[1] & 0xFF) << 16) |
                         ((b[2] & 0xFF) << 8)  |
                         ((b[3] & 0xFF) << 0);

        byte[] b1 = a1.getAddress();
        int ipInt1 = ((b1[0] & 0xFF) << 24) |
                         ((b1[1] & 0xFF) << 16) |
                         ((b1[2] & 0xFF) << 8)  |
                         ((b1[3] & 0xFF) << 0);

        int mask = ~((1 << (32 - prefix)) - 1);

        if ((ipInt & mask) == (ipInt1 & mask)) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Add a routing rule for the host
     *
     * @param sw - Switch to add the rule
     * @param hostIpAddress Destination host IP address
     * @param hostMacAddress Destination host MAC address
     */
    public void addRouteToHost(Switch sw, int hostIpAddress, byte[] hostMacAddress) {
        ipHandler.addRouteToHost(sw, hostIpAddress, hostMacAddress);

    }

    /**
     * Add IP packet to a buffer queue
     *
     * @param ipv4
     */
    public void addPacket(IPv4 ipv4) {
        ipPacketQueue.add(ipv4);
    }

    /**
     * Retrieve all packets whose destination is the given address.
     *
     * @param destIp Destination address of packets to retrieve
     */
    public List<IPv4> getIpPacketFromQueue(byte[] destIp) {

        List<IPv4> bufferedPackets = new ArrayList<IPv4>();

        if (!ipPacketQueue.isEmpty()) {
            for (IPv4 ip: ipPacketQueue) {
                int dest = ip.getDestinationAddress();
                IPv4Address ip1 = IPv4Address.of(dest);
                IPv4Address ip2 = IPv4Address.of(destIp);
                if (ip1.equals(ip2)) {
                    bufferedPackets.add((IPv4)(ipPacketQueue.poll()).clone());
                }
            }
        }

        return bufferedPackets;
    }

}
