package net.onrc.onos.core.topology;

import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.util.MACAddress;
import net.onrc.onos.core.datagrid.IDatagridService;
import net.onrc.onos.core.datagrid.IEventChannel;
import net.onrc.onos.core.datagrid.IEventChannelListener;
import net.onrc.onos.core.registry.IControllerRegistryService;
import net.onrc.onos.core.registry.RegistryException;
import net.onrc.onos.core.util.Dpid;
import net.onrc.onos.core.util.EventEntry;
import static net.onrc.onos.core.util.ImmutableClassChecker.assertThatClassIsImmutable;
import net.onrc.onos.core.util.OnosInstanceId;
import net.onrc.onos.core.util.PortNumber;
import net.onrc.onos.core.util.SwitchPort;
import net.onrc.onos.core.util.TestUtils;
import net.onrc.onos.core.util.UnitTest;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the TopologyManager class in the Topology module.
 * These test cases only check the sanity of functions in the TopologyManager.
 * Note that we do not test the eventHandler functions in the TopologyManager
 * class.
 * DatagridService, eventChannel, and controllerRegistryService are mocked out.
 */
public class TopologyManagerTest extends UnitTest {
    private TopologyPublisher theTopologyPublisher;
    private TopologyManager theTopologyManager;
    private TopologyManager.EventHandler theEventHandler;
    private TopologyListenerTest theTopologyListener =
        new TopologyListenerTest();
    private final String eventChannelName = "onos.topology";
    private IEventChannel<byte[], TopologyEvent> eventChannel;
    private IDatagridService datagridService;
    private IControllerRegistryService registryService;
    private Collection<TopologyEvent> allTopologyEvents;
    private static final OnosInstanceId ONOS_INSTANCE_ID_1 =
        new OnosInstanceId("ONOS-Instance-ID-1");
    private static final OnosInstanceId ONOS_INSTANCE_ID_2 =
        new OnosInstanceId("ONOS-Instance-ID-2");
    private static final Dpid DPID_1 = new Dpid(1);
    private static final Dpid DPID_2 = new Dpid(2);

    /**
     * Topology events listener.
     */
    private class TopologyListenerTest implements ITopologyListener {
        private TopologyEvents topologyEvents;

        @Override
        public void topologyEvents(TopologyEvents events) {
            this.topologyEvents = events;
        }

        /**
         * Clears the Topology Listener state.
         */
        public void clear() {
            this.topologyEvents = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        // Mock objects for testing
        datagridService = createNiceMock(IDatagridService.class);
        registryService = createMock(IControllerRegistryService.class);
        eventChannel = createNiceMock(IEventChannel.class);

        expect(datagridService.createChannel(
                eq(eventChannelName),
                eq(byte[].class),
                eq(TopologyEvent.class)))
                .andReturn(eventChannel).once();

        expect(datagridService.addListener(
                eq(eventChannelName),
                anyObject(IEventChannelListener.class),
                eq(byte[].class),
                eq(TopologyEvent.class)))
                .andReturn(eventChannel).once();

        // Setup the Registry Service
        expect(registryService.getOnosInstanceId()).andReturn(ONOS_INSTANCE_ID_1).anyTimes();
        expect(registryService.getControllerForSwitch(DPID_1.value()))
            .andReturn(ONOS_INSTANCE_ID_1.toString()).anyTimes();
        expect(registryService.getControllerForSwitch(DPID_2.value()))
            .andReturn(ONOS_INSTANCE_ID_2.toString()).anyTimes();
        expect(registryService.hasControl(DPID_1.value()))
            .andReturn(true).anyTimes();
        expect(registryService.hasControl(DPID_2.value()))
            .andReturn(false).anyTimes();

        allTopologyEvents = new CopyOnWriteArrayList<>();
        expect(eventChannel.getAllEntries())
            .andReturn(allTopologyEvents).anyTimes();

        replay(datagridService);
        replay(registryService);
        // replay(eventChannel);
    }

    /**
     * Setup the Topology Publisher.
     */
    private void setupTopologyPublisher() throws RegistryException {
        // Create a TopologyPublisher object for testing
        theTopologyPublisher = new TopologyPublisher();

        // Setup the registry service
        TestUtils.setField(theTopologyPublisher, "registryService",
                           registryService);

        //
        // Update the Registry Service, so the ONOS instance is the
        // Master for both switches.
        //
        reset(registryService);
        expect(registryService.getOnosInstanceId()).andReturn(ONOS_INSTANCE_ID_1).anyTimes();
        expect(registryService.getControllerForSwitch(DPID_1.value()))
            .andReturn(ONOS_INSTANCE_ID_1.toString()).anyTimes();
        expect(registryService.getControllerForSwitch(DPID_2.value()))
            .andReturn(ONOS_INSTANCE_ID_2.toString()).anyTimes();
        expect(registryService.hasControl(DPID_1.value()))
            .andReturn(true).anyTimes();
        expect(registryService.hasControl(DPID_2.value()))
            .andReturn(true).anyTimes();
        replay(registryService);

        // Setup the event channel
        TestUtils.setField(theTopologyPublisher, "eventChannel", eventChannel);
    }

    /**
     * Setup the Topology Manager.
     */
    private void setupTopologyManager() {
        // Create a TopologyManager object for testing
        theTopologyManager = new TopologyManager(registryService);

        // Replace the eventHandler to prevent the thread from starting
        TestUtils.setField(theTopologyManager, "eventHandler",
            createNiceMock(TopologyManager.EventHandler.class));
        theTopologyManager.startup(datagridService);
    }

    /**
     * Setup the Topology Manager with the Event Handler.
     */
    private void setupTopologyManagerWithEventHandler() {
        // Create a TopologyManager object for testing
        theTopologyManager = new TopologyManager(registryService);
        theTopologyManager.addListener(theTopologyListener, true);

        // Allocate the Event Handler, so we can have direct access to it
        theEventHandler = theTopologyManager.new EventHandler();
        TestUtils.setField(theTopologyManager, "eventHandler",
                           theEventHandler);

        replay(eventChannel);
        //
        // NOTE: Uncomment-out the line below if the startup() method needs
        // to be called for some of the unit tests. For now it is commented-out
        // to avoid any side effects of starting the eventHandler thread.
        //
        // theTopologyManager.startup(datagridService);
    }

    /**
     * Tests the immutability of {@link TopologyEvents}.
     */
    @Test
    public void testImmutableTopologyEvents() {
        assertThatClassIsImmutable(TopologyEvents.class);
    }

    /**
     * Tests the publishing of Add Switch Mastership Event.
     */
    @Test
    public void testPublishAddSwitchMastershipEvent() throws RegistryException {
        setupTopologyPublisher();

        // Mock the eventChannel functions
        eventChannel.addEntry(anyObject(byte[].class),
                              anyObject(TopologyEvent.class));
        expectLastCall().times(1, 1);          // 1 event
        replay(eventChannel);

        // Generate the Switch Mastership event
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_1, ONOS_INSTANCE_ID_1, role);

        // Call the TopologyPublisher function for adding the event
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchMastershipEvent",
                             MastershipData.class, mastershipData);

        // Verify the function calls
        verify(eventChannel);
    }

    /**
     * Tests the publishing of Remove Switch Mastership Event.
     */
    @Test
    public void testPublishRemoveSwitchMastershipEvent() throws RegistryException {
        setupTopologyPublisher();

        // Mock the eventChannel functions
        eventChannel.removeEntry(anyObject(byte[].class));
        expectLastCall().times(1, 1);          // 1 event
        replay(eventChannel);

        // Generate the Switch Mastership Event
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_1, ONOS_INSTANCE_ID_1, role);

        // Call the TopologyPublisher function for adding the event
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchMastershipEvent",
                             MastershipData.class, mastershipData);

        // Call the TopologyPublisher function for removing the event
        TestUtils.callMethod(theTopologyPublisher,
                             "publishRemoveSwitchMastershipEvent",
                             MastershipData.class, mastershipData);

        // Verify the function calls
        verify(eventChannel);
    }

    /**
     * Tests the publishing of Add Switch and Port Events.
     */
    @Test
    public void testPublishAddSwitchAndPortEvent() throws RegistryException {
        setupTopologyPublisher();

        // Mock the eventChannel functions
        eventChannel.addEntry(anyObject(byte[].class),
                              anyObject(TopologyEvent.class));
        expectLastCall().times(3, 3);           // (1 Switch + 1 Port), 1 Port
        replay(eventChannel);

        // Mock Switch has one Port
        PortNumber portNumber = PortNumber.uint32(1);

        // Generate the Switch Event along with a Port Event
        SwitchData switchData = new SwitchData(DPID_1);
        Collection<PortData> portDataEntries = new ArrayList<PortData>();
        portDataEntries.add(new PortData(DPID_1, portNumber));

        // Call the TopologyPublisher function for adding a Switch
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchEvent",
                             new Class<?>[] {SwitchData.class,
                                     Collection.class},
                             switchData, portDataEntries);
        // Call the TopologyPublisher function for adding a Port
        for (PortData portData : portDataEntries) {
            TestUtils.callMethod(theTopologyPublisher,
                                 "publishAddPortEvent",
                                 PortData.class, portData);
        }

        // Verify the function calls
        verify(eventChannel);

    }

    /**
     * Tests the publishing of Remove Switch and Port Events.
     */
    @Test
    public void testPublishRemoveSwitchAndPortEvent() throws RegistryException {
        setupTopologyPublisher();

        // Mock the eventChannel functions
        eventChannel.removeEntry(anyObject(byte[].class));
        expectLastCall().times(2, 2);          // 1 Switch, 1 Port
        replay(eventChannel);

        PortNumber portNumber = PortNumber.uint32(1);

        // Generate the Switch Event along with a Port Event
        SwitchData switchData = new SwitchData(DPID_1);
        Collection<PortData> portDataEntries = new ArrayList<PortData>();
        portDataEntries.add(new PortData(DPID_1, portNumber));

        // Call the TopologyPublisher function for adding a Switch and Ports
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchEvent",
                             new Class<?>[] {SwitchData.class,
                                     Collection.class},
                             switchData, portDataEntries);

        // Call the TopologyPublisher function for removing a Port
        for (PortData portData : portDataEntries) {
            TestUtils.callMethod(theTopologyPublisher,
                                 "publishRemovePortEvent",
                                 PortData.class, portData);
        }
        // Call the TopologyPublisher function for removing a Switch
        TestUtils.callMethod(theTopologyPublisher,
                             "publishRemoveSwitchEvent",
                             SwitchData.class, switchData);

        // Verify the function calls
        verify(eventChannel);

    }

    /**
     * Tests the publishing of Add Link Event.
     */
    @Test
    public void testPublishAddLinkEvent() throws RegistryException {
        setupTopologyPublisher();

        // Mock the eventChannel functions
        eventChannel.addEntry(anyObject(byte[].class),
                              anyObject(TopologyEvent.class));
        expectLastCall().times(5, 5);  // (2 Switch + 2 Port + 1 Link)
        replay(eventChannel);

        // Generate the Switch and Port Events
        PortNumber portNumber1 = PortNumber.uint32(1);
        SwitchData switchData1 = new SwitchData(DPID_1);
        Collection<PortData> portDataEntries1 = new ArrayList<PortData>();
        portDataEntries1.add(new PortData(DPID_1, portNumber1));

        // Call the TopologyPublisher function for adding a Switch
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchEvent",
                             new Class<?>[] {SwitchData.class,
                                     Collection.class},
                             switchData1, portDataEntries1);

        // Generate the Switch and Port Events
        PortNumber portNumber2 = PortNumber.uint32(2);
        SwitchData switchData2 = new SwitchData(DPID_2);
        Collection<PortData> portDataEntries2 = new ArrayList<PortData>();
        portDataEntries2.add(new PortData(DPID_2, portNumber2));

        // Call the TopologyPublisher function for adding a Switch
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchEvent",
                             new Class<?>[] {SwitchData.class,
                                     Collection.class},
                             switchData2, portDataEntries2);

        // Generate the Link Event
        LinkData linkData =
            new LinkData(new SwitchPort(DPID_1, portNumber1),
                          new SwitchPort(DPID_2, portNumber2));

        // Call the TopologyPublisher function for adding a Link
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddLinkEvent",
                             LinkData.class, linkData);

        // Verify the function calls
        verify(eventChannel);
    }

    /**
     * Tests the publishing of Remove Link Event.
     */
    @Test
    public void testPublishRemoveLinkEvent() throws RegistryException {
        setupTopologyPublisher();

        // Mock the eventChannel functions
        eventChannel.removeEntry(anyObject(byte[].class));
        expectLastCall().times(1, 1);          // (1 Link)
        replay(eventChannel);

        // Generate the Switch and Port Events
        PortNumber portNumber1 = PortNumber.uint32(1);
        SwitchData switchData1 = new SwitchData(DPID_1);
        Collection<PortData> portDataEntries1 = new ArrayList<PortData>();
        portDataEntries1.add(new PortData(DPID_1, portNumber1));

        // Call the TopologyPublisher function for adding a Switch
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchEvent",
                             new Class<?>[] {SwitchData.class,
                                     Collection.class},
                             switchData1, portDataEntries1);

        // Generate the Switch and Port Events
        PortNumber portNumber2 = PortNumber.uint32(2);
        SwitchData switchData2 = new SwitchData(DPID_2);
        Collection<PortData> portDataEntries2 = new ArrayList<PortData>();
        portDataEntries2.add(new PortData(DPID_2, portNumber2));

        // Call the TopologyPublisher function for adding a Switch
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchEvent",
                             new Class<?>[] {SwitchData.class,
                                     Collection.class},
                             switchData2, portDataEntries2);

        // Generate the Link Event
        LinkData linkData =
            new LinkData(new SwitchPort(DPID_1, portNumber1),
                          new SwitchPort(DPID_2, portNumber2));

        // Call the TopologyPublisher function for adding a Link
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddLinkEvent",
                             LinkData.class, linkData);

        // Call the TopologyPublisher function for removing a Link
        TestUtils.callMethod(theTopologyPublisher,
                             "publishRemoveLinkEvent",
                             LinkData.class, linkData);

        // Verify the function calls
        verify(eventChannel);
    }

    /**
     * Tests the publishing of Add Host Event.
     */
    @Test
    public void testPublishAddHostEvent() throws RegistryException {
        setupTopologyPublisher();

        // Mock the eventChannel functions
        eventChannel.addEntry(anyObject(byte[].class),
                              anyObject(TopologyEvent.class));
        expectLastCall().times(3, 3);  // (1 Switch + 1 Port + 1 Host)
        replay(eventChannel);

        // Generate the Switch and Port Events
        PortNumber portNumber1 = PortNumber.uint32(1);
        SwitchData switchData1 = new SwitchData(DPID_1);
        Collection<PortData> portDataEntries1 = new ArrayList<PortData>();
        portDataEntries1.add(new PortData(DPID_1, portNumber1));

        // Call the TopologyPublisher function for adding a Switch
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchEvent",
                             new Class<?>[] {SwitchData.class,
                                     Collection.class},
                             switchData1, portDataEntries1);

        // Generate the Host Event
        PortNumber portNumber = PortNumber.uint32(1);
        MACAddress hostMac = MACAddress.valueOf("00:AA:11:BB:33:CC");
        SwitchPort sp = new SwitchPort(DPID_1, portNumber);
        List<SwitchPort> spLists = new ArrayList<SwitchPort>();
        spLists.add(sp);
        HostData hostData = new HostData(hostMac);
        hostData.setAttachmentPoints(spLists);

        // Call the TopologyPublisher function for adding a Host
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddHostEvent",
                             HostData.class, hostData);

        // Verify the function calls
        verify(eventChannel);
    }

    /**
     * Tests the publising of Remove Host Event.
     */
    @Test
    public void testPublishRemoveHostEvent() throws RegistryException {
        setupTopologyPublisher();

        // Mock the eventChannel functions
        eventChannel.removeEntry(anyObject(byte[].class));
        expectLastCall().times(1, 1);          // 1 Host
        replay(eventChannel);

        // Generate the Switch and Port Events
        PortNumber portNumber1 = PortNumber.uint32(1);
        SwitchData switchData1 = new SwitchData(DPID_1);
        Collection<PortData> portDataEntries1 = new ArrayList<PortData>();
        portDataEntries1.add(new PortData(DPID_1, portNumber1));

        // Call the TopologyPublisher function for adding a Switch
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddSwitchEvent",
                             new Class<?>[] {SwitchData.class,
                                     Collection.class},
                             switchData1, portDataEntries1);

        // Generate the Host Event
        PortNumber portNumber = PortNumber.uint32(1);
        MACAddress hostMac = MACAddress.valueOf("00:AA:11:BB:33:CC");
        SwitchPort sp = new SwitchPort(DPID_1, portNumber);
        List<SwitchPort> spLists = new ArrayList<SwitchPort>();
        spLists.add(sp);
        HostData hostData = new HostData(hostMac);
        hostData.setAttachmentPoints(spLists);

        // Call the TopologyPublisher function for adding a Host
        TestUtils.callMethod(theTopologyPublisher,
                             "publishAddHostEvent",
                             HostData.class, hostData);

        // Call the TopologyPublisher function for removing a Host
        TestUtils.callMethod(theTopologyPublisher,
                             "publishRemoveHostEvent",
                             HostData.class, hostData);

        // Verify the function calls
        verify(eventChannel);
    }

    /**
     * Tests adding of a Switch Mastership event and the topology replica
     * transformation.
     */
    @Test
    public void testAddMastershipData() {
        setupTopologyManager();

        // Prepare the event
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_1, ONOS_INSTANCE_ID_1, role);
        // Add the event
        TestUtils.callMethod(theTopologyManager, "addMastershipData",
                             MastershipData.class, mastershipData);

        //
        // NOTE: The topology itself doesn't contain the Mastership Events,
        // hence we don't check the topology.
        //

        // Check the events to be fired
        List<MastershipData> apiAddedMastershipDataEntries
            = TestUtils.getField(theTopologyManager,
                                 "apiAddedMastershipDataEntries");
        assertThat(apiAddedMastershipDataEntries, hasItem(mastershipData));
    }

    /**
     * Tests removing of a Switch Mastership event and the topology replica
     * transformation.
     */
    @Test
    public void testRemoveMastershipData() {
        setupTopologyManager();

        // Prepare the event
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_1, ONOS_INSTANCE_ID_1, role);
        // Add the event
        TestUtils.callMethod(theTopologyManager, "addMastershipData",
                             MastershipData.class, mastershipData);

        // Check the events to be fired
        List<MastershipData> apiAddedMastershipDataEntries
            = TestUtils.getField(theTopologyManager,
                                 "apiAddedMastershipDataEntries");
        assertThat(apiAddedMastershipDataEntries, hasItem(mastershipData));

        // Remove the event
        TestUtils.callMethod(theTopologyManager, "removeMastershipData",
                             MastershipData.class,
                             new MastershipData(mastershipData));

        // Check the events to be fired
        List<MastershipData> apiRemovedMastershipDataEntries
            = TestUtils.getField(theTopologyManager,
                                 "apiRemovedMastershipDataEntries");
        assertThat(apiRemovedMastershipDataEntries, hasItem(mastershipData));
    }

    /**
     * Tests adding of a Switch and the topology replica transformation.
     */
    @Test
    public void testAddSwitch() {
        setupTopologyManager();

        SwitchData sw = new SwitchData(DPID_1);
        sw.createStringAttribute("foo", "bar");

        TestUtils.callMethod(theTopologyManager, "addSwitch",
                             SwitchData.class, sw);

        // Check the topology structure
        BaseInternalTopology topology =
            (BaseInternalTopology) theTopologyManager.getTopology();
        SwitchData swInTopo = topology.getSwitchData(DPID_1);
        assertEquals(sw, swInTopo);
        assertTrue(swInTopo.isFrozen());
        assertEquals("bar", swInTopo.getStringAttribute("foo"));

        // Check the events to be fired
        List<SwitchData> apiAddedSwitchDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedSwitchDataEntries");
        assertThat(apiAddedSwitchDataEntries, hasItem(sw));
    }

    /**
     * Tests adding of a Port and the topology replica transformation.
     */
    @Test
    public void testAddPort() {
        setupTopologyManager();

        SwitchData sw = new SwitchData(DPID_1);
        sw.createStringAttribute("foo", "bar");

        final PortNumber portNumber = PortNumber.uint32(2);
        PortData port = new PortData(DPID_1, portNumber);
        port.createStringAttribute("fuzz", "buzz");

        TestUtils.callMethod(theTopologyManager, "addSwitch",
                             SwitchData.class, sw);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, port);

        // Check the topology structure
        BaseInternalTopology topology =
            (BaseInternalTopology) theTopologyManager.getTopology();
        SwitchData swInTopo = topology.getSwitchData(DPID_1);
        assertEquals(sw, swInTopo);
        assertTrue(swInTopo.isFrozen());
        assertEquals("bar", swInTopo.getStringAttribute("foo"));

        final SwitchPort switchPort = new SwitchPort(DPID_1, portNumber);
        PortData portInTopo = topology.getPortData(switchPort);
        assertEquals(port, portInTopo);
        assertTrue(portInTopo.isFrozen());
        assertEquals("buzz", portInTopo.getStringAttribute("fuzz"));

        // Check the events to be fired
        List<PortData> apiAddedPortDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedPortDataEntries");
        assertThat(apiAddedPortDataEntries, hasItem(port));
    }

    /**
     * Tests removing of a Port followed by removing of a Switch,
     * and the topology replica transformation.
     */
    @Test
    public void testRemovePortThenSwitch() {
        setupTopologyManager();

        SwitchData sw = new SwitchData(DPID_1);
        sw.createStringAttribute("foo", "bar");

        final PortNumber portNumber = PortNumber.uint32(2);
        PortData port = new PortData(DPID_1, portNumber);
        port.createStringAttribute("fuzz", "buzz");

        TestUtils.callMethod(theTopologyManager, "addSwitch",
                             SwitchData.class, sw);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, port);

        // Check the topology structure
        BaseInternalTopology topology =
            (BaseInternalTopology) theTopologyManager.getTopology();
        SwitchData swInTopo = topology.getSwitchData(DPID_1);
        assertEquals(sw, swInTopo);
        assertTrue(swInTopo.isFrozen());
        assertEquals("bar", swInTopo.getStringAttribute("foo"));

        final SwitchPort switchPort = new SwitchPort(DPID_1, portNumber);
        PortData portInTopo = topology.getPortData(switchPort);
        assertEquals(port, portInTopo);
        assertTrue(portInTopo.isFrozen());
        assertEquals("buzz", portInTopo.getStringAttribute("fuzz"));

        // Remove in proper order
        TestUtils.callMethod(theTopologyManager, "removePort",
                            PortData.class, new PortData(port));
        TestUtils.callMethod(theTopologyManager, "removeSwitch",
                            SwitchData.class, new SwitchData(sw));


        // Check the events to be fired
        List<PortData> apiRemovedPortDataEntries
            = TestUtils.getField(theTopologyManager, "apiRemovedPortDataEntries");
        assertThat(apiRemovedPortDataEntries, hasItem(port));
        List<SwitchData> apiRemovedSwitchDataEntries
            = TestUtils.getField(theTopologyManager, "apiRemovedSwitchDataEntries");
        assertThat(apiRemovedSwitchDataEntries, hasItem(sw));
    }

    /**
     * Tests removing of a Switch without removing of a Port,
     * and the topology replica transformation.
     */
    @Test
    public void testRemoveSwitchWithoutPortRemoval() {
        setupTopologyManager();

        SwitchData sw = new SwitchData(DPID_1);
        sw.createStringAttribute("foo", "bar");

        final PortNumber portNumber = PortNumber.uint32(2);
        PortData port = new PortData(DPID_1, portNumber);
        port.createStringAttribute("fuzz", "buzz");

        TestUtils.callMethod(theTopologyManager, "addSwitch",
                             SwitchData.class, sw);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, port);

        // Check the topology structure
        BaseInternalTopology topology =
            (BaseInternalTopology) theTopologyManager.getTopology();
        SwitchData swInTopo = topology.getSwitchData(DPID_1);
        assertEquals(sw, swInTopo);
        assertTrue(swInTopo.isFrozen());
        assertEquals("bar", swInTopo.getStringAttribute("foo"));

        final SwitchPort switchPort = new SwitchPort(DPID_1, portNumber);
        PortData portInTopo = topology.getPortData(switchPort);
        assertEquals(port, portInTopo);
        assertTrue(portInTopo.isFrozen());
        assertEquals("buzz", portInTopo.getStringAttribute("fuzz"));

        // Remove in in-proper order
//        TestUtils.callMethod(theTopologyManager, "removePort",
//                            PortData.class, new PortData(port));
        TestUtils.callMethod(theTopologyManager, "removeSwitch",
                            SwitchData.class, new SwitchData(sw));


        // Check the events to be fired
        // The outcome should be the same as #testRemovePortThenSwitch
        List<PortData> apiRemovedPortDataEntries
            = TestUtils.getField(theTopologyManager, "apiRemovedPortDataEntries");
        assertThat(apiRemovedPortDataEntries, hasItem(port));
        List<SwitchData> apiRemovedSwitchDataEntries
            = TestUtils.getField(theTopologyManager, "apiRemovedSwitchDataEntries");
        assertThat(apiRemovedSwitchDataEntries, hasItem(sw));
    }

    /**
     * Tests adding of a Link and the topology replica transformation.
     */
    @Test
    public void testAddLink() {
        setupTopologyManager();

        SwitchData sw = new SwitchData(DPID_1);
        sw.createStringAttribute("foo", "bar");

        final PortNumber portNumberA = PortNumber.uint32(2);
        PortData portA = new PortData(DPID_1, portNumberA);
        portA.createStringAttribute("fuzz", "buzz");

        final PortNumber portNumberB = PortNumber.uint32(3);
        PortData portB = new PortData(DPID_1, portNumberB);
        portB.createStringAttribute("fizz", "buz");

        LinkData linkA = new LinkData(portA.getSwitchPort(),
                                        portB.getSwitchPort());
        linkA.createStringAttribute(TopologyElement.TYPE,
                                    TopologyElement.TYPE_OPTICAL_LAYER);
        LinkData linkB = new LinkData(portB.getSwitchPort(),
                                        portA.getSwitchPort());
        linkB.createStringAttribute(TopologyElement.TYPE,
                                    TopologyElement.TYPE_OPTICAL_LAYER);

        TestUtils.callMethod(theTopologyManager, "addSwitch",
                             SwitchData.class, sw);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portA);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portB);
        TestUtils.callMethod(theTopologyManager, "addLink",
                             LinkData.class, linkA);
        TestUtils.callMethod(theTopologyManager, "addLink",
                             LinkData.class, linkB);

        // Check the topology structure
        BaseInternalTopology topology =
            (BaseInternalTopology) theTopologyManager.getTopology();
        SwitchData swInTopo = topology.getSwitchData(DPID_1);
        assertEquals(sw, swInTopo);
        assertTrue(swInTopo.isFrozen());
        assertEquals("bar", swInTopo.getStringAttribute("foo"));

        final SwitchPort switchPortA = new SwitchPort(DPID_1, portNumberA);
        PortData portAInTopo = topology.getPortData(switchPortA);
        assertEquals(portA, portAInTopo);
        assertTrue(portAInTopo.isFrozen());
        assertEquals("buzz", portAInTopo.getStringAttribute("fuzz"));

        final SwitchPort switchPortB = new SwitchPort(DPID_1, portNumberB);
        PortData portBInTopo = topology.getPortData(switchPortB);
        assertEquals(portB, portBInTopo);
        assertTrue(portBInTopo.isFrozen());
        assertEquals("buz", portBInTopo.getStringAttribute("fizz"));

        LinkData linkAInTopo = topology.getLinkData(linkA.getLinkTuple());
        assertEquals(linkA, linkAInTopo);
        assertTrue(linkAInTopo.isFrozen());
        assertEquals(TopologyElement.TYPE_OPTICAL_LAYER,
                     linkAInTopo.getType());

        LinkData linkBInTopo = topology.getLinkData(linkB.getLinkTuple());
        assertEquals(linkB, linkBInTopo);
        assertTrue(linkBInTopo.isFrozen());
        assertEquals(TopologyElement.TYPE_OPTICAL_LAYER,
                     linkBInTopo.getType());

        // Check the events to be fired
        List<LinkData> apiAddedLinkDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedLinkDataEntries");
        assertThat(apiAddedLinkDataEntries, containsInAnyOrder(linkA, linkB));
    }

    /**
     * Tests removing of a Link without removing of a Host, and the topology
     * replica transformation.
     */
    @Test
    public void testAddLinkKickingOffHost() {
        setupTopologyManager();

        SwitchData sw = new SwitchData(DPID_1);
        sw.createStringAttribute("foo", "bar");

        final PortNumber portNumberA = PortNumber.uint32(2);
        PortData portA = new PortData(DPID_1, portNumberA);
        portA.createStringAttribute("fuzz", "buzz");

        final PortNumber portNumberB = PortNumber.uint32(3);
        PortData portB = new PortData(DPID_1, portNumberB);
        portB.createStringAttribute("fizz", "buz");

        final PortNumber portNumberC = PortNumber.uint32(4);
        PortData portC = new PortData(DPID_1, portNumberC);
        portC.createStringAttribute("fizz", "buz");

        final MACAddress macA = MACAddress.valueOf(666L);
        HostData hostA = new HostData(macA);
        hostA.addAttachmentPoint(portA.getSwitchPort());
        final long timestampA = 392893200L;
        hostA.setLastSeenTime(timestampA);

        final MACAddress macB = MACAddress.valueOf(999L);
        HostData hostB = new HostData(macB);
        hostB.addAttachmentPoint(portB.getSwitchPort());
        hostB.addAttachmentPoint(portC.getSwitchPort());
        final long timestampB = 392893201L;
        hostB.setLastSeenTime(timestampB);


        LinkData linkA = new LinkData(portA.getSwitchPort(),
                                        portB.getSwitchPort());
        linkA.createStringAttribute(TopologyElement.TYPE,
                                    TopologyElement.TYPE_OPTICAL_LAYER);
        LinkData linkB = new LinkData(portB.getSwitchPort(),
                                        portA.getSwitchPort());
        linkB.createStringAttribute(TopologyElement.TYPE,
                                    TopologyElement.TYPE_OPTICAL_LAYER);

        TestUtils.callMethod(theTopologyManager, "addSwitch",
                             SwitchData.class, sw);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portA);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portB);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portC);
        TestUtils.callMethod(theTopologyManager, "addHost",
                             HostData.class, hostA);
        TestUtils.callMethod(theTopologyManager, "addHost",
                             HostData.class, hostB);

        TestUtils.callMethod(theTopologyManager, "addLink",
                             LinkData.class, linkA);
        TestUtils.callMethod(theTopologyManager, "addLink",
                             LinkData.class, linkB);

        // Check the topology structure
        BaseInternalTopology topology =
            (BaseInternalTopology) theTopologyManager.getTopology();
        SwitchData swInTopo = topology.getSwitchData(DPID_1);
        assertEquals(sw, swInTopo);
        assertTrue(swInTopo.isFrozen());
        assertEquals("bar", swInTopo.getStringAttribute("foo"));

        final SwitchPort switchPortA = new SwitchPort(DPID_1, portNumberA);
        PortData portAInTopo = topology.getPortData(switchPortA);
        assertEquals(portA, portAInTopo);
        assertTrue(portAInTopo.isFrozen());
        assertEquals("buzz", portAInTopo.getStringAttribute("fuzz"));

        final SwitchPort switchPortB = new SwitchPort(DPID_1, portNumberB);
        PortData portBInTopo = topology.getPortData(switchPortB);
        assertEquals(portB, portBInTopo);
        assertTrue(portBInTopo.isFrozen());
        assertEquals("buz", portBInTopo.getStringAttribute("fizz"));

        // hostA expected to be removed
        assertNull(topology.getHostData(macA));
        // hostB expected to be there with reduced attachment point
        HostData hostBrev = new HostData(macB);
        hostBrev.addAttachmentPoint(portC.getSwitchPort());
        hostBrev.setLastSeenTime(timestampB);
        hostBrev.freeze();
        assertEquals(hostBrev, topology.getHostData(macB));


        LinkData linkAInTopo = topology.getLinkData(linkA.getLinkTuple());
        assertEquals(linkA, linkAInTopo);
        assertTrue(linkAInTopo.isFrozen());
        assertEquals(TopologyElement.TYPE_OPTICAL_LAYER,
                     linkAInTopo.getType());

        LinkData linkBInTopo = topology.getLinkData(linkB.getLinkTuple());
        assertEquals(linkB, linkBInTopo);
        assertTrue(linkBInTopo.isFrozen());
        assertEquals(TopologyElement.TYPE_OPTICAL_LAYER,
                     linkBInTopo.getType());

        // Check the events to be fired
        List<HostData> apiAddedHostDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedHostDataEntries");
        assertThat(apiAddedHostDataEntries, hasItem(hostBrev));

        List<HostData> apiRemovedHostDataEntries
            = TestUtils.getField(theTopologyManager, "apiRemovedHostDataEntries");
        assertThat(apiRemovedHostDataEntries, hasItem(hostA));
        List<LinkData> apiAddedLinkDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedLinkDataEntries");
        assertThat(apiAddedLinkDataEntries, containsInAnyOrder(linkA, linkB));
    }

    /**
     * Tests removing of a Link and the topology replica transformation.
     */
    @Test
    public void testRemoveLink() {
        setupTopologyManager();

        SwitchData sw = new SwitchData(DPID_1);
        sw.createStringAttribute("foo", "bar");

        final PortNumber portNumberA = PortNumber.uint32(2);
        PortData portA = new PortData(DPID_1, portNumberA);
        portA.createStringAttribute("fuzz", "buzz");

        final PortNumber portNumberB = PortNumber.uint32(3);
        PortData portB = new PortData(DPID_1, portNumberB);
        portB.createStringAttribute("fizz", "buz");

        LinkData linkA = new LinkData(portA.getSwitchPort(),
                                        portB.getSwitchPort());
        linkA.createStringAttribute(TopologyElement.TYPE,
                                    TopologyElement.TYPE_OPTICAL_LAYER);
        LinkData linkB = new LinkData(portB.getSwitchPort(),
                                        portA.getSwitchPort());
        linkB.createStringAttribute(TopologyElement.TYPE,
                                    TopologyElement.TYPE_OPTICAL_LAYER);

        TestUtils.callMethod(theTopologyManager, "addSwitch",
                             SwitchData.class, sw);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portA);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portB);
        TestUtils.callMethod(theTopologyManager, "addLink",
                             LinkData.class, linkA);
        TestUtils.callMethod(theTopologyManager, "addLink",
                             LinkData.class, linkB);

        // Check the topology structure
        BaseInternalTopology topology =
            (BaseInternalTopology) theTopologyManager.getTopology();
        SwitchData swInTopo = topology.getSwitchData(DPID_1);
        assertEquals(sw, swInTopo);
        assertTrue(swInTopo.isFrozen());
        assertEquals("bar", swInTopo.getStringAttribute("foo"));

        final SwitchPort switchPortA = new SwitchPort(DPID_1, portNumberA);
        PortData portAInTopo = topology.getPortData(switchPortA);
        assertEquals(portA, portAInTopo);
        assertTrue(portAInTopo.isFrozen());
        assertEquals("buzz", portAInTopo.getStringAttribute("fuzz"));

        final SwitchPort switchPortB = new SwitchPort(DPID_1, portNumberB);
        PortData portBInTopo = topology.getPortData(switchPortB);
        assertEquals(portB, portBInTopo);
        assertTrue(portBInTopo.isFrozen());
        assertEquals("buz", portBInTopo.getStringAttribute("fizz"));

        LinkData linkAInTopo = topology.getLinkData(linkA.getLinkTuple());
        assertEquals(linkA, linkAInTopo);
        assertTrue(linkAInTopo.isFrozen());
        assertEquals(TopologyElement.TYPE_OPTICAL_LAYER,
                     linkAInTopo.getType());


        LinkData linkBInTopo = topology.getLinkData(linkB.getLinkTuple());
        assertEquals(linkB, linkBInTopo);
        assertTrue(linkBInTopo.isFrozen());
        assertEquals(TopologyElement.TYPE_OPTICAL_LAYER,
                     linkBInTopo.getType());

        // Check the events to be fired
        // FIXME if link flapped (linkA in this scenario),
        //  linkA appears in both removed and added is this expected behavior?
        List<LinkData> apiAddedLinkDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedLinkDataEntries");
        assertThat(apiAddedLinkDataEntries, containsInAnyOrder(linkA, linkB));

        // Clear the events before removing the link
        apiAddedLinkDataEntries.clear();

        // Remove the link
        TestUtils.callMethod(theTopologyManager, "removeLink",
                             LinkData.class, new LinkData(linkA));

        LinkData linkANotInTopo = topology.getLinkData(linkA.getLinkTuple());
        assertNull(linkANotInTopo);

        List<LinkData> apiRemovedLinkDataEntries
            = TestUtils.getField(theTopologyManager, "apiRemovedLinkDataEntries");
        assertThat(apiRemovedLinkDataEntries, hasItem(linkA));
    }

    /**
     * Tests adding of a Host without adding of a Link, and the topology
     * replica transformation.
     */
    @Test
    public void testAddHostIgnoredByLink() {
        setupTopologyManager();

        SwitchData sw = new SwitchData(DPID_1);
        sw.createStringAttribute("foo", "bar");

        final PortNumber portNumberA = PortNumber.uint32(2);
        PortData portA = new PortData(DPID_1, portNumberA);
        portA.createStringAttribute("fuzz", "buzz");

        final PortNumber portNumberB = PortNumber.uint32(3);
        PortData portB = new PortData(DPID_1, portNumberB);
        portB.createStringAttribute("fizz", "buz");

        final PortNumber portNumberC = PortNumber.uint32(4);
        PortData portC = new PortData(DPID_1, portNumberC);
        portC.createStringAttribute("fizz", "buz");

        LinkData linkA = new LinkData(portA.getSwitchPort(),
                                        portB.getSwitchPort());
        linkA.createStringAttribute(TopologyElement.TYPE,
                                    TopologyElement.TYPE_OPTICAL_LAYER);
        LinkData linkB = new LinkData(portB.getSwitchPort(),
                                        portA.getSwitchPort());
        linkB.createStringAttribute(TopologyElement.TYPE,
                                    TopologyElement.TYPE_OPTICAL_LAYER);

        TestUtils.callMethod(theTopologyManager, "addSwitch",
                             SwitchData.class, sw);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portA);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portB);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portC);
        TestUtils.callMethod(theTopologyManager, "addLink",
                             LinkData.class, linkA);
        TestUtils.callMethod(theTopologyManager, "addLink",
                             LinkData.class, linkB);

        // Add hostA attached to a port which already has a link
        final MACAddress macA = MACAddress.valueOf(666L);
        HostData hostA = new HostData(macA);
        hostA.addAttachmentPoint(portA.getSwitchPort());
        final long timestampA = 392893200L;
        hostA.setLastSeenTime(timestampA);

        TestUtils.callMethod(theTopologyManager, "addHost",
                             HostData.class, hostA);

        // Add hostB attached to multiple ports,
        // some of them which already has a link
        final MACAddress macB = MACAddress.valueOf(999L);
        HostData hostB = new HostData(macB);
        hostB.addAttachmentPoint(portB.getSwitchPort());
        hostB.addAttachmentPoint(portC.getSwitchPort());
        final long timestampB = 392893201L;
        hostB.setLastSeenTime(timestampB);

        TestUtils.callMethod(theTopologyManager, "addHost",
                             HostData.class, hostB);

        // Check the topology structure
        BaseInternalTopology topology =
            (BaseInternalTopology) theTopologyManager.getTopology();
        SwitchData swInTopo = topology.getSwitchData(DPID_1);
        assertEquals(sw, swInTopo);
        assertTrue(swInTopo.isFrozen());
        assertEquals("bar", swInTopo.getStringAttribute("foo"));

        final SwitchPort switchPortA = new SwitchPort(DPID_1, portNumberA);
        PortData portAInTopo = topology.getPortData(switchPortA);
        assertEquals(portA, portAInTopo);
        assertTrue(portAInTopo.isFrozen());
        assertEquals("buzz", portAInTopo.getStringAttribute("fuzz"));

        final SwitchPort switchPortB = new SwitchPort(DPID_1, portNumberB);
        PortData portBInTopo = topology.getPortData(switchPortB);
        assertEquals(portB, portBInTopo);
        assertTrue(portBInTopo.isFrozen());
        assertEquals("buz", portBInTopo.getStringAttribute("fizz"));

        // hostA expected to be completely ignored
        assertNull(topology.getHostData(macA));
        // hostB expected to be there with reduced attachment point
        HostData hostBrev = new HostData(macB);
        hostBrev.addAttachmentPoint(portC.getSwitchPort());
        hostBrev.setLastSeenTime(timestampB);
        hostBrev.freeze();
        assertEquals(hostBrev, topology.getHostData(macB));


        LinkData linkAInTopo = topology.getLinkData(linkA.getLinkTuple());
        assertEquals(linkA, linkAInTopo);
        assertTrue(linkAInTopo.isFrozen());
        assertEquals(TopologyElement.TYPE_OPTICAL_LAYER,
                     linkAInTopo.getType());

        LinkData linkBInTopo = topology.getLinkData(linkB.getLinkTuple());
        assertEquals(linkB, linkBInTopo);
        assertTrue(linkBInTopo.isFrozen());
        assertEquals(TopologyElement.TYPE_OPTICAL_LAYER,
                     linkBInTopo.getType());

        // Check the events to be fired
        // hostB should be added with reduced attachment points
        List<HostData> apiAddedHostDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedHostDataEntries");
        assertThat(apiAddedHostDataEntries, hasItem(hostBrev));

        // hostA should not be ignored
        List<HostData> apiRemovedHostDataEntries
            = TestUtils.getField(theTopologyManager, "apiRemovedHostDataEntries");
        assertThat(apiRemovedHostDataEntries, not(hasItem(hostA)));

        List<LinkData> apiAddedLinkDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedLinkDataEntries");
        assertThat(apiAddedLinkDataEntries, containsInAnyOrder(linkA, linkB));
    }

    /**
     * Tests adding and moving of a Host, and the topology replica
     * transformation.
     */
    @Test
    public void testAddHostMove() {
        setupTopologyManager();

        SwitchData sw = new SwitchData(DPID_1);
        sw.createStringAttribute("foo", "bar");

        final PortNumber portNumberA = PortNumber.uint32(2);
        PortData portA = new PortData(DPID_1, portNumberA);
        portA.createStringAttribute("fuzz", "buzz");

        final PortNumber portNumberB = PortNumber.uint32(3);
        PortData portB = new PortData(DPID_1, portNumberB);
        portB.createStringAttribute("fizz", "buz");

        final PortNumber portNumberC = PortNumber.uint32(4);
        PortData portC = new PortData(DPID_1, portNumberC);
        portC.createStringAttribute("fizz", "buz");

        TestUtils.callMethod(theTopologyManager, "addSwitch",
                             SwitchData.class, sw);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portA);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portB);
        TestUtils.callMethod(theTopologyManager, "addPort",
                             PortData.class, portC);

        // Add hostA attached to a Port which already has a Link
        final MACAddress macA = MACAddress.valueOf(666L);
        HostData hostA = new HostData(macA);
        hostA.addAttachmentPoint(portA.getSwitchPort());
        final long timestampA = 392893200L;
        hostA.setLastSeenTime(timestampA);

        TestUtils.callMethod(theTopologyManager, "addHost",
                             HostData.class, hostA);


        // Check the topology structure
        BaseInternalTopology topology =
            (BaseInternalTopology) theTopologyManager.getTopology();
        SwitchData swInTopo = topology.getSwitchData(DPID_1);
        assertEquals(sw, swInTopo);
        assertTrue(swInTopo.isFrozen());
        assertEquals("bar", swInTopo.getStringAttribute("foo"));

        final SwitchPort switchPortA = new SwitchPort(DPID_1, portNumberA);
        PortData portAInTopo = topology.getPortData(switchPortA);
        assertEquals(portA, portAInTopo);
        assertTrue(portAInTopo.isFrozen());
        assertEquals("buzz", portAInTopo.getStringAttribute("fuzz"));

        final SwitchPort switchPortB = new SwitchPort(DPID_1, portNumberB);
        PortData portBInTopo = topology.getPortData(switchPortB);
        assertEquals(portB, portBInTopo);
        assertTrue(portBInTopo.isFrozen());
        assertEquals("buz", portBInTopo.getStringAttribute("fizz"));

        // hostA expected to be there
        assertEquals(hostA, topology.getHostData(macA));
        assertEquals(timestampA,
                     topology.getHostData(macA).getLastSeenTime());

        // Check the events to be fired
        // hostA should be added
        List<HostData> apiAddedHostDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedHostDataEntries");
        assertThat(apiAddedHostDataEntries, hasItem(hostA));


        // Clear the events before moving the Host
        apiAddedHostDataEntries.clear();

        HostData hostAmoved = new HostData(macA);
        hostAmoved.addAttachmentPoint(portB.getSwitchPort());
        final long timestampAmoved = 392893201L;
        hostAmoved.setLastSeenTime(timestampAmoved);

        TestUtils.callMethod(theTopologyManager, "addHost",
                             HostData.class, hostAmoved);

        assertEquals(hostAmoved, topology.getHostData(macA));
        assertEquals(timestampAmoved,
                     topology.getHostData(macA).getLastSeenTime());

        // hostA expected to be there with new attachment point
        apiAddedHostDataEntries
            = TestUtils.getField(theTopologyManager, "apiAddedHostDataEntries");
        assertThat(apiAddedHostDataEntries, hasItem(hostAmoved));

        // hostA is updated not removed
        List<HostData> apiRemovedHostDataEntries
            = TestUtils.getField(theTopologyManager, "apiRemovedHostDataEntries");
        assertThat(apiRemovedHostDataEntries, not(hasItem(hostA)));
    }

    /**
     * Tests processing of a Switch Mastership Event and the delivery of the
     * topology events.
     */
    @Test
    public void testProcessMastershipData() {
        List<EventEntry<TopologyEvent>> events = new LinkedList<>();
        EventEntry<TopologyEvent> eventEntry;
        TopologyEvent topologyEvent;

        setupTopologyManagerWithEventHandler();

        // Prepare the Mastership Event
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_1, ONOS_INSTANCE_ID_1, role);
        topologyEvent = new TopologyEvent(mastershipData, ONOS_INSTANCE_ID_1);

        // Add the Mastership Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologyEvent);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events
        TopologyEvents topologyEvents = theTopologyListener.topologyEvents;
        assertNotNull(topologyEvents);
        assertThat(topologyEvents.getAddedMastershipDataEntries(),
                   hasItem(mastershipData));
        theTopologyListener.clear();
    }

    /**
     * Tests processing of a Switch Event, and the delivery of the topology
     * events.
     *
     * We test the following scenario:
     * - Switch Mastership Event is processed along with a Switch Event - both
     *   events should be delivered.
     */
    @Test
    public void testProcessSwitchData() {
        TopologyEvents topologyEvents;
        List<EventEntry<TopologyEvent>> events = new LinkedList<>();
        EventEntry<TopologyEvent> eventEntry;
        TopologyEvent topologyMastershipData;
        TopologyEvent topologySwitchData;

        setupTopologyManagerWithEventHandler();

        // Prepare the Mastership Event
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_1, ONOS_INSTANCE_ID_1, role);
        topologyMastershipData = new TopologyEvent(mastershipData,
                                                    ONOS_INSTANCE_ID_1);

        // Prepare the Switch Event
        SwitchData switchData = new SwitchData(DPID_1);
        topologySwitchData = new TopologyEvent(switchData,
                                                ONOS_INSTANCE_ID_1);

        // Add the Mastership Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologyMastershipData);
        events.add(eventEntry);

        // Add the Switch Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologySwitchData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events
        topologyEvents = theTopologyListener.topologyEvents;
        assertNotNull(topologyEvents);
        assertThat(topologyEvents.getAddedMastershipDataEntries(),
                   hasItem(mastershipData));
        assertThat(topologyEvents.getAddedSwitchDataEntries(),
                   hasItem(switchData));
        theTopologyListener.clear();
    }

    /**
     * Tests processing of a misordered Switch Event, and the delivery of the
     * topology events.
     *
     * We test the following scenario:
     * - Only a Switch Event is processed first, later followed by a Switch
     *   Mastership Event - the Switch Event should be delivered after the
     *   Switch Mastership Event is processed.
     */
    @Test
    public void testProcessMisorderedSwitchData() {
        TopologyEvents topologyEvents;
        List<EventEntry<TopologyEvent>> events = new LinkedList<>();
        EventEntry<TopologyEvent> eventEntry;
        TopologyEvent topologyMastershipData;
        TopologyEvent topologySwitchData;

        setupTopologyManagerWithEventHandler();

        // Prepare the Mastership Event
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_1, ONOS_INSTANCE_ID_1, role);
        topologyMastershipData = new TopologyEvent(mastershipData,
                                                    ONOS_INSTANCE_ID_1);

        // Prepare the Switch Event
        SwitchData switchData = new SwitchData(DPID_1);
        topologySwitchData = new TopologyEvent(switchData,
                                                ONOS_INSTANCE_ID_1);

        // Add the Switch Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologySwitchData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: no events should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNull(topologyEvents);
        theTopologyListener.clear();
        events.clear();

        // Add the Mastership Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologyMastershipData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: both events should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNotNull(topologyEvents);
        assertThat(topologyEvents.getAddedMastershipDataEntries(),
                   hasItem(mastershipData));
        assertThat(topologyEvents.getAddedSwitchDataEntries(),
                   hasItem(switchData));
        theTopologyListener.clear();
    }

    /**
     * Tests processing of a Switch Event with Mastership Event from
     * another ONOS instance, and the delivery of the topology events.
     *
     * We test the following scenario:
     * - Only a Switch Event is processed first, later followed by a Switch
     *   Mastership Event from another ONOS instance - only the Switch
     *   Mastership Event should be delivered.
     */
    @Test
    public void testProcessSwitchDataNoMastership() {
        TopologyEvents topologyEvents;
        List<EventEntry<TopologyEvent>> events = new LinkedList<>();
        EventEntry<TopologyEvent> eventEntry;
        TopologyEvent topologyMastershipData;
        TopologyEvent topologySwitchData;

        setupTopologyManagerWithEventHandler();

        // Prepare the Mastership Event
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_2, ONOS_INSTANCE_ID_2, role);
        topologyMastershipData = new TopologyEvent(mastershipData,
                                                    ONOS_INSTANCE_ID_2);

        // Prepare the Switch Event
        // NOTE: The originator (ONOS_INSTANCE_ID_1) is NOT the Master
        SwitchData switchData = new SwitchData(DPID_2);
        topologySwitchData = new TopologyEvent(switchData,
                                                ONOS_INSTANCE_ID_1);

        // Add the Switch Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologySwitchData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: no events should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNull(topologyEvents);
        theTopologyListener.clear();
        events.clear();

        // Add the Mastership Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologyMastershipData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: only the Mastership event should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNotNull(topologyEvents);
        assertThat(topologyEvents.getAddedMastershipDataEntries(),
                   hasItem(mastershipData));
        assertThat(topologyEvents.getAddedSwitchDataEntries(), is(empty()));
        theTopologyListener.clear();
    }

    /**
     * Tests processing of Switch Events with Mastership switchover between
     * two ONOS instance, and the delivery of the topology events.
     *
     * We test the following scenario:
     * - Initially, a Mastership Event and a Switch Event from one ONOS
     *   instance are processed - both events should be delivered.
     * - Later, a Mastership Event and a Switch event from another ONOS
     *   instances are processed - both events should be delivered.
     * - Finally, a REMOVE Switch Event is received from the first ONOS
     *   instance - no event should be delivered.
     *
     * @throws RegistryException
     */
    @Test
    public void testProcessSwitchMastershipSwitchover()
                        throws RegistryException {
        TopologyEvents topologyEvents;
        List<EventEntry<TopologyEvent>> events = new LinkedList<>();
        EventEntry<TopologyEvent> eventEntry;
        TopologyEvent topologyMastershipData;
        TopologyEvent topologySwitchData;

        setupTopologyManagerWithEventHandler();

        // Prepare the Mastership Event from the first ONOS instance
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_1, ONOS_INSTANCE_ID_1, role);
        topologyMastershipData = new TopologyEvent(mastershipData,
                                                    ONOS_INSTANCE_ID_1);

        // Prepare the Switch Event from the first ONOS instance
        SwitchData switchData = new SwitchData(DPID_1);
        topologySwitchData = new TopologyEvent(switchData,
                                                ONOS_INSTANCE_ID_1);

        // Add the Mastership Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologyMastershipData);
        events.add(eventEntry);

        // Add the Switch Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologySwitchData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: both events should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNotNull(topologyEvents);
        assertThat(topologyEvents.getAddedMastershipDataEntries(),
                   hasItem(mastershipData));
        assertThat(topologyEvents.getAddedSwitchDataEntries(),
                   hasItem(switchData));
        theTopologyListener.clear();
        events.clear();

        //
        // Update the Registry Service, so the second ONOS instance is the
        // Master.
        //
        reset(registryService);
        expect(registryService.getControllerForSwitch(DPID_1.value()))
            .andReturn(ONOS_INSTANCE_ID_2.toString()).anyTimes();
        replay(registryService);

        // Prepare the Mastership Event from the second ONOS instance
        role = Role.MASTER;
        mastershipData = new MastershipData(DPID_1,
                                              ONOS_INSTANCE_ID_2, role);
        topologyMastershipData = new TopologyEvent(mastershipData,
                                                    ONOS_INSTANCE_ID_2);

        // Prepare the Switch Event from second ONOS instance
        switchData = new SwitchData(DPID_1);
        topologySwitchData = new TopologyEvent(switchData,
                                                ONOS_INSTANCE_ID_2);

        // Add the Mastership Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologyMastershipData);
        events.add(eventEntry);

        // Add the Switch Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologySwitchData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: both events should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNotNull(topologyEvents);
        assertThat(topologyEvents.getAddedMastershipDataEntries(),
                   hasItem(mastershipData));
        assertThat(topologyEvents.getAddedSwitchDataEntries(),
                   hasItem(switchData));
        theTopologyListener.clear();
        events.clear();

        // Prepare the REMOVE Switch Event from first ONOS instance
        switchData = new SwitchData(DPID_1);
        topologySwitchData = new TopologyEvent(switchData,
                                                ONOS_INSTANCE_ID_1);
        // Add the Switch Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_REMOVE,
                                                   topologySwitchData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: no events should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNull(topologyEvents);
        theTopologyListener.clear();
        events.clear();
    }

    /**
     * Tests processing of Configured Switch Events with Mastership switchover
     * between two ONOS instance, and the delivery of the topology events.
     * <p/>
     * NOTE: This test is similar to testProcessSwitchMastershipSwitchover()
     * except that the topology and all events are considered as statically
     * configured.
     * <p/>
     * We test the following scenario:
     * - Initially, a Mastership Event and a Switch Event from one ONOS
     *   instance are processed - both events should be delivered.
     * - Later, a Mastership Event and a Switch event from another ONOS
     *   instances are processed - both events should be delivered.
     */
    @Test
    public void testProcessConfiguredSwitchMastershipSwitchover() {
        TopologyEvents topologyEvents;
        List<EventEntry<TopologyEvent>> events = new LinkedList<>();
        EventEntry<TopologyEvent> eventEntry;
        TopologyEvent topologyMastershipData;
        TopologyEvent topologySwitchData;

        setupTopologyManagerWithEventHandler();

        // Reset the Registry Service so it is not used
        reset(registryService);

        // Prepare the Mastership Event from the first ONOS instance
        Role role = Role.MASTER;
        MastershipData mastershipData =
            new MastershipData(DPID_1, ONOS_INSTANCE_ID_1, role);
        mastershipData.createStringAttribute(
                TopologyElement.ELEMENT_CONFIG_STATE,
                ConfigState.CONFIGURED.toString());
        topologyMastershipData = new TopologyEvent(mastershipData,
                                                    ONOS_INSTANCE_ID_1);

        // Prepare the Switch Event from the first ONOS instance
        SwitchData switchData = new SwitchData(DPID_1);
        switchData.createStringAttribute(
                TopologyElement.ELEMENT_CONFIG_STATE,
                ConfigState.CONFIGURED.toString());
        topologySwitchData = new TopologyEvent(switchData,
                                                ONOS_INSTANCE_ID_1);

        // Add the Mastership Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologyMastershipData);
        events.add(eventEntry);

        // Add the Switch Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologySwitchData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: both events should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNotNull(topologyEvents);
        assertThat(topologyEvents.getAddedMastershipDataEntries(),
                   hasItem(mastershipData));
        assertThat(topologyEvents.getAddedSwitchDataEntries(),
                   hasItem(switchData));
        theTopologyListener.clear();
        events.clear();

        // Prepare the Mastership Event from the second ONOS instance
        role = Role.MASTER;
        mastershipData = new MastershipData(DPID_1,
                                              ONOS_INSTANCE_ID_2, role);
        mastershipData.createStringAttribute(
                TopologyElement.ELEMENT_CONFIG_STATE,
                ConfigState.CONFIGURED.toString());
        topologyMastershipData = new TopologyEvent(mastershipData,
                                                    ONOS_INSTANCE_ID_2);

        // Prepare the Switch Event from second ONOS instance
        switchData = new SwitchData(DPID_1);
        switchData.createStringAttribute(
                TopologyElement.ELEMENT_CONFIG_STATE,
                ConfigState.CONFIGURED.toString());
        topologySwitchData = new TopologyEvent(switchData,
                                                ONOS_INSTANCE_ID_2);

        // Add the Mastership Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologyMastershipData);
        events.add(eventEntry);

        // Add the Switch Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_ADD,
                                                   topologySwitchData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: both events should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNotNull(topologyEvents);
        assertThat(topologyEvents.getAddedMastershipDataEntries(),
                   hasItem(mastershipData));
        assertThat(topologyEvents.getAddedSwitchDataEntries(),
                   hasItem(switchData));
        theTopologyListener.clear();
        events.clear();

        // Prepare the REMOVE Switch Event from first ONOS instance
        //
        // NOTE: This event only is explicitly marked as NOT_CONFIGURED,
        // otherwise it will override the previous configuration events.
        //
        switchData = new SwitchData(DPID_1);
        switchData.createStringAttribute(
                TopologyElement.ELEMENT_CONFIG_STATE,
                ConfigState.NOT_CONFIGURED.toString());
        topologySwitchData = new TopologyEvent(switchData,
                                                ONOS_INSTANCE_ID_1);
        // Add the Switch Event
        eventEntry = new EventEntry<TopologyEvent>(EventEntry.Type.ENTRY_REMOVE,
                                                   topologySwitchData);
        events.add(eventEntry);

        // Process the events
        TestUtils.callMethod(theEventHandler, "processEvents",
                             List.class, events);

        // Check the fired events: no events should be fired
        topologyEvents = theTopologyListener.topologyEvents;
        assertNull(topologyEvents);
        theTopologyListener.clear();
        events.clear();
    }
}
