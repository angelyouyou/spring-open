package net.onrc.onos.core.flowprogrammer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.OFMessageDamper;
import net.onrc.onos.core.util.Dpid;
import net.onrc.onos.core.util.FlowEntry;
import net.onrc.onos.core.util.FlowEntryActions;
import net.onrc.onos.core.util.FlowEntryErrorState;
import net.onrc.onos.core.util.FlowEntryId;
import net.onrc.onos.core.util.FlowEntryMatch;
import net.onrc.onos.core.util.FlowEntryUserState;
import net.onrc.onos.core.util.FlowId;
import net.onrc.onos.core.util.Port;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Test;
import org.openflow.protocol.OFBarrierRequest;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.factory.BasicFactory;

public class FlowPusherTest {
    private FlowPusher pusher;
    private FloodlightContext context;
    private FloodlightModuleContext modContext;
    private BasicFactory factory;
    private OFMessageDamper damper;
    private IFloodlightProviderService flProviderService;
    private IThreadPoolService threadPoolService;

    /**
     * Test single OFMessage is correctly sent to single switch via MessageDamper.
     */
    @Test
    public void testAddMessage() {
        beginInitMock();

        OFMessage msg = EasyMock.createMock(OFMessage.class);
        EasyMock.expect(msg.getXid()).andReturn(1).anyTimes();
        EasyMock.expect(msg.getLength()).andReturn((short) 100).anyTimes();
        EasyMock.replay(msg);

        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        EasyMock.expect(sw.getId()).andReturn((long) 1).anyTimes();
        sw.flush();
        EasyMock.expectLastCall().once();
        EasyMock.replay(sw);

        try {
            EasyMock.expect(damper.write(EasyMock.eq(sw), EasyMock.eq(msg), EasyMock.eq(context)))
                    .andReturn(true).once();
        } catch (IOException e1) {
            fail("Failed in OFMessageDamper#write()");
        }

        endInitMock();
        initPusher(1);

        boolean addResult = pusher.add(sw, msg);
        assertTrue(addResult);

        try {
            // wait until message is processed.
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("Failed in Thread.sleep()");
        }
        EasyMock.verify(msg);
        EasyMock.verify(sw);
        verifyAll();

        pusher.stop();
    }

    /**
     * Test bunch of OFMessages are correctly sent to single switch via MessageDamper.
     */
    @Test
    public void testMassiveAddMessage() {
        final int NUM_MSG = 10000;

        beginInitMock();

        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        EasyMock.expect(sw.getId()).andReturn((long) 1).anyTimes();
        sw.flush();
        EasyMock.expectLastCall().atLeastOnce();
        EasyMock.replay(sw);

        List<OFMessage> messages = new ArrayList<OFMessage>();

        for (int i = 0; i < NUM_MSG; ++i) {
            OFMessage msg = EasyMock.createMock(OFMessage.class);
            EasyMock.expect(msg.getXid()).andReturn(i).anyTimes();
            EasyMock.expect(msg.getLength()).andReturn((short) 100).anyTimes();
            EasyMock.replay(msg);
            messages.add(msg);

            try {
                EasyMock.expect(damper.write(EasyMock.eq(sw), EasyMock.eq(msg), EasyMock.eq(context)))
                        .andReturn(true).once();
            } catch (IOException e1) {
                fail("Failed in OFMessageDamper#write()");
            }
        }

        endInitMock();
        initPusher(1);

        for (OFMessage msg : messages) {
            boolean addResult = pusher.add(sw, msg);
            assertTrue(addResult);
        }

        try {
            // wait until message is processed.
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            fail("Failed in Thread.sleep()");
        }

        for (OFMessage msg : messages) {
            EasyMock.verify(msg);
        }
        EasyMock.verify(sw);
        verifyAll();

        pusher.stop();
    }

    /**
     * Test bunch of OFMessages are correctly sent to multiple switches with single threads.
     */
    @Test
    public void testMultiSwitchAddMessage() {
        final int NUM_SWITCH = 10;
        final int NUM_MSG = 100;    // messages per thread

        beginInitMock();

        Map<IOFSwitch, List<OFMessage>> swMap = new HashMap<IOFSwitch, List<OFMessage>>();
        for (int i = 0; i < NUM_SWITCH; ++i) {
            IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
            EasyMock.expect(sw.getId()).andReturn((long) i).anyTimes();
            sw.flush();
            EasyMock.expectLastCall().atLeastOnce();
            EasyMock.replay(sw);

            List<OFMessage> messages = new ArrayList<OFMessage>();

            for (int j = 0; j < NUM_MSG; ++j) {
                OFMessage msg = EasyMock.createMock(OFMessage.class);
                EasyMock.expect(msg.getXid()).andReturn(j).anyTimes();
                EasyMock.expect(msg.getLength()).andReturn((short) 100).anyTimes();
                EasyMock.replay(msg);
                messages.add(msg);

                try {
                    EasyMock.expect(damper.write(EasyMock.eq(sw), EasyMock.eq(msg), EasyMock.eq(context)))
                            .andReturn(true).once();
                } catch (IOException e1) {
                    fail("Failed in OFMessageDamper#write()");
                }
            }
            swMap.put(sw, messages);
        }

        endInitMock();
        initPusher(1);

        for (IOFSwitch sw : swMap.keySet()) {
            for (OFMessage msg : swMap.get(sw)) {
                boolean addResult = pusher.add(sw, msg);
                assertTrue(addResult);
            }
        }

        try {
            // wait until message is processed.
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("Failed in Thread.sleep()");
        }

        for (IOFSwitch sw : swMap.keySet()) {
            for (OFMessage msg : swMap.get(sw)) {
                EasyMock.verify(msg);
            }

            EasyMock.verify(sw);
        }
        verifyAll();

        pusher.stop();
    }

    /**
     * Test bunch of OFMessages are correctly sent to multiple switches using multiple threads.
     */
    @Test
    public void testMultiThreadedAddMessage() {
        final int NUM_THREAD = 10;
        final int NUM_MSG = 100;    // messages per thread

        beginInitMock();

        Map<IOFSwitch, List<OFMessage>> swMap = new HashMap<IOFSwitch, List<OFMessage>>();
        for (int i = 0; i < NUM_THREAD; ++i) {
            IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
            EasyMock.expect(sw.getId()).andReturn((long) i).anyTimes();
            sw.flush();
            EasyMock.expectLastCall().atLeastOnce();
            EasyMock.replay(sw);

            List<OFMessage> messages = new ArrayList<OFMessage>();

            for (int j = 0; j < NUM_MSG; ++j) {
                OFMessage msg = EasyMock.createMock(OFMessage.class);
                EasyMock.expect(msg.getXid()).andReturn(j).anyTimes();
                EasyMock.expect(msg.getLength()).andReturn((short) 100).anyTimes();
                EasyMock.replay(msg);
                messages.add(msg);

                try {
                    EasyMock.expect(damper.write(EasyMock.eq(sw), EasyMock.eq(msg), EasyMock.eq(context)))
                            .andReturn(true).once();
                } catch (IOException e1) {
                    fail("Failed in OFMessageDamper#write()");
                }
            }
            swMap.put(sw, messages);
        }

        endInitMock();
        initPusher(NUM_THREAD);

        for (IOFSwitch sw : swMap.keySet()) {
            for (OFMessage msg : swMap.get(sw)) {
                boolean addResult = pusher.add(sw, msg);
                assertTrue(addResult);
            }
        }

        try {
            // wait until message is processed.
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("Failed in Thread.sleep()");
        }

        for (IOFSwitch sw : swMap.keySet()) {
            for (OFMessage msg : swMap.get(sw)) {
                EasyMock.verify(msg);
            }

            EasyMock.verify(sw);
        }
        verifyAll();

        pusher.stop();
    }

    private long barrierTime = 0;

    /**
     * Test rate limitation of messages works correctly.
     */
    @Test
    public void testRateLimitedAddMessage() {
        final long LIMIT_RATE = 100; // [bytes/ms]
        final int NUM_MSG = 1000;

        // Accuracy of FlowPusher's rate calculation can't be measured by unit test
        // because switch doesn't return BARRIER_REPLY.
        // In unit test we use approximate way to measure rate. This value is
        // acceptable margin of measured rate.
        final double ACCEPTABLE_RATE = LIMIT_RATE * 1.2;

        beginInitMock();

        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        EasyMock.expect(sw.getId()).andReturn((long) 1).anyTimes();
        sw.flush();
        EasyMock.expectLastCall().atLeastOnce();
        prepareBarrier(sw);
        EasyMock.replay(sw);

        List<OFMessage> messages = new ArrayList<OFMessage>();

        for (int i = 0; i < NUM_MSG; ++i) {
            OFMessage msg = EasyMock.createMock(OFMessage.class);
            EasyMock.expect(msg.getXid()).andReturn(1).anyTimes();
            EasyMock.expect(msg.getLength()).andReturn((short) 100).anyTimes();
            EasyMock.expect(msg.getLengthU()).andReturn(100).anyTimes();
            EasyMock.replay(msg);
            messages.add(msg);

            try {
                EasyMock.expect(damper.write(EasyMock.eq(sw), EasyMock.eq(msg), EasyMock.eq(context)))
                        .andReturn(true).once();
            } catch (IOException e) {
                fail("Failed in OFMessageDamper#write()");
            }
        }

        try {
            EasyMock.expect(damper.write(EasyMock.eq(sw), (OFMessage) EasyMock.anyObject(), EasyMock.eq(context)))
                    .andAnswer(new IAnswer<Boolean>() {
                        @Override
                        public Boolean answer() throws Throwable {
                            OFMessage msg = (OFMessage) EasyMock.getCurrentArguments()[1];
                            if (msg.getType() == OFType.BARRIER_REQUEST) {
                                barrierTime = System.currentTimeMillis();
                            }
                            return true;
                        }
                    }).once();
        } catch (IOException e1) {
            fail("Failed in OFMessageDamper#write()");
        }

        endInitMock();
        initPusher(1);

        pusher.createQueue(sw);
        pusher.setRate(sw, LIMIT_RATE);

        long beginTime = System.currentTimeMillis();
        for (OFMessage msg : messages) {
            boolean addResult = pusher.add(sw, msg);
            assertTrue(addResult);
        }

        pusher.barrierAsync(sw);

        try {
            do {
                Thread.sleep(1000);
            } while (barrierTime == 0);
        } catch (InterruptedException e) {
            fail("Failed to sleep");
        }

        double measuredRate = NUM_MSG * 100 / (barrierTime - beginTime);
        assertTrue(measuredRate < ACCEPTABLE_RATE);

        for (OFMessage msg : messages) {
            EasyMock.verify(msg);
        }
        EasyMock.verify(sw);
        verifyAll();

        pusher.stop();
    }

    /**
     * Test barrier message is correctly sent to a switch.
     */
    @Test
    public void testBarrierMessage() {
        beginInitMock();

        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        EasyMock.expect(sw.getId()).andReturn((long) 1).anyTimes();
        sw.flush();
        EasyMock.expectLastCall().atLeastOnce();
        prepareBarrier(sw);
        EasyMock.replay(sw);

        try {
            EasyMock.expect(damper.write(EasyMock.eq(sw), (OFMessage) EasyMock.anyObject(), EasyMock.eq(context)))
                    .andReturn(true).once();
        } catch (IOException e1) {
            fail("Failed in OFMessageDamper#write()");
        }

        endInitMock();
        initPusher(1);

        OFBarrierReplyFuture future = pusher.barrierAsync(sw);

        assertNotNull(future);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("Failed to sleep");
        }

        verifyAll();

        pusher.stop();
    }

    static final int XID_TO_VERIFY = 100;
    static final long DPID_TO_VERIFY = 10;

    /**
     * Test FlowObject is correctly converted to message and is sent to a switch.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testAddFlow() {
        // instantiate required objects
        FlowEntry flowEntry1 = new FlowEntry();
        flowEntry1.setDpid(new Dpid(DPID_TO_VERIFY));
        flowEntry1.setFlowId(new FlowId(1));
        flowEntry1.setInPort(new Port((short) 1));
        flowEntry1.setOutPort(new Port((short) 11));
        flowEntry1.setFlowEntryId(new FlowEntryId(1));
        flowEntry1.setFlowEntryMatch(new FlowEntryMatch());
        flowEntry1.setFlowEntryActions(new FlowEntryActions());
        flowEntry1.setFlowEntryErrorState(new FlowEntryErrorState());
        flowEntry1.setFlowEntryUserState(FlowEntryUserState.FE_USER_ADD);

        beginInitMock();

        OFFlowMod msg = EasyMock.createMock(OFFlowMod.class);
        EasyMock.expect(msg.setIdleTimeout(EasyMock.anyShort())).andReturn(msg);
        EasyMock.expect(msg.setHardTimeout(EasyMock.anyShort())).andReturn(msg);
        EasyMock.expect(msg.setPriority(EasyMock.anyShort())).andReturn(msg);
        EasyMock.expect(msg.setBufferId(EasyMock.anyInt())).andReturn(msg);
        EasyMock.expect(msg.setCookie(EasyMock.anyLong())).andReturn(msg);
        EasyMock.expect(msg.setCommand(EasyMock.anyShort())).andReturn(msg);
        EasyMock.expect(msg.setMatch(EasyMock.anyObject(OFMatch.class))).andReturn(msg);
        EasyMock.expect(msg.setActions((List<OFAction>) EasyMock.anyObject())).andReturn(msg);
        EasyMock.expect(msg.setLengthU(EasyMock.anyShort())).andReturn(msg);
        EasyMock.expect(msg.setOutPort(EasyMock.anyShort())).andReturn(msg).atLeastOnce();
        EasyMock.expect(msg.getXid()).andReturn(XID_TO_VERIFY).anyTimes();
        EasyMock.expect(msg.getType()).andReturn(OFType.FLOW_MOD).anyTimes();
        EasyMock.expect(msg.getLength()).andReturn((short) 100).anyTimes();
        EasyMock.replay(msg);

        EasyMock.expect(factory.getMessage(EasyMock.eq(OFType.FLOW_MOD))).andReturn(msg);

        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        EasyMock.expect(sw.getId()).andReturn(DPID_TO_VERIFY).anyTimes();
        EasyMock.expect(sw.getStringId()).andReturn("1").anyTimes();
        sw.flush();
        EasyMock.expectLastCall().once();

        try {
            EasyMock.expect(damper.write(EasyMock.eq(sw), EasyMock.anyObject(OFMessage.class), EasyMock.eq(context)))
                    .andAnswer(new IAnswer<Boolean>() {
                        @Override
                        public Boolean answer() throws Throwable {
                            OFMessage msg = (OFMessage) EasyMock.getCurrentArguments()[1];
                            if (msg.getType() == OFType.FLOW_MOD) {
                                assertEquals(msg.getXid(), XID_TO_VERIFY);
                            }
                            return true;
                        }
                    }).atLeastOnce();
        } catch (IOException e1) {
            fail("Failed in OFMessageDamper#write()");
        }

        EasyMock.replay(sw);

        endInitMock();
        initPusher(1);

        pusher.pushFlowEntry(sw, flowEntry1);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("Failed to sleep");
        }

        EasyMock.verify(sw);
        verifyAll();

        pusher.stop();
    }

    private void beginInitMock() {
        context = EasyMock.createMock(FloodlightContext.class);
        modContext = EasyMock.createMock(FloodlightModuleContext.class);
        factory = EasyMock.createMock(BasicFactory.class);
        damper = EasyMock.createMock(OFMessageDamper.class);
        flProviderService = EasyMock.createMock(IFloodlightProviderService.class);
        threadPoolService = EasyMock.createMock(IThreadPoolService.class);

        EasyMock.expect(modContext.getServiceImpl(EasyMock.eq(IThreadPoolService.class)))
                .andReturn(threadPoolService).once();
        EasyMock.expect(modContext.getServiceImpl(EasyMock.eq(IFloodlightProviderService.class)))
                .andReturn(flProviderService).once();
        flProviderService.addOFMessageListener(EasyMock.eq(OFType.BARRIER_REPLY),
                (FlowPusher) EasyMock.anyObject());
        EasyMock.expectLastCall().once();

        ScheduledExecutorService executor = EasyMock.createMock(ScheduledExecutorService.class);
        EasyMock.expect(executor.schedule((Runnable) EasyMock.anyObject(), EasyMock.anyLong(),
                (TimeUnit) EasyMock.anyObject())).andReturn(null).once();
        EasyMock.replay(executor);
        EasyMock.expect(threadPoolService.getScheduledExecutor()).andReturn(executor).anyTimes();
    }

    private void endInitMock() {
        EasyMock.replay(threadPoolService);
        EasyMock.replay(flProviderService);
        EasyMock.replay(damper);
        EasyMock.replay(factory);
        EasyMock.replay(modContext);
        EasyMock.replay(context);
    }

    private void verifyAll() {
        EasyMock.verify(threadPoolService);
        EasyMock.verify(flProviderService);
        EasyMock.verify(damper);
        EasyMock.verify(factory);
        EasyMock.verify(modContext);
        EasyMock.verify(context);
    }

    private void initPusher(int numThread) {
        pusher = new FlowPusher(numThread);
        pusher.init(context, modContext, factory, damper);
        pusher.start();
    }

    private void prepareBarrier(IOFSwitch sw) {
        OFBarrierRequest req = EasyMock.createMock(OFBarrierRequest.class);
        req.setXid(EasyMock.anyInt());
        EasyMock.expectLastCall().once();
        EasyMock.expect(req.getXid()).andReturn(1).anyTimes();
        EasyMock.expect(req.getType()).andReturn(OFType.BARRIER_REQUEST).anyTimes();
        EasyMock.expect(req.getLength()).andReturn((short) 100).anyTimes();
        EasyMock.replay(req);
        EasyMock.expect(factory.getMessage(EasyMock.eq(OFType.BARRIER_REQUEST))).andReturn(req).anyTimes();
        EasyMock.expect(sw.getNextTransactionId()).andReturn(1);
    }

}
