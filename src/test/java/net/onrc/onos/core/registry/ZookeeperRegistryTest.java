package net.onrc.onos.core.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.onrc.onos.core.registry.StandaloneRegistryTest.LoggingCallback;
import net.onrc.onos.core.registry.ZookeeperRegistry.SwitchLeaderListener;
import net.onrc.onos.core.util.OnosInstanceId;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.listen.ListenerContainer;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.x.discovery.ServiceCache;
import org.apache.curator.x.discovery.ServiceCacheBuilder;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.projectfloodlight.openflow.util.HexString;

/**
 * Unit test for {@link ZookeeperRegistry}.
 * NOTE: {@link FloodlightTestCase} conflicts with PowerMock. If FloodLight-related methods need to be tested,
 * implement another test class to test them.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ZookeeperRegistry.class, CuratorFramework.class, CuratorFrameworkFactory.class,
        ServiceDiscoveryBuilder.class, ServiceDiscovery.class, ServiceCache.class, PathChildrenCache.class,
        ZookeeperRegistry.SwitchPathCacheListener.class })
public class ZookeeperRegistryTest extends FloodlightTestCase {
    private static final Long ID_BLOCK_SIZE = 0x100000000L;

    protected ZookeeperRegistry registry;
    protected CuratorFramework client;

    protected PathChildrenCacheListener pathChildrenCacheListener;
    protected static final String CONTROLLER_ID = "controller2013";

    /**
     * Initialize {@link ZookeeperRegistry} Object and inject initial value with
     * {@link ZookeeperRegistry#init(FloodlightModuleContext)} method.
     * This setup code also tests {@link ZookeeperRegistry#init(FloodlightModuleContext)} method itself.
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        pathChildrenCacheListener = null;

        // Mock of CuratorFramework
        client = createCuratorFrameworkMock();

        // Mock of CuratorFrameworkFactory
        PowerMock.mockStatic(CuratorFrameworkFactory.class);
        EasyMock.expect(CuratorFrameworkFactory.newClient((String) EasyMock.anyObject(),
                EasyMock.anyInt(), EasyMock.anyInt(), (RetryPolicy) EasyMock.anyObject())).andReturn(client);
        PowerMock.replay(CuratorFrameworkFactory.class);

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        registry = new ZookeeperRegistry();
        fmc.addService(ZookeeperRegistry.class, registry);

        registry.init(fmc);

        PowerMock.verify(client, CuratorFrameworkFactory.class);
    }

    /**
     * Clean up member variables (empty for now).
     */
    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test if {@link ZookeeperRegistry#registerController(String)} method can go through without exception.
     * (Exceptions are usually out of test target,
     * but {@link ZookeeperRegistry#registerController(String)}
     * throws an exception in case of invalid registration.)
     */
    @Test
    public void testRegisterController() {
        String controllerIdToRegister = "controller2013";

        try {
            registry.registerController(controllerIdToRegister);
        } catch (RegistryException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Test if {@link ZookeeperRegistry#getOnosInstanceId()} correctly returns
     * registered ID.
     *
     * @throws Exception
     */
    @Test
    public void testGetOnosInstanceId() throws Exception {
        String controllerIdToRegister = "controller1";

        // try before controller is registered
        OnosInstanceId onosInstanceId = registry.getOnosInstanceId();
        assertNull(onosInstanceId);

        // register
        registry.registerController(controllerIdToRegister);

        // call getOnosInstanceId and verify
        onosInstanceId = registry.getOnosInstanceId();
        assertNotNull(onosInstanceId);
        assertEquals(controllerIdToRegister, onosInstanceId.toString());
    }

    /**
     * Test if {@link ZookeeperRegistry#getAllControllers()} returns all controllers.
     * Controllers to be returned are injected while setup.
     * See {@link ZookeeperRegistryTest#createCuratorFrameworkMock()}
     * to what controllers are injected using mock {@link ServiceCache}.
     *
     * @throws Exception
     */
    @Test
    public void testGetAllControllers() throws Exception {
        String controllerIdRegistered = "controller1";
        String controllerIdNotRegistered = "controller2013";

        try {
            Collection<String> ctrls = registry.getAllControllers();
            assertTrue(ctrls.contains(controllerIdRegistered));
            assertFalse(ctrls.contains(controllerIdNotRegistered));
        } catch (RegistryException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Test if {@link ZookeeperRegistry#requestControl(long, IControllerRegistryService.ControlChangeCallback)}
     * correctly take control of specific switch.
     * Because {@link ZookeeperRegistry#requestControl(long, IControllerRegistryService.ControlChangeCallback)}
     * doesn't return values, inject mock {@link LeaderLatch} object and verify latch is correctly set up.
     *
     * @throws Exception
     */
    @Test
    public void testRequestControl() throws Exception {
        // Mock LeaderLatch
        LeaderLatch latch = EasyMock.createMock(LeaderLatch.class);
        latch.addListener(EasyMock.anyObject(SwitchLeaderListener.class));
        EasyMock.expectLastCall().once();
        latch.start();
        EasyMock.expectLastCall().once();
        EasyMock.replay(latch);

        PowerMock.expectNew(LeaderLatch.class,
                EasyMock.anyObject(CuratorFramework.class),
                EasyMock.anyObject(String.class),
                EasyMock.anyObject(String.class))
                .andReturn(latch).once();
        PowerMock.replay(LeaderLatch.class);

        String controllerId = "controller2013";
        registry.registerController(controllerId);

        LoggingCallback callback = new LoggingCallback(1);
        long dpidToRequest = 2000L;

        try {
            registry.requestControl(dpidToRequest, callback);
        } catch (RegistryException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        EasyMock.verify(latch);
    }

    /**
     * Test if {@link ZookeeperRegistry#releaseControl(long)} correctly release control of specific switch.
     * Because {@link ZookeeperRegistry#releaseControl(long)} doesn't return values, inject mock
     * {@link LeaderLatch} object and verify latch is correctly set up.
     *
     * @throws Exception
     */
    @Test
    public void testReleaseControl() throws Exception {
        // Mock of LeaderLatch
        LeaderLatch latch = EasyMock.createMock(LeaderLatch.class);
        latch.addListener(EasyMock.anyObject(SwitchLeaderListener.class));
        EasyMock.expectLastCall().once();
        latch.start();
        EasyMock.expectLastCall().once();
        latch.removeListener(EasyMock.anyObject(SwitchLeaderListener.class));
        EasyMock.expectLastCall().once();
        latch.close();
        EasyMock.expectLastCall().once();
        EasyMock.replay(latch);

        PowerMock.expectNew(LeaderLatch.class,
                EasyMock.anyObject(CuratorFramework.class),
                EasyMock.anyObject(String.class),
                EasyMock.anyObject(String.class))
                .andReturn(latch).once();
        PowerMock.replay(LeaderLatch.class);

        String controllerId = "controller2013";
        registry.registerController(controllerId);

        long dpidToRequest = 2000L;
        LoggingCallback callback = new LoggingCallback(1);

        registry.requestControl(dpidToRequest, callback);
        registry.releaseControl(dpidToRequest);

        EasyMock.verify(latch);
    }

    /**
     * Test if {@link ZookeeperRegistry#hasControl(long)} returns
     * correct status whether controller has control of specific switch.
     *
     * @throws Exception
     */
    @Test
    public void testHasControl() throws Exception {
        // Mock of LeaderLatch
        LeaderLatch latch = EasyMock.createMock(LeaderLatch.class);
        latch.addListener(EasyMock.anyObject(SwitchLeaderListener.class));
        EasyMock.expectLastCall().once();
        latch.start();
        EasyMock.expectLastCall().once();
        EasyMock.expect(latch.hasLeadership()).andReturn(true).anyTimes();
        latch.removeListener(EasyMock.anyObject(SwitchLeaderListener.class));
        EasyMock.expectLastCall().once();
        latch.close();
        EasyMock.expectLastCall().once();
        EasyMock.replay(latch);

        PowerMock.expectNew(LeaderLatch.class,
                EasyMock.anyObject(CuratorFramework.class),
                EasyMock.anyObject(String.class),
                EasyMock.anyObject(String.class))
                .andReturn(latch);
        PowerMock.replay(LeaderLatch.class);

        String controllerId = "controller2013";
        registry.registerController(controllerId);

        long dpidToRequest = 2000L;
        LoggingCallback callback = new LoggingCallback(2);

        // Test before request control
        assertFalse(registry.hasControl(dpidToRequest));

        registry.requestControl(dpidToRequest, callback);

        // Test after request control
        assertTrue(registry.hasControl(dpidToRequest));

        registry.releaseControl(dpidToRequest);

        // Test after release control
        assertFalse(registry.hasControl(dpidToRequest));

        EasyMock.verify(latch);
    }

    /**
     * Test if {@link ZookeeperRegistry#getControllerForSwitch(long)}
     * correctly returns controller ID of specific switch.
     * Relation between controllers and switches are defined by
     * {@link ZookeeperRegistryTest#setPathChildrenCache()} function.
     *
     * @throws Throwable
     */
    @Test
    public void testGetControllerForSwitch() throws Throwable {
        long dpidRegistered = 1000L;
        long dpidNotRegistered = 2000L;

        setPathChildrenCache();

        String controllerForSw = registry.getControllerForSwitch(dpidRegistered);
        assertEquals("controller1", controllerForSw);

        controllerForSw = registry.getControllerForSwitch(dpidNotRegistered);
        assertEquals(null, controllerForSw);
    }

    /**
     * Test if {@link ZookeeperRegistry#getSwitchesControlledByController(String)} returns correct list of
     * switches controlled by a controller.
     *
     * @throws Exception
     */
    // TODO: Test after getSwitchesControlledByController() is implemented.
    @Ignore
    @Test
    public void testGetSwitchesControlledByController() throws Exception {
        String controllerIdRegistered = "controller1";
        String dpidRegistered = HexString.toHexString(1000L);
        String controllerIdNotRegistered = CONTROLLER_ID;

        Collection<Long> switches = registry.getSwitchesControlledByController(controllerIdRegistered);
        assertNotNull(switches);
        assertTrue(switches.contains(dpidRegistered));

        switches = registry.getSwitchesControlledByController(controllerIdNotRegistered);
        assertNotNull(switches);
        assertEquals(0, switches.size());
    }

    /**
     * Test if {@link ZookeeperRegistry#getAllSwitches()} returns correct list of all switches.
     * Switches are injected in {@link ZookeeperRegistryTest#setPathChildrenCache()} function.
     *
     * @throws Exception
     */
    @Test
    public void testGetAllSwitches() throws Exception {
        String[] dpids = {
                HexString.toHexString(1000L),
                HexString.toHexString(1001L),
                HexString.toHexString(1002L),
        };

        setPathChildrenCache();

        Map<String, List<ControllerRegistryEntry>> switches = registry.getAllSwitches();
        assertNotNull(switches);
        assertEquals(dpids.length, switches.size());
        for (String dpid : dpids) {
            assertTrue(switches.keySet().contains(dpid));
        }
    }

    /**
     * Test if {@link ZookeeperRegistry#allocateUniqueIdBlock()} can assign IdBlock without duplication.
     */
    @Test
    public void testAllocateUniqueIdBlock() {
        // Number of blocks to be verified that any of them has unique block
        final int numBlocks = 100;
        ArrayList<IdBlock> blocks = new ArrayList<IdBlock>(numBlocks);

        for (int i = 0; i < numBlocks; ++i) {
            IdBlock block = registry.allocateUniqueIdBlock();
            assertNotNull(block);
            blocks.add(block);
        }

        for (int i = 0; i < numBlocks; ++i) {
            IdBlock block1 = blocks.get(i);
            for (int j = i + 1; j < numBlocks; ++j) {
                IdBlock block2 = blocks.get(j);
                IdBlock lower, higher;

                if (block1.getStart() < block2.getStart()) {
                    lower = block1;
                    higher = block2;
                } else {
                    lower = block2;
                    higher = block1;
                }

                assertTrue(lower.getSize() > 0L);
                assertTrue(higher.getSize() > 0L);
                assertTrue(lower.getEnd() <= higher.getStart());
            }
        }
    }


    //-------------------------- Creation of mock objects --------------------------

    /**
     * Create mock {@link CuratorFramework} object with initial value below.<br>
     * [Ctrl ID]    : [DPID]<br>
     * controller1    :  1000<br>
     * controller2    :  1001<br>
     * controller2    :  1002<br>
     * controller2013 : nothing
     *
     * @return Created mock object
     * @throws Exception
     */
    @SuppressWarnings({"serial", "unchecked" })
    private CuratorFramework createCuratorFrameworkMock() throws Exception {
        // Mock of AtomicValue
        AtomicValue<Long> atomicValue = EasyMock.createMock(AtomicValue.class);
        EasyMock.expect(atomicValue.succeeded()).andReturn(true).anyTimes();
        EasyMock.expect(atomicValue.preValue()).andAnswer(new IAnswer<Long>() {
            private long value = 0;

            @Override
            public Long answer() throws Throwable {
                value += ID_BLOCK_SIZE;
                return value;
            }
        }).anyTimes();
        EasyMock.expect(atomicValue.postValue()).andAnswer(new IAnswer<Long>() {
            private long value = ID_BLOCK_SIZE;

            @Override
            public Long answer() throws Throwable {
                value += ID_BLOCK_SIZE;
                return value;
            }
        }).anyTimes();
        EasyMock.replay(atomicValue);

        // Mock DistributedAtomicLong
        DistributedAtomicLong daLong = EasyMock.createMock(DistributedAtomicLong.class);
        EasyMock.expect(daLong.add(EasyMock.anyLong())).andReturn(atomicValue).anyTimes();
        EasyMock.replay(daLong);
        PowerMock.expectNew(DistributedAtomicLong.class,
                new Class<?>[]{CuratorFramework.class, String.class, RetryPolicy.class},
                EasyMock.anyObject(CuratorFramework.class),
                EasyMock.anyObject(String.class),
                EasyMock.anyObject(RetryPolicy.class)).
                andReturn(daLong).anyTimes();
        PowerMock.replay(DistributedAtomicLong.class);

        // Mock ListenerContainer
        ListenerContainer<PathChildrenCacheListener> listenerContainer = EasyMock.createMock(ListenerContainer.class);
        listenerContainer.addListener(EasyMock.anyObject(PathChildrenCacheListener.class));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                pathChildrenCacheListener = (PathChildrenCacheListener) EasyMock.getCurrentArguments()[0];
                return null;
            }
        }).once();
        EasyMock.replay(listenerContainer);

        // Mock PathChildrenCache
        PathChildrenCache pathChildrenCacheMain = createPathChildrenCacheMock(
                CONTROLLER_ID, new String[] {"/switches"}, listenerContainer);
        PathChildrenCache pathChildrenCache1 = createPathChildrenCacheMock("controller1",
                new String[] {HexString.toHexString(1000L)}, listenerContainer);
        PathChildrenCache pathChildrenCache2 = createPathChildrenCacheMock("controller2",
                new String[] {
                        HexString.toHexString(1001L), HexString.toHexString(1002L)},
                listenerContainer);

        // Mock PathChildrenCache constructor
        PowerMock.expectNew(PathChildrenCache.class,
                EasyMock.anyObject(CuratorFramework.class), EasyMock.anyObject(String.class), EasyMock.anyBoolean()).
                andReturn(pathChildrenCacheMain).once();
        PowerMock.expectNew(PathChildrenCache.class,
                EasyMock.anyObject(CuratorFramework.class), EasyMock.anyObject(String.class), EasyMock.anyBoolean()).
                andReturn(pathChildrenCache1).once();
        PowerMock.expectNew(PathChildrenCache.class,
                EasyMock.anyObject(CuratorFramework.class), EasyMock.anyObject(String.class), EasyMock.anyBoolean()).
                andReturn(pathChildrenCache2).anyTimes();
        PowerMock.replay(PathChildrenCache.class);

        // Mock ServiceCache
        ServiceCache<ControllerService> serviceCache = EasyMock.createMock(ServiceCache.class);
        serviceCache.start();
        EasyMock.expectLastCall().once();
        EasyMock.expect(serviceCache.getInstances()).andReturn(new ArrayList<ServiceInstance<ControllerService>>() { {
            add(createServiceInstanceMock("controller1"));
            add(createServiceInstanceMock("controller2"));
        } }).anyTimes();
        EasyMock.replay(serviceCache);

        // Mock ServiceCacheBuilder
        ServiceCacheBuilder<ControllerService> serviceCacheBuilder = EasyMock.createMock(ServiceCacheBuilder.class);
        EasyMock.expect(serviceCacheBuilder.name(EasyMock.anyObject(String.class)))
            .andReturn(serviceCacheBuilder).once();
        EasyMock.expect(serviceCacheBuilder.build()).andReturn(serviceCache).once();
        EasyMock.replay(serviceCacheBuilder);

        // Mock ServiceDiscovery
        ServiceDiscovery<ControllerService> serviceDiscovery = EasyMock.createMock(ServiceDiscovery.class);
        serviceDiscovery.start();
        EasyMock.expectLastCall().once();
        EasyMock.expect(serviceDiscovery.serviceCacheBuilder()).andReturn(serviceCacheBuilder).once();
        serviceDiscovery.registerService(EasyMock.anyObject(ServiceInstance.class));
        EasyMock.expectLastCall().once();
        EasyMock.replay(serviceDiscovery);

        // Mock CuratorFramework
        CuratorFramework mockClient = EasyMock.createMock(CuratorFramework.class);
        mockClient.start();
        EasyMock.expectLastCall().once();
        EasyMock.expect(mockClient.usingNamespace(EasyMock.anyObject(String.class))).andReturn(mockClient);
        EasyMock.replay(mockClient);

        // Mock ServiceDiscoveryBuilder
        ServiceDiscoveryBuilder<ControllerService> builder = EasyMock.createMock(ServiceDiscoveryBuilder.class);
        EasyMock.expect(builder.client(mockClient)).andReturn(builder).once();
        EasyMock.expect(builder.basePath(EasyMock.anyObject(String.class))).andReturn(builder);
        EasyMock.expect(builder.build()).andReturn(serviceDiscovery);
        EasyMock.replay(builder);

        PowerMock.mockStatic(ServiceDiscoveryBuilder.class);
        EasyMock.expect(ServiceDiscoveryBuilder.builder(ControllerService.class)).andReturn(builder).once();
        PowerMock.replay(ServiceDiscoveryBuilder.class);

        return mockClient;
    }

    /**
     * Create mock {@link ServiceInstance} object using given controller ID.
     *
     * @param controllerId Controller ID to represent instance's payload (ControllerService).
     * @return Mock ServiceInstance object
     */
    private ServiceInstance<ControllerService> createServiceInstanceMock(String controllerId) {
        ControllerService controllerService = EasyMock.createMock(ControllerService.class);
        EasyMock.expect(controllerService.getControllerId()).andReturn(controllerId).anyTimes();
        EasyMock.replay(controllerService);

        @SuppressWarnings("unchecked")
        ServiceInstance<ControllerService> serviceInstance = EasyMock.createMock(ServiceInstance.class);
        EasyMock.expect(serviceInstance.getPayload()).andReturn(controllerService).anyTimes();
        EasyMock.replay(serviceInstance);

        return serviceInstance;
    }

    /**
     * Create mock {@link PathChildrenCache} using given controller ID and DPIDs.
     *
     * @param controllerId Controller ID to represent current data.
     * @param paths        List of HexString indicating switch's DPID.
     * @param listener     Callback object to be set as Listenable.
     * @return Mock PathChildrenCache object
     * @throws Exception
     */
    private PathChildrenCache createPathChildrenCacheMock(
                final String controllerId,
                final String[] paths,
                ListenerContainer<PathChildrenCacheListener> listener)
                    throws Exception {
        PathChildrenCache pathChildrenCache = EasyMock.createMock(PathChildrenCache.class);

        EasyMock.expect(pathChildrenCache.getListenable()).andReturn(listener).anyTimes();

        pathChildrenCache.start(EasyMock.anyObject(StartMode.class));
        EasyMock.expectLastCall().anyTimes();

        List<ChildData> childs = new ArrayList<ChildData>();
        for (String path : paths) {
            childs.add(createChildDataMockForCurrentData(controllerId, path));
        }
        EasyMock.expect(pathChildrenCache.getCurrentData()).andReturn(childs).anyTimes();

        pathChildrenCache.rebuild();
        EasyMock.expectLastCall().anyTimes();

        EasyMock.replay(pathChildrenCache);

        return pathChildrenCache;
    }

    /**
     * Create mock {@link ChildData} for {@link PathChildrenCache#getCurrentData()} return value.
     * This object need to include 'sequence number' in tail of path string. ("-0" means 0th sequence)
     *
     * @param controllerId Controller ID
     * @param path         HexString indicating switch's DPID
     * @return Mock ChildData object
     */
    private ChildData createChildDataMockForCurrentData(String controllerId, String path) {
        ChildData data = EasyMock.createMock(ChildData.class);
        EasyMock.expect(data.getPath()).andReturn(path + "-0").anyTimes();
        EasyMock.expect(data.getData()).andReturn(controllerId.getBytes()).anyTimes();
        EasyMock.replay(data);

        return data;
    }

    /**
     * Inject relations between controllers and switches using callback object.
     *
     * @throws Exception
     */
    private void setPathChildrenCache() throws Exception {
        pathChildrenCacheListener.childEvent(
                client,
                createChildrenCacheEventMock("controller1", HexString.toHexString(1000L),
                        PathChildrenCacheEvent.Type.CHILD_ADDED));
        pathChildrenCacheListener.childEvent(
                client,
                createChildrenCacheEventMock("controller2", HexString.toHexString(1001L),
                        PathChildrenCacheEvent.Type.CHILD_ADDED));
        pathChildrenCacheListener.childEvent(
                client,
                createChildrenCacheEventMock("controller2", HexString.toHexString(1002L),
                        PathChildrenCacheEvent.Type.CHILD_ADDED));
    }

    /**
     * Create mock {@link PathChildrenCacheEvent} object using given controller ID and DPID.
     *
     * @param controllerId Controller ID.
     * @param path         HexString of DPID.
     * @param type         Event type to be set to mock object.
     * @return Mock PathChildrenCacheEvent object
     */
    private PathChildrenCacheEvent createChildrenCacheEventMock(String controllerId, String path,
                                                                PathChildrenCacheEvent.Type type) {
        PathChildrenCacheEvent event = EasyMock.createMock(PathChildrenCacheEvent.class);
        ChildData data = EasyMock.createMock(ChildData.class);

        EasyMock.expect(data.getPath()).andReturn(path).anyTimes();
        EasyMock.expect(data.getData()).andReturn(controllerId.getBytes()).anyTimes();
        EasyMock.replay(data);

        EasyMock.expect(event.getType()).andReturn(type).anyTimes();
        EasyMock.expect(event.getData()).andReturn(data).anyTimes();
        EasyMock.replay(event);

        return event;
    }
}
