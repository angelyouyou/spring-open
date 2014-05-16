package net.onrc.onos.core.intent.runtime;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.restserver.IRestApiService;

import net.onrc.onos.core.datagrid.IDatagridService;
import net.onrc.onos.core.datagrid.IEventChannel;
import net.onrc.onos.core.datagrid.IEventChannelListener;
import net.onrc.onos.core.intent.ConstrainedShortestPathIntent;
import net.onrc.onos.core.intent.FlowEntry;
import net.onrc.onos.core.intent.Intent;
import net.onrc.onos.core.intent.Intent.IntentState;
import net.onrc.onos.core.intent.IntentOperation.Operator;
import net.onrc.onos.core.intent.IntentOperationList;
import net.onrc.onos.core.intent.MockTopology;
import net.onrc.onos.core.intent.PathIntent;
import net.onrc.onos.core.intent.PathIntentMap;
import net.onrc.onos.core.intent.ShortestPathIntent;
import net.onrc.onos.core.intent.runtime.web.IntentWebRoutable;
import net.onrc.onos.core.registry.IControllerRegistryService;
import net.onrc.onos.core.topology.DeviceEvent;
import net.onrc.onos.core.topology.ITopologyListener;
import net.onrc.onos.core.topology.ITopologyService;
import net.onrc.onos.core.topology.LinkEvent;
import net.onrc.onos.core.topology.PortEvent;
import net.onrc.onos.core.topology.SwitchEvent;
import net.onrc.onos.core.topology.Topology;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Temporary test cases for the ONS2014 demo.
 * These test cases should be modified and be moved to appropriate classes
 * (ex. PathCalcRuntimeModuleTest, PlanInstallModuleTest, etc.)
 *
 * @author Toshio Koide (t-koide@onlab.us)
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(PathCalcRuntimeModule.class)
public class UseCaseTest {
    private Topology topology;
    private FloodlightModuleContext modContext;
    private IDatagridService datagridService;
    private ITopologyService topologyService;
    private IControllerRegistryService controllerRegistryService;
    private PersistIntent persistIntent;
    private IRestApiService restApi;
    private IEventChannel<Long, IntentOperationList> intentOperationChannel;
    private IEventChannel<Long, IntentStateList> intentStateChannel;

    private static final Long LOCAL_PORT = 0xFFFEL;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        MockTopology mockTopology = new MockTopology();
        mockTopology.createSampleTopology1();
        this.topology = mockTopology;

        datagridService = createMock(IDatagridService.class);
        topologyService = createMock(ITopologyService.class);
        controllerRegistryService = createMock(IControllerRegistryService.class);
        modContext = createMock(FloodlightModuleContext.class);
        intentOperationChannel = createMock(IEventChannel.class);
        intentStateChannel = createMock(IEventChannel.class);
        persistIntent = PowerMock.createMock(PersistIntent.class);
        restApi = createMock(IRestApiService.class);

        PowerMock.expectNew(PersistIntent.class,
                anyObject(IControllerRegistryService.class)).andReturn(persistIntent);

        expect(modContext.getServiceImpl(IDatagridService.class))
                .andReturn(datagridService).once();
        expect(modContext.getServiceImpl(ITopologyService.class))
                .andReturn(topologyService).once();
        expect(modContext.getServiceImpl(IControllerRegistryService.class))
                .andReturn(controllerRegistryService).once();
        expect(persistIntent.getKey()).andReturn(1L).anyTimes();
        expect(persistIntent.persistIfLeader(eq(1L),
                anyObject(IntentOperationList.class))).andReturn(true).anyTimes();
        expect(modContext.getServiceImpl(IRestApiService.class))
                .andReturn(restApi).once();

        expect(topologyService.getTopology()).andReturn(mockTopology).anyTimes();
        topologyService.registerTopologyListener(anyObject(ITopologyListener.class));
        expectLastCall();

        expect(datagridService.createChannel("onos.pathintent", Long.class, IntentOperationList.class))
                .andReturn(intentOperationChannel).once();

        expect(datagridService.addListener(
                eq("onos.pathintent_state"),
                anyObject(IEventChannelListener.class),
                eq(Long.class),
                eq(IntentStateList.class)))
                .andReturn(intentStateChannel).once();
        restApi.addRestletRoutable(anyObject(IntentWebRoutable.class));

        replay(datagridService);
        replay(topologyService);
        replay(modContext);
        replay(controllerRegistryService);
        PowerMock.replay(persistIntent, PersistIntent.class);
        replay(restApi);
    }

    @After
    public void tearDown() {
        verify(datagridService);
        verify(topologyService);
        verify(modContext);
        verify(controllerRegistryService);
        PowerMock.verify(persistIntent, PersistIntent.class);
        verify(restApi);
    }

    private void showResult(PathIntentMap intents) {
        for (Intent intent : intents.getAllIntents()) {
            PathIntent pathIntent = (PathIntent) intent;
            System.out.println("Path intent:" + pathIntent);
            System.out.println("Parent intent: " + pathIntent.getParentIntent().toString());
        }
    }

    @Test
    public void createShortestPaths() throws FloodlightModuleException {
        // create shortest path intents
        IntentOperationList opList = new IntentOperationList();
        opList.add(Operator.ADD, new ShortestPathIntent("1", 1L, 12L, LOCAL_PORT, 2L, 21L, LOCAL_PORT));
        opList.add(Operator.ADD, new ShortestPathIntent("2", 1L, 14L, LOCAL_PORT, 4L, 41L, LOCAL_PORT));
        opList.add(Operator.ADD, new ShortestPathIntent("3", 2L, 23L, LOCAL_PORT, 3L, 32L, LOCAL_PORT));

        // compile high-level intent operations into low-level intent operations (calculate paths)
        PathCalcRuntimeModule runtime1 = new PathCalcRuntimeModule();
        runtime1.init(modContext);
        runtime1.startUp(modContext);
        IntentOperationList pathIntentOpList = runtime1.executeIntentOperations(opList);

        // compile low-level intents into flow entry installation plan
        PlanCalcRuntime runtime2 = new PlanCalcRuntime();
        List<Set<FlowEntry>> plan = runtime2.computePlan(pathIntentOpList);

        // show results
        showResult((PathIntentMap) runtime1.getPathIntents());
        System.out.println(plan);
    }

    @Test
    public void createConstrainedShortestPaths() throws FloodlightModuleException {
        // create constrained shortest path intents
        IntentOperationList opList = new IntentOperationList();
        opList.add(Operator.ADD, new ConstrainedShortestPathIntent("1", 1L, 12L, LOCAL_PORT, 2L, 21L, LOCAL_PORT, 400.0));
        opList.add(Operator.ADD, new ConstrainedShortestPathIntent("2", 1L, 14L, LOCAL_PORT, 4L, 41L, LOCAL_PORT, 400.0));
        opList.add(Operator.ADD, new ConstrainedShortestPathIntent("3", 2L, 24L, LOCAL_PORT, 4L, 42L, LOCAL_PORT, 400.0));
        opList.add(Operator.ADD, new ConstrainedShortestPathIntent("4", 2L, 23L, LOCAL_PORT, 3L, 32L, LOCAL_PORT, 400.0));
        opList.add(Operator.ADD, new ConstrainedShortestPathIntent("5", 3L, 34L, LOCAL_PORT, 4L, 43L, LOCAL_PORT, 400.0));

        // compile high-level intent operations into low-level intent operations (calculate paths)
        PathCalcRuntimeModule runtime1 = new PathCalcRuntimeModule();
        runtime1.init(modContext);
        runtime1.startUp(modContext);
        IntentOperationList pathIntentOpList = runtime1.executeIntentOperations(opList);

        // compile low-level intents into flow entry installation plan
        PlanCalcRuntime runtime2 = new PlanCalcRuntime();
        List<Set<FlowEntry>> plan = runtime2.computePlan(pathIntentOpList);

        // show results
        showResult((PathIntentMap) runtime1.getPathIntents());
        System.out.println(plan);
    }

    @Test
    public void createMixedShortestPaths() throws FloodlightModuleException {
        // create constrained & best effort shortest path intents
        IntentOperationList opList = new IntentOperationList();
        opList.add(Operator.ADD, new ConstrainedShortestPathIntent("1", 1L, 12L, LOCAL_PORT, 2L, 21L, LOCAL_PORT, 400.0));
        opList.add(Operator.ADD, new ConstrainedShortestPathIntent("2", 1L, 14L, LOCAL_PORT, 4L, 41L, LOCAL_PORT, 400.0));
        opList.add(Operator.ADD, new ShortestPathIntent("3", 2L, 24L, LOCAL_PORT, 4L, 42L, LOCAL_PORT));
        opList.add(Operator.ADD, new ShortestPathIntent("4", 2L, 23L, LOCAL_PORT, 3L, 32L, LOCAL_PORT));
        opList.add(Operator.ADD, new ConstrainedShortestPathIntent("5", 3L, 34L, LOCAL_PORT, 4L, 43L, LOCAL_PORT, 400.0));

        // compile high-level intent operations into low-level intent operations (calculate paths)
        PathCalcRuntimeModule runtime1 = new PathCalcRuntimeModule();
        runtime1.init(modContext);
        runtime1.startUp(modContext);
        IntentOperationList pathIntentOpList = runtime1.executeIntentOperations(opList);

        // compile low-level intents into flow entry installation plan
        PlanCalcRuntime runtime2 = new PlanCalcRuntime();
        List<Set<FlowEntry>> plan = runtime2.computePlan(pathIntentOpList);

        // show results
        showResult((PathIntentMap) runtime1.getPathIntents());
        System.out.println(plan);
    }

    @Test
    public void rerouteShortestPaths() throws FloodlightModuleException {
        List<SwitchEvent> addedSwitchEvents = new LinkedList<>();
        List<SwitchEvent> removedSwitchEvents = new LinkedList<>();
        List<PortEvent> addedPortEvents = new LinkedList<>();
        List<PortEvent> removedPortEvents = new LinkedList<>();
        List<LinkEvent> addedLinkEvents = new LinkedList<>();
        List<LinkEvent> removedLinkEvents = new LinkedList<>();
        List<DeviceEvent> addedDeviceEvents = new LinkedList<>();
        List<DeviceEvent> removedDeviceEvents = new LinkedList<>();

        // create shortest path intents
        IntentOperationList opList = new IntentOperationList();
        opList.add(Operator.ADD, new ShortestPathIntent("1", 1L, 12L, LOCAL_PORT, 2L, 21L, LOCAL_PORT));
        opList.add(Operator.ADD, new ShortestPathIntent("2", 1L, 14L, LOCAL_PORT, 4L, 41L, LOCAL_PORT));
        opList.add(Operator.ADD, new ShortestPathIntent("3", 2L, 23L, LOCAL_PORT, 3L, 32L, LOCAL_PORT));

        // compile high-level intent operations into low-level intent operations (calculate paths)
        PathCalcRuntimeModule runtime1 = new PathCalcRuntimeModule();
        runtime1.init(modContext);
        runtime1.startUp(modContext);
        IntentOperationList pathIntentOpList = runtime1.executeIntentOperations(opList);

        // compile low-level intents into flow entry installation plan
        PlanCalcRuntime runtime2 = new PlanCalcRuntime();
        List<Set<FlowEntry>> plan = runtime2.computePlan(pathIntentOpList);

        // show results step1
        showResult((PathIntentMap) runtime1.getPathIntents());
        System.out.println(plan);

        // TODO this state changes should be triggered by notification of plan module
        IntentStateList states = new IntentStateList();
        states.put("1", IntentState.INST_ACK);
        states.put("2", IntentState.INST_ACK);
        states.put("3", IntentState.INST_ACK);
        runtime1.getHighLevelIntents().changeStates(states);
        states.clear();
        states.put("1___0", IntentState.INST_ACK);
        states.put("2___0", IntentState.INST_ACK);
        states.put("3___0", IntentState.INST_ACK);
        runtime1.getPathIntents().changeStates(states);

        // link down
        ((MockTopology) topology).removeLink(1L, 12L, 2L, 21L); // This link is used by the intent "1"
        ((MockTopology) topology).removeLink(2L, 21L, 1L, 12L);
        LinkEvent linkEvent1 = new LinkEvent(1L, 12L, 2L, 21L);
        LinkEvent linkEvent2 = new LinkEvent(2L, 21L, 1L, 12L);
        removedLinkEvents.clear();
        removedLinkEvents.add(linkEvent1);
        removedLinkEvents.add(linkEvent2);
        runtime1.topologyEvents(
                addedSwitchEvents,
                removedSwitchEvents,
                addedPortEvents,
                removedPortEvents,
                addedLinkEvents,
                removedLinkEvents,
                addedDeviceEvents,
                removedDeviceEvents);
        System.out.println("Link goes down.");

        // show results step2
        showResult((PathIntentMap) runtime1.getPathIntents());
        // TODO: show results of plan computation
    }


    @Test
    public void createAndRemoveShortestPaths() throws FloodlightModuleException {
        // create shortest path intents
        IntentOperationList opList = new IntentOperationList();
        opList.add(Operator.ADD, new ShortestPathIntent("1", 1L, 12L, LOCAL_PORT, 2L, 21L, LOCAL_PORT));
        opList.add(Operator.ADD, new ShortestPathIntent("2", 1L, 14L, LOCAL_PORT, 4L, 41L, LOCAL_PORT));
        opList.add(Operator.ADD, new ShortestPathIntent("3", 2L, 23L, LOCAL_PORT, 3L, 32L, LOCAL_PORT));

        // compile high-level intent operations into low-level intent operations (calculate paths)
        PathCalcRuntimeModule runtime1 = new PathCalcRuntimeModule();
        runtime1.init(modContext);
        runtime1.startUp(modContext);
        IntentOperationList pathIntentOpList = runtime1.executeIntentOperations(opList);

        // compile low-level intents into flow entry installation plan
        PlanCalcRuntime runtime2 = new PlanCalcRuntime();
        List<Set<FlowEntry>> plan = runtime2.computePlan(pathIntentOpList);

        // show results
        showResult((PathIntentMap) runtime1.getPathIntents());
        System.out.println(plan);

        // create remove operations
        opList.clear();
        opList.add(Operator.REMOVE, new Intent("1"));
        opList.add(Operator.REMOVE, new Intent("2"));

        // compile
        runtime1.executeIntentOperations(opList);

        // show results
        showResult((PathIntentMap) runtime1.getPathIntents());
        System.out.println(plan);
    }

}
