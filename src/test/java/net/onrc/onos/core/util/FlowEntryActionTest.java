package net.onrc.onos.core.util;

import static org.junit.Assert.assertEquals;

import net.floodlightcontroller.util.MACAddress;
import net.onrc.onos.core.util.FlowEntryAction.ActionEnqueue;
import net.onrc.onos.core.util.FlowEntryAction.ActionOutput;
import net.onrc.onos.core.util.FlowEntryAction.ActionSetEthernetAddr;
import net.onrc.onos.core.util.FlowEntryAction.ActionSetIPv4Addr;
import net.onrc.onos.core.util.FlowEntryAction.ActionSetIpToS;
import net.onrc.onos.core.util.FlowEntryAction.ActionSetTcpUdpPort;
import net.onrc.onos.core.util.FlowEntryAction.ActionSetVlanId;
import net.onrc.onos.core.util.FlowEntryAction.ActionSetVlanPriority;
import net.onrc.onos.core.util.FlowEntryAction.ActionStripVlan;

import org.junit.Test;

public class FlowEntryActionTest {

    @Test
    public void testSetActionOutputActionOutput() {
        FlowEntryAction act = new FlowEntryAction();
        ActionOutput actout = new FlowEntryAction.ActionOutput(new PortNumber((short) 42));
        act.setActionOutput(actout);

        assertEquals("action output", FlowEntryAction.ActionValues.ACTION_OUTPUT, act.actionType());
        assertEquals("actionOutput port should be the same", actout.port(), act.actionOutput().port());
        assertEquals("actionOutput maxlen should be the same", actout.maxLen(), act.actionOutput().maxLen());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionOutputPort() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionOutput(new PortNumber((short) 42));

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionOutputToController() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionOutputToController((short) 0);

        FlowEntryAction actCopy = new FlowEntryAction();
        actCopy.setActionOutput(new PortNumber(PortNumber.PortValues.PORT_CONTROLLER));

        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetVlanIdActionSetVlanId() {
        FlowEntryAction act = new FlowEntryAction();
        ActionSetVlanId actVlan = new FlowEntryAction.ActionSetVlanId((short) 42);
        act.setActionSetVlanId(actVlan);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_SET_VLAN_VID, act.actionType());
        assertEquals("vlanid should be the same", actVlan.vlanId(), act.actionSetVlanId().vlanId());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetVlanIdShort() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionSetVlanId((short) 42);

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetVlanPriorityActionSetVlanPriority() {
        FlowEntryAction act = new FlowEntryAction();
        ActionSetVlanPriority actVlan = new FlowEntryAction.ActionSetVlanPriority((byte) 42);
        act.setActionSetVlanPriority(actVlan);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_SET_VLAN_PCP, act.actionType());
        assertEquals("vlan priority should be the same", actVlan.vlanPriority(),
                act.actionSetVlanPriority().vlanPriority());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetVlanPriorityByte() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionSetVlanPriority((byte) 42);

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionStripVlanActionStripVlan() {
        FlowEntryAction act = new FlowEntryAction();
        ActionStripVlan actVlan = new FlowEntryAction.ActionStripVlan();
        act.setActionStripVlan(actVlan);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_STRIP_VLAN, act.actionType());
        assertEquals("vlanid should be the same", actVlan.stripVlan(), act.actionStripVlan().stripVlan());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionStripVlanBoolean() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionStripVlan(true);

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetEthernetSrcAddrActionSetEthernetAddr() {
        FlowEntryAction act = new FlowEntryAction();
        byte[] mac = {1, 2, 3, 4, 5, 6};
        ActionSetEthernetAddr setEth = new FlowEntryAction.ActionSetEthernetAddr(new MACAddress(mac));
        act.setActionSetEthernetSrcAddr(setEth);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_SET_DL_SRC, act.actionType());
        assertEquals("addr should be the same", setEth.addr(), act.actionSetEthernetSrcAddr().addr());


        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetEthernetSrcAddrMACAddress() {
        FlowEntryAction act = new FlowEntryAction();
        byte[] mac = {1, 2, 3, 4, 5, 6};
        act.setActionSetEthernetSrcAddr(new MACAddress(mac));

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetEthernetDstAddrActionSetEthernetAddr() {
        FlowEntryAction act = new FlowEntryAction();
        byte[] mac = {1, 2, 3, 4, 5, 6};
        ActionSetEthernetAddr setEth = new FlowEntryAction.ActionSetEthernetAddr(new MACAddress(mac));
        act.setActionSetEthernetDstAddr(setEth);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_SET_DL_DST, act.actionType());
        assertEquals("addr should be the same", setEth.addr(), act.actionSetEthernetDstAddr().addr());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetEthernetDstAddrMACAddress() {
        FlowEntryAction act = new FlowEntryAction();
        byte[] mac = {1, 2, 3, 4, 5, 6};
        act.setActionSetEthernetDstAddr(new MACAddress(mac));

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetIPv4SrcAddrActionSetIPv4Addr() {
        FlowEntryAction act = new FlowEntryAction();
        ActionSetIPv4Addr setIp = new FlowEntryAction.ActionSetIPv4Addr(new IPv4("127.0.0.1"));
        act.setActionSetIPv4SrcAddr(setIp);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_SET_NW_SRC, act.actionType());
        assertEquals("addr should be the same", setIp.addr(), act.actionSetIPv4SrcAddr().addr());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());


        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetIPv4SrcAddrIPv4() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionSetIPv4SrcAddr(new IPv4("127.0.0.1"));

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetIPv4DstAddrActionSetIPv4Addr() {
        FlowEntryAction act = new FlowEntryAction();
        ActionSetIPv4Addr setIp = new FlowEntryAction.ActionSetIPv4Addr(new IPv4("127.0.0.1"));
        act.setActionSetIPv4DstAddr(setIp);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_SET_NW_DST, act.actionType());
        assertEquals("addr should be the same", setIp.addr(), act.actionSetIPv4DstAddr().addr());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetIPv4DstAddrIPv4() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionSetIPv4DstAddr(new IPv4("127.0.0.1"));

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetIpToSActionSetIpToS() {
        FlowEntryAction act = new FlowEntryAction();
        ActionSetIpToS setIpTos = new FlowEntryAction.ActionSetIpToS((byte) 42);
        act.setActionSetIpToS(setIpTos);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_SET_NW_TOS, act.actionType());
        assertEquals("tos should be the same", setIpTos.ipToS(), act.actionSetIpToS().ipToS());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetIpToSByte() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionSetIpToS((byte) 1);

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetTcpUdpSrcPortActionSetTcpUdpPort() {
        FlowEntryAction act = new FlowEntryAction();
        ActionSetTcpUdpPort setPorts = new FlowEntryAction.ActionSetTcpUdpPort((short) 42);
        act.setActionSetTcpUdpSrcPort(setPorts);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_SET_TP_SRC, act.actionType());
        assertEquals("port should be the same", setPorts.port(), act.actionSetTcpUdpSrcPort().port());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetTcpUdpSrcPortShort() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionSetTcpUdpSrcPort((short) 1);

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetTcpUdpDstPortActionSetTcpUdpPort() {
        FlowEntryAction act = new FlowEntryAction();
        ActionSetTcpUdpPort setPorts = new FlowEntryAction.ActionSetTcpUdpPort((short) 42);
        act.setActionSetTcpUdpDstPort(setPorts);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_SET_TP_DST, act.actionType());
        assertEquals("port should be the same", setPorts.port(), act.actionSetTcpUdpDstPort().port());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionSetTcpUdpDstPortShort() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionSetTcpUdpDstPort((short) 1);

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionEnqueueActionEnqueue() {
        FlowEntryAction act = new FlowEntryAction();
        ActionEnqueue enq = new FlowEntryAction.ActionEnqueue(new PortNumber((short) 42), 1);
        act.setActionEnqueue(enq);

        assertEquals("action type", FlowEntryAction.ActionValues.ACTION_ENQUEUE, act.actionType());
        assertEquals("port should be the same", enq.port(), act.actionEnqueue().port());
        assertEquals("queue id should be the same", enq.queueId(), act.actionEnqueue().queueId());

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

    @Test
    public void testSetActionEnqueuePortInt() {
        FlowEntryAction act = new FlowEntryAction();
        act.setActionEnqueue(new PortNumber((short) 42), 1);

        FlowEntryAction actCopy = new FlowEntryAction(act);
        FlowEntryAction actCopy2 = new FlowEntryAction(act.toString());

        assertEquals("toString must match between copies", act.toString(),
                actCopy.toString());
        assertEquals("toString must match between copies", act.toString(),
                actCopy2.toString());
    }

}
