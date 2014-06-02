package net.onrc.onos.core.devicemanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IUpdate;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.util.MACAddress;
import net.onrc.onos.core.datagrid.IDatagridService;
import net.onrc.onos.core.datagrid.IEventChannel;
import net.onrc.onos.core.datagrid.IEventChannelListener;
import net.onrc.onos.core.packet.Ethernet;
import net.onrc.onos.core.topology.ITopologyService;
import net.onrc.onos.core.topology.Topology;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnosDeviceManager implements IFloodlightModule,
        IOFMessageListener,
        IOnosDeviceService,
        IEventChannelListener<Long, OnosDevice> {

    private static final Logger log = LoggerFactory.getLogger(OnosDeviceManager.class);
    private static final long DEVICE_CLEANING_INITIAL_DELAY = 30;
    private int cleanupSecondConfig = 60 * 60;
    private int agingMillisecConfig = 60 * 60 * 1000;

    private CopyOnWriteArrayList<IOnosDeviceListener> deviceListeners;
    private IFloodlightProviderService floodlightProvider;
    private static final ScheduledExecutorService EXECUTOR_SERVICE =
            Executors.newSingleThreadScheduledExecutor();

    // TODO This infrastructure maintains a global device cache in the
    // OnosDeviceManager module on each instance (in mapDevice). We want to
    // remove this eventually - the global cache should be maintained by the
    // topology layer (which it currently is as well).
    private IDatagridService datagrid;
    private IEventChannel<Long, OnosDevice> eventChannel;
    private static final String DEVICE_CHANNEL_NAME = "onos.device";
    private final Map<Long, OnosDevice> mapDevice =
            new ConcurrentHashMap<Long, OnosDevice>();

    private ITopologyService topologyService;
    private Topology topology;

    public enum OnosDeviceUpdateType {
        ADD, DELETE, UPDATE;
    }

    private class OnosDeviceUpdate implements IUpdate {
        private final OnosDevice device;
        private final OnosDeviceUpdateType type;

        public OnosDeviceUpdate(OnosDevice device, OnosDeviceUpdateType type) {
            this.device = device;
            this.type = type;
        }

        @Override
        public void dispatch() {
            if (type == OnosDeviceUpdateType.ADD) {
                for (IOnosDeviceListener listener : deviceListeners) {
                    listener.onosDeviceAdded(device);
                }
            } else if (type == OnosDeviceUpdateType.DELETE) {
                for (IOnosDeviceListener listener : deviceListeners) {
                    listener.onosDeviceRemoved(device);
                }
            }
        }
    }

    @Override
    public String getName() {
        return "onosdevicemanager";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // We want link discovery to consume LLDP first otherwise we'll
        // end up reading bad device info from LLDP packets
        return type == OFType.PACKET_IN && "linkdiscovery".equals(name);
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return type == OFType.PACKET_IN &&
                ("proxyarpmanager".equals(name) || "onosforwarding".equals(name));
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        if (msg.getType().equals(OFType.PACKET_IN) &&
                (msg instanceof OFPacketIn)) {
            OFPacketIn pi = (OFPacketIn) msg;

            Ethernet eth = IFloodlightProviderService.bcStore.
                    get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

            return processPacketIn(sw, pi, eth);
        }

        return Command.CONTINUE;
    }

    // This "protected" modifier is for unit test.
    // The above "receive" method couldn't be tested
    // because of IFloodlightProviderService static final field.
    protected Command processPacketIn(IOFSwitch sw, OFPacketIn pi, Ethernet eth) {
        long dpid = sw.getId();
        short portId = pi.getInPort();
        Long mac = eth.getSourceMAC().toLong();

        OnosDevice srcDevice =
                getSourceDeviceFromPacket(eth, dpid, portId);

        if (srcDevice == null) {
            return Command.STOP;
        }

        // We check if it is the same device in datagrid to suppress the device update
        OnosDevice exDev = mapDevice.get(mac);
        if (exDev != null && exDev.equals(srcDevice)) {
            // There is the same existing device. Update only ActiveSince time.
            // TODO This doesn't update the timestamp in the Topology module,
            // only in the local cache in this local driver module.
            exDev.setLastSeenTimestamp(new Date());
            if (log.isTraceEnabled()) {
                log.trace("In the local cache, there is the same device."
                        + " Only update last seen time: {}", exDev);
            }
            return Command.CONTINUE;
        }

        // If the switch port we try to attach a new device already has a link,
        // then don't add the device
        // TODO We probably don't need to check this here, it should be done in
        // the Topology module.
        if (topology.getOutgoingLink(dpid, (long) portId) != null) {
            if (log.isTraceEnabled()) {
                log.trace("Stop adding OnosDevice {} as " +
                        "there is a link on the port: dpid {} port {}",
                        srcDevice.getMacAddress(), dpid, portId);
            }
            return Command.CONTINUE;
        }

        addOnosDevice(mac, srcDevice);

        if (log.isTraceEnabled()) {
            log.trace("Add device info: {}", srcDevice);
        }
        return Command.CONTINUE;
    }

    // Thread to delete devices periodically.
    // Remove all devices from the map first and then finally delete devices
    // from the DB.

    // TODO This should be sharded based on device 'owner' (i.e. the instance
    // that owns the switch it is attached to). Currently any instance can
    // issue deletes for any device, which permits race conditions and could
    // cause the Topology replicas to diverge.
    private class CleanDevice implements Runnable {
        @Override
        public void run() {
            log.debug("called CleanDevice");
            try {
                Set<OnosDevice> deleteSet = new HashSet<OnosDevice>();
                for (OnosDevice dev : mapDevice.values()) {
                    long now = new Date().getTime();
                    if ((now - dev.getLastSeenTimestamp().getTime()
                            > agingMillisecConfig)) {
                        if (log.isTraceEnabled()) {
                            log.debug("Removing device info from the datagrid: {}, diff {}",
                                    dev, now - dev.getLastSeenTimestamp().getTime());
                        }
                        deleteSet.add(dev);
                    }
                }

                for (OnosDevice dev : deleteSet) {
                    deleteOnosDevice(dev);
                }
            } catch (Exception e) {
                // Any exception thrown by the task will prevent the Executor
                // from running the next iteration, so we need to catch and log
                // all exceptions here.
                log.error("Exception in device cleanup thread:", e);
            }
        }
    }

    /**
     * Parse a device from an {@link Ethernet} packet.
     *
     * @param eth the packet to parse
     * @param swdpid the switch on which the packet arrived
     * @param port the port on which the packet arrived
     * @return the device from the packet
     */
    protected OnosDevice getSourceDeviceFromPacket(Ethernet eth,
            long swdpid,
            short port) {
        MACAddress sourceMac = eth.getSourceMAC();

        // Ignore broadcast/multicast source
        if (sourceMac.isBroadcast() || sourceMac.isBroadcast()) {
            return null;
        }

        short vlan = eth.getVlanID();
        return new OnosDevice(sourceMac,
                ((vlan >= 0) ? vlan : null),
                swdpid,
                port,
                new Date());
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        List<Class<? extends IFloodlightService>> services =
                new ArrayList<Class<? extends IFloodlightService>>();
        services.add(IOnosDeviceService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> impls =
                new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        impls.put(IOnosDeviceService.class, this);
        return impls;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        List<Class<? extends IFloodlightService>> dependencies =
                new ArrayList<Class<? extends IFloodlightService>>();
        dependencies.add(IFloodlightProviderService.class);
        dependencies.add(ITopologyService.class);
        dependencies.add(IDatagridService.class);
        return dependencies;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        deviceListeners = new CopyOnWriteArrayList<IOnosDeviceListener>();
        datagrid = context.getServiceImpl(IDatagridService.class);
        topologyService = context.getServiceImpl(ITopologyService.class);
        topology = topologyService.getTopology();

        setOnosDeviceManagerProperty(context);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        eventChannel = datagrid.addListener(DEVICE_CHANNEL_NAME, this,
                Long.class,
                OnosDevice.class);
        EXECUTOR_SERVICE.scheduleAtFixedRate(new CleanDevice(),
                DEVICE_CLEANING_INITIAL_DELAY, cleanupSecondConfig, TimeUnit.SECONDS);
    }

    @Override
    public void deleteOnosDevice(OnosDevice dev) {
        Long mac = dev.getMacAddress().toLong();
        eventChannel.removeEntry(mac);
        floodlightProvider.publishUpdate(
                new OnosDeviceUpdate(dev, OnosDeviceUpdateType.DELETE));
    }

    @Override
    public void deleteOnosDeviceByMac(MACAddress mac) {
        OnosDevice deleteDevice = mapDevice.get(mac.toLong());
        deleteOnosDevice(deleteDevice);
    }

    @Override
    public void addOnosDevice(Long mac, OnosDevice dev) {
        eventChannel.addEntry(mac, dev);
        floodlightProvider.publishUpdate(
                new OnosDeviceUpdate(dev, OnosDeviceUpdateType.ADD));
    }

    @Override
    public void entryAdded(OnosDevice dev) {
        Long mac = dev.getMacAddress().toLong();
        mapDevice.put(mac, dev);
        log.debug("Device added into local Cache: device mac {}", mac);
    }

    @Override
    public void entryRemoved(OnosDevice dev) {
        Long mac = dev.getMacAddress().toLong();
        mapDevice.remove(mac);
        log.debug("Device removed into local Cache: device mac {}", mac);
    }

    @Override
    public void entryUpdated(OnosDevice dev) {
        Long mac = dev.getMacAddress().toLong();
        mapDevice.put(mac, dev);
        log.debug("Device updated into local Cache: device mac {}", mac);
    }

    @Override
    public void addOnosDeviceListener(IOnosDeviceListener listener) {
        deviceListeners.add(listener);
    }

    @Override
    public void deleteOnosDeviceListener(IOnosDeviceListener listener) {
        deviceListeners.remove(listener);
    }

    private void setOnosDeviceManagerProperty(FloodlightModuleContext context) {
        Map<String, String> configOptions = context.getConfigParams(this);
        String cleanupsec = configOptions.get("cleanupsec");
        String agingmsec = configOptions.get("agingmsec");
        if (cleanupsec != null) {
            cleanupSecondConfig = Integer.parseInt(cleanupsec);
            log.debug("CLEANUP_SECOND is set to {}", cleanupSecondConfig);
        }

        if (agingmsec != null) {
            agingMillisecConfig = Integer.parseInt(agingmsec);
            log.debug("AGEING_MILLSEC is set to {}", agingMillisecConfig);
        }
    }
}
