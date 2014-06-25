package net.onrc.onos.core.util;


import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * The class representing the Flow Entry.
 * <p/>
 * NOTE: The specification is incomplete. E.g., the entry needs to
 * support multiple in-ports and multiple out-ports.
 */
public class FlowEntry {
    private FlowId flowId;            // FlowID of the Flow Entry
    private FlowEntryId flowEntryId;        // The Flow Entry ID
    private int idleTimeout;        // The Flow idle timeout
    private int hardTimeout;        // The Flow hard timeout
    private int priority;        // The Flow priority
    private FlowEntryMatch flowEntryMatch;    // The Flow Entry Match
    private FlowEntryActions flowEntryActions;    // The Flow Entry Actions
    private Dpid dpid;                // The Switch DPID
    private PortNumber inPort;        // The Switch incoming port. Used only
    // when the entry is used to return
    // Shortest Path computation.
    private PortNumber outPort;        // The Switch outgoing port. Used only
    // when the entry is used to return
    // Shortest Path computation.
    private FlowEntryUserState flowEntryUserState; // The Flow Entry User state
    private FlowEntrySwitchState flowEntrySwitchState; // The Flow Entry Switch state
    // The Flow Entry Error state (if FlowEntrySwitchState is FE_SWITCH_FAILED)
    private FlowEntryErrorState flowEntryErrorState;

    /**
     * Default constructor.
     */
    public FlowEntry() {
        // TODO: Test code
    /*
    MACAddress mac = MACAddress.valueOf("01:02:03:04:05:06");
    IPv4 ipv4 = new IPv4("1.2.3.4");
    IPv4Net ipv4net = new IPv4Net("5.6.7.0/24");

    flowEntryMatch = new FlowEntryMatch();
    flowEntryMatch.enableInPort(new Port((short)10));
    flowEntryMatch.enableSrcMac(mac);
    flowEntryMatch.enableDstMac(mac);
    flowEntryMatch.enableVlanId((short)20);
    flowEntryMatch.enableVlanPriority((byte)30);
    flowEntryMatch.enableEthernetFrameType((short)40);
    flowEntryMatch.enableIpToS((byte)50);
    flowEntryMatch.enableIpProto((byte)60);
    flowEntryMatch.enableSrcIPv4Net(ipv4net);
    flowEntryMatch.enableDstIPv4Net(ipv4net);
    flowEntryMatch.enableSrcTcpUdpPort((short)70);
    flowEntryMatch.enableDstTcpUdpPort((short)80);

    FlowEntryAction action = null;
    FlowEntryActions actions = new FlowEntryActions();

    action = new FlowEntryAction();
    action.setActionOutput(new Port((short)12));
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionOutputToController((short)13);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionSetVlanId((short)14);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionSetVlanPriority((byte)15);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionStripVlan(true);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionSetEthernetSrcAddr(mac);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionSetEthernetDstAddr(mac);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionSetIPv4SrcAddr(ipv4);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionSetIPv4DstAddr(ipv4);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionSetIpToS((byte)16);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionSetTcpUdpSrcPort((short)17);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionSetTcpUdpDstPort((short)18);
    actions.addAction(action);

    action = new FlowEntryAction();
    action.setActionEnqueue(new Port((short)19), 20);
    actions.addAction(action);

    setFlowEntryActions(actions);
    */

        priority = FlowPath.PRIORITY_DEFAULT;
        flowEntryActions = new FlowEntryActions();
        flowEntryUserState = FlowEntryUserState.FE_USER_UNKNOWN;
        flowEntrySwitchState = FlowEntrySwitchState.FE_SWITCH_UNKNOWN;
    }

    /**
     * Get the Flow ID.
     *
     * @return the Flow ID.
     */
    @JsonIgnore
    public FlowId flowId() {
        return flowId;
    }

    /**
     * Set the Flow ID.
     *
     * @param flowId the Flow ID to set.
     */
    public void setFlowId(FlowId flowId) {
        this.flowId = flowId;
    }

    /**
     * Test whether the Flow ID is valid.
     *
     * @return true if the Flow ID is valid, otherwise false.
     */
    @JsonIgnore
    public boolean isValidFlowId() {
        if (this.flowId == null) {
            return false;
        }
        return (this.flowId.isValid());
    }

    /**
     * Get the Flow Entry ID.
     *
     * @return the Flow Entry ID.
     */
    @JsonProperty("flowEntryId")
    public FlowEntryId flowEntryId() {
        return flowEntryId;
    }

    /**
     * Set the Flow Entry ID.
     *
     * @param flowEntryId the Flow Entry ID to set.
     */
    @JsonProperty("flowEntryId")
    public void setFlowEntryId(FlowEntryId flowEntryId) {
        this.flowEntryId = flowEntryId;
    }

    /**
     * Test whether the Flow Entry ID is valid.
     *
     * @return true if the Flow Entry ID is valid, otherwise false.
     */
    @JsonIgnore
    public boolean isValidFlowEntryId() {
        if (this.flowEntryId == null) {
            return false;
        }
        return (this.flowEntryId.isValid());
    }

    /**
     * Get the flow idle timeout in seconds.
     * <p/>
     * It should be an unsigned integer in the interval [0, 65535].
     * If zero, the timeout is not set.
     *
     * @return the flow idle timeout.
     */
    @JsonProperty("idleTimeout")
    public int idleTimeout() {
        return idleTimeout;
    }

    /**
     * Set the flow idle timeout in seconds.
     * <p/>
     * It should be an unsigned integer in the interval [0, 65535].
     * If zero, the timeout is not set.
     *
     * @param idleTimeout the flow idle timeout to set.
     */
    @JsonProperty("idleTimeout")
    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = 0xffff & idleTimeout;
    }

    /**
     * Get the flow hard timeout in seconds.
     * <p/>
     * It should be an unsigned integer in the interval [0, 65535].
     * If zero, the timeout is not set.
     *
     * @return the flow hard timeout.
     */
    @JsonProperty("hardTimeout")
    public int hardTimeout() {
        return hardTimeout;
    }

    /**
     * Set the flow hard timeout in seconds.
     * <p/>
     * It should be an unsigned integer in the interval [0, 65535].
     * If zero, the timeout is not set.
     *
     * @param hardTimeout the flow hard timeout to set.
     */
    @JsonProperty("hardTimeout")
    public void setHardTimeout(int hardTimeout) {
        this.hardTimeout = 0xffff & hardTimeout;
    }

    /**
     * Get the flow priority.
     * <p/>
     * It should be an unsigned integer in the interval [0, 65535].
     *
     * @return the flow priority.
     */
    @JsonProperty("priority")
    public int priority() {
        return priority;
    }

    /**
     * Set the flow priority.
     * <p/>
     * It should be an unsigned integer in the interval [0, 65535].
     *
     * @param priority the flow priority to set.
     */
    @JsonProperty("priority")
    public void setPriority(int priority) {
        this.priority = 0xffff & priority;
    }

    /**
     * Get the Flow Entry Match.
     *
     * @return the Flow Entry Match.
     */
    @JsonProperty("flowEntryMatch")
    public FlowEntryMatch flowEntryMatch() {
        return flowEntryMatch;
    }

    /**
     * Set the Flow Entry Match.
     *
     * @param flowEntryMatch the Flow Entry Match to set.
     */
    @JsonProperty("flowEntryMatch")
    public void setFlowEntryMatch(FlowEntryMatch flowEntryMatch) {
        this.flowEntryMatch = flowEntryMatch;
    }

    /**
     * Get the Flow Entry Actions.
     *
     * @return the Flow Entry Actions.
     */
    @JsonProperty("flowEntryActions")
    public FlowEntryActions flowEntryActions() {
        return flowEntryActions;
    }

    /**
     * Set the Flow Entry Actions.
     *
     * @param flowEntryActions the Flow Entry Actions to set.
     */
    @JsonProperty("flowEntryActions")
    public void setFlowEntryActions(FlowEntryActions flowEntryActions) {
        this.flowEntryActions = flowEntryActions;
    }

    /**
     * Get the Switch DPID.
     *
     * @return the Switch DPID.
     */
    @JsonProperty("dpid")
    public Dpid dpid() {
        return dpid;
    }

    /**
     * Set the Switch DPID.
     *
     * @param dpid the Switch DPID to set.
     */
    @JsonProperty("dpid")
    public void setDpid(Dpid dpid) {
        this.dpid = dpid;
    }

    /**
     * Get the Switch incoming port.
     * <p/>
     * Used only when the entry is used to return Shortest Path computation.
     *
     * @return the Switch incoming port.
     */
    @JsonProperty("inPort")
    public PortNumber inPort() {
        return inPort;
    }

    /**
     * Set the Switch incoming port.
     * <p/>
     * Used only when the entry is used to return Shortest Path computation.
     *
     * @param inPort the Switch incoming port to set.
     */
    @JsonProperty("inPort")
    public void setInPort(PortNumber inPort) {
        this.inPort = inPort;
    }

    /**
     * Get the Switch outgoing port.
     * <p/>
     * Used only when the entry is used to return Shortest Path computation.
     *
     * @return the Switch outgoing port.
     */
    @JsonProperty("outPort")
    public PortNumber outPort() {
        return outPort;
    }

    /**
     * Set the Switch outgoing port.
     * <p/>
     * Used only when the entry is used to return Shortest Path computation.
     *
     * @param outPort the Switch outgoing port to set.
     */
    @JsonProperty("outPort")
    public void setOutPort(PortNumber outPort) {
        this.outPort = outPort;
    }

    /**
     * Get the Flow Entry User state.
     *
     * @return the Flow Entry User state.
     */
    @JsonProperty("flowEntryUserState")
    public FlowEntryUserState flowEntryUserState() {
        return flowEntryUserState;
    }

    /**
     * Set the Flow Entry User state.
     *
     * @param flowEntryUserState the Flow Entry User state to set.
     */
    @JsonProperty("flowEntryUserState")
    public void setFlowEntryUserState(FlowEntryUserState flowEntryUserState) {
        this.flowEntryUserState = flowEntryUserState;
    }

    /**
     * Get the Flow Entry Switch state.
     * <p/>
     * The Flow Entry Error state is used if FlowEntrySwitchState is
     * FE_SWITCH_FAILED.
     *
     * @return the Flow Entry Switch state.
     */
    @JsonProperty("flowEntrySwitchState")
    public FlowEntrySwitchState flowEntrySwitchState() {
        return flowEntrySwitchState;
    }

    /**
     * Set the Flow Entry Switch state.
     * <p/>
     * The Flow Entry Error state is used if FlowEntrySwitchState is
     * FE_SWITCH_FAILED.
     *
     * @param flowEntrySwitchState the Flow Entry Switch state to set.
     */
    @JsonProperty("flowEntrySwitchState")
    public void setFlowEntrySwitchState(FlowEntrySwitchState flowEntrySwitchState) {
        this.flowEntrySwitchState = flowEntrySwitchState;
    }

    /**
     * Get the Flow Entry Error state.
     *
     * @return the Flow Entry Error state.
     */
    @JsonProperty("flowEntryErrorState")
    public FlowEntryErrorState flowEntryErrorState() {
        return flowEntryErrorState;
    }

    /**
     * Set the Flow Entry Error state.
     *
     * @param flowEntryErrorState the Flow Entry Error state to set.
     */
    @JsonProperty("flowEntryErrorState")
    public void setFlowEntryErrorState(FlowEntryErrorState flowEntryErrorState) {
        this.flowEntryErrorState = flowEntryErrorState;
    }

    /**
     * Convert the flow entry to a string.
     * <p/>
     * The string has the following form:
     * [flowEntryId=XXX idleTimeout=XXX hardTimeout=XXX priority=XXX
     * flowEntryMatch=XXX flowEntryActions=XXX dpid=XXX
     * inPort=XXX outPort=XXX flowEntryUserState=XXX flowEntrySwitchState=XXX
     * flowEntryErrorState=XXX]
     *
     * @return the flow entry as a string.
     */
    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        if (flowEntryId != null) {
            ret.append("[flowEntryId=" + this.flowEntryId.toString());
        } else {
            ret.append("[");
        }
        if (flowId != null) {
            ret.append(" flowId=" + this.flowId.toString());
        }
        ret.append(" idleTimeout=" + this.idleTimeout);
        ret.append(" hardTimeout=" + this.hardTimeout);
        ret.append(" priority=" + this.priority);
        if (flowEntryMatch != null) {
            ret.append(" flowEntryMatch=" + this.flowEntryMatch.toString());
        }
        ret.append(" flowEntryActions=" + this.flowEntryActions.toString());
        if (dpid != null) {
            ret.append(" dpid=" + this.dpid.toString());
        }
        if (inPort != null) {
            ret.append(" inPort=" + this.inPort.toString());
        }
        if (outPort != null) {
            ret.append(" outPort=" + this.outPort.toString());
        }
        ret.append(" flowEntryUserState=" + this.flowEntryUserState);
        ret.append(" flowEntrySwitchState=" + this.flowEntrySwitchState);
        if (flowEntryErrorState != null) {
            ret.append(" flowEntryErrorState=" + this.flowEntryErrorState.toString());
        }
        ret.append("]");

        return ret.toString();
    }
}
