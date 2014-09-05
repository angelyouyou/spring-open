package net.onrc.onos.core.main.config;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.util.MACAddress;
import net.onrc.onos.apps.sdnip.Interface;
import net.onrc.onos.core.util.SwitchPort;

import org.projectfloodlight.openflow.util.HexString;

public class DefaultConfiguration implements IConfigInfoService, IFloodlightModule {

    @Override
    public boolean isInterfaceAddress(InetAddress address) {
        return false;
    }

    @Override
    public boolean inConnectedNetwork(InetAddress address) {
        return false;
    }

    @Override
    public boolean fromExternalNetwork(long inDpid, short inPort) {
        return false;
    }

    @Override
    public Interface getOutgoingInterface(InetAddress dstIpAddress) {
        return null;
    }

    @Override
    public boolean hasLayer3Configuration() {
        return false;
    }

    @Override
    public MACAddress getRouterMacAddress() {
        return MACAddress.valueOf(HexString.fromHexString("000000000001"));
    }

    @Override
    public short getVlan() {
        return 0;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l
                = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IConfigInfoService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m
                = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IConfigInfoService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        // no-op
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // no-op
    }

    @Override
    public Set<SwitchPort> getExternalSwitchPorts() {
        return null;
    }

}
