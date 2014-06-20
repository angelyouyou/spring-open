package net.onrc.onos.apps.forwarding;

import java.util.Collection;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.onrc.onos.core.util.FlowPath;

/**
 * Temporary interface that allows the Forwarding module to be
 * notified when a flow has been installed.
 * <p/>
 * This should be refactored to a listener framework in the future.
 */
public interface IForwardingService extends IFloodlightService {
    /**
     * Notify the Forwarding module that a collection of flows has been
     * installed in the network.
     *
     * @param installedFlowPaths the collection of FlowPaths that have
     *                           been installed in the network.
     */
    public void flowsInstalled(Collection<FlowPath> installedFlowPaths);

    /**
     * Notify the Forwarding module that a flow has expired and been
     * removed from the network.
     *
     * @param removedFlowPath The FlowPath that was removed
     */
    public void flowRemoved(FlowPath removedFlowPath);
}
