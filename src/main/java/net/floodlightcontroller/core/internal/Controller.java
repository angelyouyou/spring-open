/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by David Erickson, Stanford University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.core.internal;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchFilter;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IUpdate;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.internal.OFChannelState.HandshakeState;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.web.CoreWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.onrc.onos.core.linkdiscovery.ILinkDiscoveryService;
import net.onrc.onos.core.main.IOFSwitchPortListener;
import net.onrc.onos.core.packet.Ethernet;
import net.onrc.onos.core.registry.IControllerRegistryService;
import net.onrc.onos.core.registry.IControllerRegistryService.ControlChangeCallback;
import net.onrc.onos.core.registry.RegistryException;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelUpstreamHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.openflow.protocol.OFEchoReply;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFError.OFBadActionCode;
import org.openflow.protocol.OFError.OFBadRequestCode;
import org.openflow.protocol.OFError.OFErrorType;
import org.openflow.protocol.OFError.OFFlowModFailedCode;
import org.openflow.protocol.OFError.OFHelloFailedCode;
import org.openflow.protocol.OFError.OFPortModFailedCode;
import org.openflow.protocol.OFError.OFQueueOpFailedCode;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFGetConfigReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFSetConfig;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFSwitchConfig;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.MessageParseException;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.protocol.vendor.OFBasicVendorDataType;
import org.openflow.protocol.vendor.OFBasicVendorId;
import org.openflow.protocol.vendor.OFVendorId;
import org.openflow.util.HexString;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleReplyVendorData;
import org.openflow.vendor.nicira.OFRoleRequestVendorData;
import org.openflow.vendor.nicira.OFRoleVendorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The main controller class.  Handles all setup and network listeners
 * <p/>
 * Extensions made by ONOS are:
 * - Detailed Port event: PORTCHANGED -> {PORTCHANGED, PORTADDED, PORTREMOVED}
 * Available as net.onrc.onos.core.main.IOFSwitchPortListener
 * - Distributed ownership control of switch through RegistryService(IControllerRegistryService)
 * - Register ONOS services. (IControllerRegistryService)
 * - Additional DEBUG logs
 * - Try using hostname as controller ID, when ID was not explicitly given.
 */
public class Controller implements IFloodlightProviderService {

    protected final static Logger log = LoggerFactory.getLogger(Controller.class);

    private static final String ERROR_DATABASE =
            "The controller could not communicate with the system database.";

    protected BasicFactory factory;
    protected ConcurrentMap<OFType,
            ListenerDispatcher<OFType, IOFMessageListener>>
            messageListeners;
    // The activeSwitches map contains only those switches that are actively
    // being controlled by us -- it doesn't contain switches that are
    // in the slave role
    protected ConcurrentHashMap<Long, IOFSwitch> activeSwitches;
    // connectedSwitches contains all connected switches, including ones where
    // we're a slave controller. We need to keep track of them so that we can
    // send role request messages to switches when our role changes to master
    // We add a switch to this set after it successfully completes the
    // handshake. Access to this Set needs to be synchronized with roleChanger
    protected HashSet<OFSwitchImpl> connectedSwitches;

    // The controllerNodeIPsCache maps Controller IDs to their IP address. 
    // It's only used by handleControllerNodeIPsChanged
    protected HashMap<String, String> controllerNodeIPsCache;

    protected Set<IOFSwitchListener> switchListeners;
    protected BlockingQueue<IUpdate> updates;

    // Module dependencies
    protected IRestApiService restApi;
    protected IThreadPoolService threadPool;
    protected IControllerRegistryService registryService;

    protected ILinkDiscoveryService linkDiscovery;

    // Configuration options
    protected int openFlowPort = 6633;
    protected int workerThreads = 0;
    // The id for this controller node. Should be unique for each controller
    // node in a controller cluster.
    protected String controllerId = "localhost";

    // The current role of the controller.
    // If the controller isn't configured to support roles, then this is null.
    protected Role role;
    // A helper that handles sending and timeout handling for role requests
    protected RoleChanger roleChanger;

    // Start time of the controller
    protected long systemStartTime;

    // Flag to always flush flow table on switch reconnect (HA or otherwise)
    protected boolean alwaysClearFlowsOnSwAdd = false;

    // Perf. related configuration
    protected static final int SEND_BUFFER_SIZE = 4 * 1024 * 1024;
    protected static final int BATCH_MAX_SIZE = 100;
    protected static final boolean ALWAYS_DECODE_ETH = true;

    public enum SwitchUpdateType {
        ADDED,
        REMOVED,
        PORTCHANGED,
        PORTADDED,
        PORTREMOVED
    }

    /**
     * Update message indicating a switch was added or removed
     * ONOS: This message extended to indicate Port add or removed event.
     */
    protected class SwitchUpdate implements IUpdate {
        public IOFSwitch sw;
        public OFPhysicalPort port; // Added by ONOS
        public SwitchUpdateType switchUpdateType;

        public SwitchUpdate(IOFSwitch sw, SwitchUpdateType switchUpdateType) {
            this.sw = sw;
            this.switchUpdateType = switchUpdateType;
        }

        public SwitchUpdate(IOFSwitch sw, OFPhysicalPort port, SwitchUpdateType switchUpdateType) {
            this.sw = sw;
            this.port = port;
            this.switchUpdateType = switchUpdateType;
        }

        public void dispatch() {
            if (log.isTraceEnabled()) {
                log.trace("Dispatching switch update {} {}",
                        sw, switchUpdateType);
            }
            if (switchListeners != null) {
                for (IOFSwitchListener listener : switchListeners) {
                    switch (switchUpdateType) {
                        case ADDED:
                            listener.addedSwitch(sw);
                            break;
                        case REMOVED:
                            listener.removedSwitch(sw);
                            break;
                        case PORTCHANGED:
                            listener.switchPortChanged(sw.getId());
                            break;
                        case PORTADDED:
                            if (listener instanceof IOFSwitchPortListener) {
                                ((IOFSwitchPortListener) listener).switchPortAdded(sw.getId(), port);
                            }
                            break;
                        case PORTREMOVED:
                            if (listener instanceof IOFSwitchPortListener) {
                                ((IOFSwitchPortListener) listener).switchPortRemoved(sw.getId(), port);
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    // ***************
    // Getters/Setters
    // *************** 

    public void setRestApiService(IRestApiService restApi) {
        this.restApi = restApi;
    }

    public void setThreadPoolService(IThreadPoolService tp) {
        this.threadPool = tp;
    }

    public void setMastershipService(IControllerRegistryService serviceImpl) {
        this.registryService = serviceImpl;
    }

    public void setLinkDiscoveryService(ILinkDiscoveryService linkDiscovery) {
        this.linkDiscovery = linkDiscovery;
    }

    public void publishUpdate(IUpdate update) {
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            log.error("Failure adding update to queue", e);
        }
    }

    // **********************
    // ChannelUpstreamHandler
    // **********************

    /**
     * Return a new channel handler for processing a switch connections
     *
     * @param state The channel state object for the connection
     * @return the new channel handler
     */
    protected ChannelUpstreamHandler getChannelHandler(OFChannelState state) {
        return new OFChannelHandler(state);
    }

    protected class RoleChangeCallback implements ControlChangeCallback {
        @Override
        public void controlChanged(long dpid, boolean hasControl) {
            log.info("Role change callback for switch {}, hasControl {}",
                    HexString.toHexString(dpid), hasControl);

            synchronized (roleChanger) {
                OFSwitchImpl sw = null;
                for (OFSwitchImpl connectedSw : connectedSwitches) {
                    if (connectedSw.getId() == dpid) {
                        sw = connectedSw;
                        break;
                    }
                }
                if (sw == null) {
                    log.warn("Switch {} not found in connected switches",
                            HexString.toHexString(dpid));
                    return;
                }

                Role role = null;
                                
                                /*
                                 * issue #229
                                 * Cannot rely on sw.getRole() as it can be behind due to pending
                                 * role changes in the queue. Just submit it and late the RoleChanger
                                 * handle duplicates.
                                 */

                if (hasControl) {
                    role = Role.MASTER;
                } else {
                    role = Role.SLAVE;
                }

                log.debug("Sending role request {} msg to {}", role, sw);
                Collection<OFSwitchImpl> swList = new ArrayList<OFSwitchImpl>(1);
                swList.add(sw);
                roleChanger.submitRequest(swList, role);

            }

        }
    }

    /**
     * Channel handler deals with the switch connection and dispatches
     * switch messages to the appropriate locations.
     *
     * @author readams
     */
    protected class OFChannelHandler
            extends IdleStateAwareChannelUpstreamHandler {
        protected OFSwitchImpl sw;
        protected OFChannelState state;

        public OFChannelHandler(OFChannelState state) {
            this.state = state;
        }

        @Override
        @LogMessageDoc(message = "New switch connection from {ip address}",
                explanation = "A new switch has connected from the " +
                        "specified IP address")
        public void channelConnected(ChannelHandlerContext ctx,
                                     ChannelStateEvent e) throws Exception {
            log.info("New switch connection from {}",
                    e.getChannel().getRemoteAddress());

            sw = new OFSwitchImpl();
            sw.setChannel(e.getChannel());
            sw.setFloodlightProvider(Controller.this);
            sw.setThreadPoolService(threadPool);

            List<OFMessage> msglist = new ArrayList<OFMessage>(1);
            msglist.add(factory.getMessage(OFType.HELLO));
            e.getChannel().write(msglist);

        }

        @Override
        @LogMessageDoc(message = "Disconnected switch {switch information}",
                explanation = "The specified switch has disconnected.")
        public void channelDisconnected(ChannelHandlerContext ctx,
                                        ChannelStateEvent e) throws Exception {
            if (sw != null && state.hsState == HandshakeState.READY) {
                if (activeSwitches.containsKey(sw.getId())) {
                    // It's safe to call removeSwitch even though the map might
                    // not contain this particular switch but another with the 
                    // same DPID
                    removeSwitch(sw);
                }
                synchronized (roleChanger) {
                    if (controlRequested) {
                        registryService.releaseControl(sw.getId());
                    }
                    connectedSwitches.remove(sw);
                }
                sw.setConnected(false);
            }
            log.info("Disconnected switch {}", sw);
        }

        @Override
        @LogMessageDocs({
                @LogMessageDoc(level = "ERROR",
                        message = "Disconnecting switch {switch} due to read timeout",
                        explanation = "The connected switch has failed to send any " +
                                "messages or respond to echo requests",
                        recommendation = LogMessageDoc.CHECK_SWITCH),
                @LogMessageDoc(level = "ERROR",
                        message = "Disconnecting switch {switch}: failed to " +
                                "complete handshake",
                        explanation = "The switch did not respond correctly " +
                                "to handshake messages",
                        recommendation = LogMessageDoc.CHECK_SWITCH),
                @LogMessageDoc(level = "ERROR",
                        message = "Disconnecting switch {switch} due to IO Error: {}",
                        explanation = "There was an error communicating with the switch",
                        recommendation = LogMessageDoc.CHECK_SWITCH),
                @LogMessageDoc(level = "ERROR",
                        message = "Disconnecting switch {switch} due to switch " +
                                "state error: {error}",
                        explanation = "The switch sent an unexpected message",
                        recommendation = LogMessageDoc.CHECK_SWITCH),
                @LogMessageDoc(level = "ERROR",
                        message = "Disconnecting switch {switch} due to " +
                                "message parse failure",
                        explanation = "Could not parse a message from the switch",
                        recommendation = LogMessageDoc.CHECK_SWITCH),
                @LogMessageDoc(level = "ERROR",
                        message = "Could not process message: queue full",
                        explanation = "OpenFlow messages are arriving faster than " +
                                " the controller can process them.",
                        recommendation = LogMessageDoc.CHECK_CONTROLLER),
                @LogMessageDoc(level = "ERROR",
                        message = "Error while processing message " +
                                "from switch {switch} {cause}",
                        explanation = "An error occurred processing the switch message",
                        recommendation = LogMessageDoc.GENERIC_ACTION)
        })
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            if (e.getCause() instanceof ReadTimeoutException) {
                // switch timeout
                log.error("Disconnecting switch {} due to read timeout", sw);
                ctx.getChannel().close();
            } else if (e.getCause() instanceof HandshakeTimeoutException) {
                log.error("Disconnecting switch {}: failed to complete handshake",
                        sw);
                ctx.getChannel().close();
            } else if (e.getCause() instanceof ClosedChannelException) {
                //log.warn("Channel for sw {} already closed", sw);
            } else if (e.getCause() instanceof IOException) {
                log.error("Disconnecting switch {} due to IO Error: {}",
                        sw, e.getCause().getMessage());
                ctx.getChannel().close();
            } else if (e.getCause() instanceof SwitchStateException) {
                log.error("Disconnecting switch {} due to switch state error: {}",
                        sw, e.getCause().getMessage());
                ctx.getChannel().close();
            } else if (e.getCause() instanceof MessageParseException) {
                log.error("Disconnecting switch " + sw +
                        " due to message parse failure",
                        e.getCause());
                ctx.getChannel().close();
            } else if (e.getCause() instanceof RejectedExecutionException) {
                log.warn("Could not process message: queue full");
            } else {
                log.error("Error while processing message from switch " + sw,
                        e.getCause());
                ctx.getChannel().close();
            }
        }

        @Override
        public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
                throws Exception {
            List<OFMessage> msglist = new ArrayList<OFMessage>(1);
            msglist.add(factory.getMessage(OFType.ECHO_REQUEST));
            e.getChannel().write(msglist);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            if (e.getMessage() instanceof List) {
                @SuppressWarnings("unchecked")
                List<OFMessage> msglist = (List<OFMessage>) e.getMessage();

                for (OFMessage ofm : msglist) {
                    try {
                        processOFMessage(ofm);
                    } catch (Exception ex) {
                        // We are the last handler in the stream, so run the 
                        // exception through the channel again by passing in 
                        // ctx.getChannel().
                        Channels.fireExceptionCaught(ctx.getChannel(), ex);
                    }
                }

                // Flush all flow-mods/packet-out generated from this "train"
                OFSwitchImpl.flush_all();
            }
        }

        /**
         * Process the request for the switch description
         */
        @LogMessageDoc(level = "ERROR",
                message = "Exception in reading description " +
                        " during handshake {exception}",
                explanation = "Could not process the switch description string",
                recommendation = LogMessageDoc.CHECK_SWITCH)
        @SuppressWarnings("unchecked")
        void processSwitchDescReply() {
            try {
                // Read description, if it has been updated
                Future<?> future =
                        (Future<?>) sw.
                                getAttribute(IOFSwitch.SWITCH_DESCRIPTION_FUTURE);
                Future<List<OFStatistics>> desc_future =
                        (Future<List<OFStatistics>>) future;
                List<OFStatistics> values =
                        desc_future.get(0, TimeUnit.MILLISECONDS);
                if (values != null) {
                    OFDescriptionStatistics description =
                            new OFDescriptionStatistics();
                    ChannelBuffer data =
                            ChannelBuffers.buffer(description.getLength());
                    for (OFStatistics f : values) {
                        f.writeTo(data);
                        description.readFrom(data);
                        break; // SHOULD be a list of length 1
                    }
                    sw.setAttribute(IOFSwitch.SWITCH_DESCRIPTION_DATA,
                            description);
                    sw.setSwitchProperties(description);
                    data = null;
                }

                sw.removeAttribute(IOFSwitch.SWITCH_DESCRIPTION_FUTURE);
                state.hasDescription = true;
                checkSwitchReady();
            } catch (InterruptedException ex) {
                // Ignore
            } catch (TimeoutException ex) {
                // Ignore
            } catch (Exception ex) {
                log.error("Exception in reading description " +
                        " during handshake", ex);
            }
        }

        /**
         * Send initial switch setup information that we need before adding
         * the switch
         *
         * @throws IOException
         */
        void sendHelloConfiguration() throws IOException {
            // Send initial Features Request
            log.debug("Sending FEATURES_REQUEST to {}", sw);
            sw.write(factory.getMessage(OFType.FEATURES_REQUEST), null);
        }

        /**
         * Send the configuration requests we can only do after we have
         * the features reply
         *
         * @throws IOException
         */
        void sendFeatureReplyConfiguration() throws IOException {
            log.debug("Sending CONFIG_REQUEST to {}", sw);
            // Ensure we receive the full packet via PacketIn
            OFSetConfig config = (OFSetConfig) factory
                    .getMessage(OFType.SET_CONFIG);
            config.setMissSendLength((short) 0xffff)
                    .setLengthU(OFSwitchConfig.MINIMUM_LENGTH);
            sw.write(config, null);
            sw.write(factory.getMessage(OFType.GET_CONFIG_REQUEST),
                    null);

            // Get Description to set switch-specific flags
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(OFStatisticsType.DESC);
            req.setLengthU(req.getLengthU());
            Future<List<OFStatistics>> dfuture =
                    sw.getStatistics(req);
            sw.setAttribute(IOFSwitch.SWITCH_DESCRIPTION_FUTURE,
                    dfuture);

        }

        volatile Boolean controlRequested = Boolean.FALSE;

        protected void checkSwitchReady() {
            if (state.hsState == HandshakeState.FEATURES_REPLY &&
                    state.hasDescription && state.hasGetConfigReply) {

                state.hsState = HandshakeState.READY;
                log.debug("Handshake with {} complete", sw);

                synchronized (roleChanger) {
                    // We need to keep track of all of the switches that are connected
                    // to the controller, in any role, so that we can later send the
                    // role request messages when the controller role changes.
                    // We need to be synchronized while doing this: we must not
                    // send a another role request to the connectedSwitches until
                    // we were able to add this new switch to connectedSwitches
                    // *and* send the current role to the new switch.
                    connectedSwitches.add(sw);

                    if (role != null) {
                        //Put the switch in SLAVE mode until we know we have control
                        log.debug("Setting new switch {} to SLAVE", sw.getStringId());
                        Collection<OFSwitchImpl> swList = new ArrayList<OFSwitchImpl>(1);
                        swList.add(sw);
                        roleChanger.submitRequest(swList, Role.SLAVE);

                        //Request control of the switch from the global registry
                        try {
                            controlRequested = Boolean.TRUE;
                            registryService.requestControl(sw.getId(),
                                    new RoleChangeCallback());
                        } catch (RegistryException e) {
                            log.debug("Registry error: {}", e.getMessage());
                            controlRequested = Boolean.FALSE;
                        }


                        // Send a role request if role support is enabled for the controller
                        // This is a probe that we'll use to determine if the switch
                        // actually supports the role request message. If it does we'll
                        // get back a role reply message. If it doesn't we'll get back an
                        // OFError message.
                        // If role is MASTER we will promote switch to active
                        // list when we receive the switch's role reply messages
                        /*
                        log.debug("This controller's role is {}, " +
                                "sending initial role request msg to {}",
                                role, sw);
                        Collection<OFSwitchImpl> swList = new ArrayList<OFSwitchImpl>(1);
                        swList.add(sw);
                        roleChanger.submitRequest(swList, role);
                        */
                    } else {
                        // Role supported not enabled on controller (for now)
                        // automatically promote switch to active state.
                        log.debug("This controller's role is {}, " +
                                "not sending role request msg to {}",
                                role, sw);
                        // Need to clear FlowMods before we add the switch
                        // and dispatch updates otherwise we have a race condition.
                        sw.clearAllFlowMods();
                        addSwitch(sw);
                        state.firstRoleReplyReceived = true;
                    }
                }
                if (!controlRequested) {
                    // yield to allow other thread(s) to release control
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // Ignore interruptions
                    }
                    // safer to bounce the switch to reconnect here than proceeding further
                    log.debug("Closing {} because we weren't able to request control " +
                            "successfully" + sw);
                    sw.channel.close();
                }
            }
        }

        /* Handle a role reply message we received from the switch. Since
         * netty serializes message dispatch we don't need to synchronize
         * against other receive operations from the same switch, so no need
         * to synchronize addSwitch(), removeSwitch() operations from the same
         * connection.
         * FIXME: However, when a switch with the same DPID connects we do
         * need some synchronization. However, handling switches with same
         * DPID needs to be revisited anyways (get rid of r/w-lock and synchronous
         * removedSwitch notification):1
         *
         */
        @LogMessageDoc(level = "ERROR",
                message = "Invalid role value in role reply message",
                explanation = "Was unable to set the HA role (master or slave) " +
                        "for the controller.",
                recommendation = LogMessageDoc.CHECK_CONTROLLER)
        protected void handleRoleReplyMessage(OFVendor vendorMessage,
                                              OFRoleReplyVendorData roleReplyVendorData) {
            // Map from the role code in the message to our role enum
            int nxRole = roleReplyVendorData.getRole();
            Role role = null;
            switch (nxRole) {
                case OFRoleVendorData.NX_ROLE_OTHER:
                    role = Role.EQUAL;
                    break;
                case OFRoleVendorData.NX_ROLE_MASTER:
                    role = Role.MASTER;
                    break;
                case OFRoleVendorData.NX_ROLE_SLAVE:
                    role = Role.SLAVE;
                    break;
                default:
                    log.error("Invalid role value in role reply message");
                    sw.getChannel().close();
                    return;
            }

            log.debug("Handling role reply for role {} from {}. " +
                    "Controller's role is {} ",
                    new Object[]{role, sw, Controller.this.role}
            );

            sw.deliverRoleReply(vendorMessage.getXid(), role);

            boolean isActive = activeSwitches.containsKey(sw.getId());
            if (!isActive && sw.isActive()) {
                // Transition from SLAVE to MASTER.

                if (!state.firstRoleReplyReceived ||
                        getAlwaysClearFlowsOnSwAdd()) {
                    // This is the first role-reply message we receive from
                    // this switch or roles were disabled when the switch
                    // connected:
                    // Delete all pre-existing flows for new connections to
                    // the master
                    //
                    // FIXME: Need to think more about what the test should
                    // be for when we flush the flow-table? For example,
                    // if all the controllers are temporarily in the backup
                    // role (e.g. right after a failure of the master
                    // controller) at the point the switch connects, then
                    // all of the controllers will initially connect as
                    // backup controllers and not flush the flow-table.
                    // Then when one of them is promoted to master following
                    // the master controller election the flow-table
                    // will still not be flushed because that's treated as
                    // a failover event where we don't want to flush the
                    // flow-table. The end result would be that the flow
                    // table for a newly connected switch is never
                    // flushed. Not sure how to handle that case though...
                    sw.clearAllFlowMods();
                    log.debug("First role reply from master switch {}, " +
                            "clear FlowTable to active switch list",
                            HexString.toHexString(sw.getId()));
                }

                // Some switches don't seem to update us with port
                // status messages while in slave role.
                //readSwitchPortStateFromStorage(sw);

                // Only add the switch to the active switch list if
                // we're not in the slave role. Note that if the role
                // attribute is null, then that means that the switch
                // doesn't support the role request messages, so in that
                // case we're effectively in the EQUAL role and the
                // switch should be included in the active switch list.
                addSwitch(sw);
                log.debug("Added master switch {} to active switch list",
                        HexString.toHexString(sw.getId()));

            } else if (isActive && !sw.isActive()) {
                // Transition from MASTER to SLAVE: remove switch
                // from active switch list.
                log.debug("Removed slave switch {} from active switch" +
                        " list", HexString.toHexString(sw.getId()));
                removeSwitch(sw);
            }

            // Indicate that we have received a role reply message.
            state.firstRoleReplyReceived = true;
        }

        protected boolean handleVendorMessage(OFVendor vendorMessage) {
            boolean shouldHandleMessage = false;
            int vendor = vendorMessage.getVendor();
            switch (vendor) {
                case OFNiciraVendorData.NX_VENDOR_ID:
                    OFNiciraVendorData niciraVendorData =
                            (OFNiciraVendorData) vendorMessage.getVendorData();
                    int dataType = niciraVendorData.getDataType();
                    switch (dataType) {
                        case OFRoleReplyVendorData.NXT_ROLE_REPLY:
                            OFRoleReplyVendorData roleReplyVendorData =
                                    (OFRoleReplyVendorData) niciraVendorData;
                            handleRoleReplyMessage(vendorMessage,
                                    roleReplyVendorData);
                            break;
                        default:
                            log.warn("Unhandled Nicira VENDOR message; " +
                                    "data type = {}", dataType);
                            break;
                    }
                    break;
                default:
                    log.warn("Unhandled VENDOR message; vendor id = {}", vendor);
                    break;
            }

            return shouldHandleMessage;
        }

        /**
         * Dispatch an Openflow message from a switch to the appropriate
         * handler.
         *
         * @param m The message to process
         * @throws IOException
         * @throws SwitchStateException
         */
        @LogMessageDocs({
                @LogMessageDoc(level = "WARN",
                        message = "Config Reply from {switch} has " +
                                "miss length set to {length}",
                        explanation = "The controller requires that the switch " +
                                "use a miss length of 0xffff for correct " +
                                "function",
                        recommendation = "Use a different switch to ensure " +
                                "correct function"),
                @LogMessageDoc(level = "WARN",
                        message = "Received ERROR from sw {switch} that "
                                + "indicates roles are not supported "
                                + "but we have received a valid "
                                + "role reply earlier",
                        explanation = "The switch sent a confusing message to the" +
                                "controller")
        })
        protected void processOFMessage(OFMessage m)
                throws IOException, SwitchStateException {
            boolean shouldHandleMessage = false;

            switch (m.getType()) {
                case HELLO:
                    if (log.isTraceEnabled())
                        log.trace("HELLO from {}", sw);

                    if (state.hsState.equals(HandshakeState.START)) {
                        state.hsState = HandshakeState.HELLO;
                        sendHelloConfiguration();
                    } else {
                        throw new SwitchStateException("Unexpected HELLO from "
                                + sw);
                    }
                    break;
                case ECHO_REQUEST:
                    OFEchoReply reply =
                            (OFEchoReply) factory.getMessage(OFType.ECHO_REPLY);
                    reply.setXid(m.getXid());
                    sw.write(reply, null);
                    break;
                case ECHO_REPLY:
                    break;
                case FEATURES_REPLY:
                    if (log.isTraceEnabled())
                        log.trace("Features Reply from {}", sw);

                    sw.setFeaturesReply((OFFeaturesReply) m);
                    if (state.hsState.equals(HandshakeState.HELLO)) {
                        sendFeatureReplyConfiguration();
                        state.hsState = HandshakeState.FEATURES_REPLY;
                        // uncomment to enable "dumb" switches like cbench
                        // state.hsState = HandshakeState.READY;
                        // addSwitch(sw);
                    } else {
                        // return results to rest api caller
                        sw.deliverOFFeaturesReply(m);
                        // update database */
                        //updateActiveSwitchInfo(sw);
                    }
                    break;
                case GET_CONFIG_REPLY:
                    if (log.isTraceEnabled())
                        log.trace("Get config reply from {}", sw);

                    if (!state.hsState.equals(HandshakeState.FEATURES_REPLY)) {
                        String em = "Unexpected GET_CONFIG_REPLY from " + sw;
                        throw new SwitchStateException(em);
                    }
                    OFGetConfigReply cr = (OFGetConfigReply) m;
                    if (cr.getMissSendLength() == (short) 0xffff) {
                        log.trace("Config Reply from {} confirms " +
                                "miss length set to 0xffff", sw);
                    } else {
                        log.warn("Config Reply from {} has " +
                                "miss length set to {}",
                                sw, cr.getMissSendLength() & 0xffff);
                    }
                    state.hasGetConfigReply = true;
                    checkSwitchReady();
                    break;
                case VENDOR:
                    shouldHandleMessage = handleVendorMessage((OFVendor) m);
                    break;
                case ERROR:
                    log.debug("Recieved ERROR message from switch {}: {}", sw, m);
                    // TODO: we need better error handling. Especially for
                    // request/reply style message (stats, roles) we should have
                    // a unified way to lookup the xid in the error message.
                    // This will probable involve rewriting the way we handle
                    // request/reply style messages.
                    OFError error = (OFError) m;
                    boolean shouldLogError = true;
                    // TODO: should we check that firstRoleReplyReceived is false,
                    // i.e., check only whether the first request fails?
                    if (sw.checkFirstPendingRoleRequestXid(error.getXid())) {
                        boolean isBadVendorError =
                                (error.getErrorType() == OFError.OFErrorType.
                                        OFPET_BAD_REQUEST.getValue());
                        // We expect to receive a bad vendor error when
                        // we're connected to a switch that doesn't support
                        // the Nicira vendor extensions (i.e. not OVS or
                        // derived from OVS).  By protocol, it should also be
                        // BAD_VENDOR, but too many switch implementations
                        // get it wrong and we can already check the xid()
                        // so we can ignore the type with confidence that this
                        // is not a spurious error
                        shouldLogError = !isBadVendorError;
                        if (isBadVendorError) {
                            log.debug("Handling bad vendor error for {}", sw);
                            if (state.firstRoleReplyReceived && (role != null)) {
                                log.warn("Received ERROR from sw {} that "
                                        + "indicates roles are not supported "
                                        + "but we have received a valid "
                                        + "role reply earlier", sw);
                            }
                            state.firstRoleReplyReceived = true;
                            Role requestedRole =
                                    sw.deliverRoleRequestNotSupportedEx(error.getXid());
                            synchronized (roleChanger) {
                                if (sw.role == null && Controller.this.role == Role.SLAVE) {
                                    //This will now never happen. The Controller's role
                                    //is now never SLAVE, always MASTER.
                                    // the switch doesn't understand role request
                                    // messages and the current controller role is
                                    // slave. We need to disconnect the switch.
                                    // @see RoleChanger for rationale
                                    log.warn("Closing {} channel because controller's role " +
                                            "is SLAVE", sw);
                                    sw.getChannel().close();
                                } else if (sw.role == null && requestedRole == Role.MASTER) {
                                    log.debug("Adding switch {} because we got an error" +
                                            " returned from a MASTER role request", sw);
                                    // Controller's role is master: add to
                                    // active
                                    // TODO: check if clearing flow table is
                                    // right choice here.
                                    // Need to clear FlowMods before we add the switch
                                    // and dispatch updates otherwise we have a race condition.
                                    // TODO: switch update is async. Won't we still have a potential
                                    //       race condition?
                                    sw.clearAllFlowMods();
                                    addSwitch(sw);
                                }
                            }
                        } else {
                            // TODO: Is this the right thing to do if we receive
                            // some other error besides a bad vendor error?
                            // Presumably that means the switch did actually
                            // understand the role request message, but there
                            // was some other error from processing the message.
                            // OF 1.2 specifies a OFPET_ROLE_REQUEST_FAILED
                            // error code, but it doesn't look like the Nicira
                            // role request has that. Should check OVS source
                            // code to see if it's possible for any other errors
                            // to be returned.
                            // If we received an error the switch is not
                            // in the correct role, so we need to disconnect it.
                            // We could also resend the request but then we need to
                            // check if there are other pending request in which
                            // case we shouldn't resend. If we do resend we need
                            // to make sure that the switch eventually accepts one
                            // of our requests or disconnect the switch. This feels
                            // cumbersome.
                            log.debug("Closing {} channel because we recieved an " +
                                    "error other than BAD_VENDOR", sw);
                            sw.getChannel().close();
                        }
                    }
                    // Once we support OF 1.2, we'd add code to handle it here.
                    //if (error.getXid() == state.ofRoleRequestXid) {
                    //}
                    if (shouldLogError)
                        logError(sw, error);
                    break;
                case STATS_REPLY:
                    if (state.hsState.ordinal() <
                            HandshakeState.FEATURES_REPLY.ordinal()) {
                        String em = "Unexpected STATS_REPLY from " + sw;
                        throw new SwitchStateException(em);
                    }
                    sw.deliverStatisticsReply(m);
                    if (sw.hasAttribute(IOFSwitch.SWITCH_DESCRIPTION_FUTURE)) {
                        processSwitchDescReply();
                    }
                    break;
                case PORT_STATUS:
                    // We want to update our port state info even if we're in
                    // the slave role, but we only want to update storage if
                    // we're the master (or equal).
                    boolean updateStorage = state.hsState.
                            equals(HandshakeState.READY) &&
                            (sw.getRole() != Role.SLAVE);
                    handlePortStatusMessage(sw, (OFPortStatus) m, updateStorage);
                    shouldHandleMessage = true;
                    break;

                default:
                    shouldHandleMessage = true;
                    break;
            }

            if (shouldHandleMessage) {
                sw.getListenerReadLock().lock();
                try {
                    if (sw.isConnected()) {
                        if (!state.hsState.equals(HandshakeState.READY)) {
                            log.debug("Ignoring message type {} received " +
                                    "from switch {} before switch is " +
                                    "fully configured.", m.getType(), sw);
                        }
                        // Check if the controller is in the slave role for the
                        // switch. If it is, then don't dispatch the message to
                        // the listeners.
                        // TODO: Should we dispatch messages that we expect to
                        // receive when we're in the slave role, e.g. port
                        // status messages? Since we're "hiding" switches from
                        // the listeners when we're in the slave role, then it
                        // seems a little weird to dispatch port status messages
                        // to them. On the other hand there might be special
                        // modules that care about all of the connected switches
                        // and would like to receive port status notifications.
                        else if (sw.getRole() == Role.SLAVE) {
                            // Don't log message if it's a port status message
                            // since we expect to receive those from the switch
                            // and don't want to emit spurious messages.
                            if (m.getType() != OFType.PORT_STATUS) {
                                log.debug("Ignoring message type {} received " +
                                        "from switch {} while in the slave role.",
                                        m.getType(), sw);
                            }
                        } else {
                            handleMessage(sw, m, null);
                        }
                    }
                } finally {
                    sw.getListenerReadLock().unlock();
                }
            }
        }
    }

    // ****************
    // Message handlers
    // ****************

    protected void handlePortStatusMessage(IOFSwitch sw,
                                           OFPortStatus m,
                                           boolean updateStorage) {
        short portNumber = m.getDesc().getPortNumber();
        OFPhysicalPort port = m.getDesc();
        if (m.getReason() == (byte) OFPortReason.OFPPR_MODIFY.ordinal()) {
            boolean portDown = ((OFPortConfig.OFPPC_PORT_DOWN.getValue() & port.getConfig()) > 0) ||
                    ((OFPortState.OFPPS_LINK_DOWN.getValue() & port.getState()) > 0);
            sw.setPort(port);
            if (!portDown) {
                SwitchUpdate update = new SwitchUpdate(sw, port, SwitchUpdateType.PORTADDED);
                try {
                    this.updates.put(update);
                } catch (InterruptedException e) {
                    log.error("Failure adding update to queue", e);
                }
            } else {
                SwitchUpdate update = new SwitchUpdate(sw, port, SwitchUpdateType.PORTREMOVED);
                try {
                    this.updates.put(update);
                } catch (InterruptedException e) {
                    log.error("Failure adding update to queue", e);
                }
            }
            //if (updateStorage)
            //updatePortInfo(sw, port);
            log.debug("Port #{} modified for {}", portNumber, sw);
        } else if (m.getReason() == (byte) OFPortReason.OFPPR_ADD.ordinal()) {
            // XXX Workaround to prevent race condition where a link is detected
            // and attempted to be written to the database before the port is in
            // the database. We now suppress link discovery on ports until we're
            // sure they're in the database.
            linkDiscovery.disableDiscoveryOnPort(sw.getId(), port.getPortNumber());

            sw.setPort(port);
            SwitchUpdate update = new SwitchUpdate(sw, port, SwitchUpdateType.PORTADDED);
            try {
                this.updates.put(update);
            } catch (InterruptedException e) {
                log.error("Failure adding update to queue", e);
            }
            //if (updateStorage)
            //updatePortInfo(sw, port);
            log.debug("Port #{} added for {}", portNumber, sw);
        } else if (m.getReason() ==
                (byte) OFPortReason.OFPPR_DELETE.ordinal()) {
            sw.deletePort(portNumber);
            SwitchUpdate update = new SwitchUpdate(sw, port, SwitchUpdateType.PORTREMOVED);
            try {
                this.updates.put(update);
            } catch (InterruptedException e) {
                log.error("Failure adding update to queue", e);
            }
            //if (updateStorage)
            //removePortInfo(sw, portNumber);
            log.debug("Port #{} deleted for {}", portNumber, sw);
        }
        SwitchUpdate update = new SwitchUpdate(sw, SwitchUpdateType.PORTCHANGED);
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            log.error("Failure adding update to queue", e);
        }
    }

    /**
     * flcontext_cache - Keep a thread local stack of contexts
     */
    protected static final ThreadLocal<Stack<FloodlightContext>> flcontext_cache =
            new ThreadLocal<Stack<FloodlightContext>>() {
                @Override
                protected Stack<FloodlightContext> initialValue() {
                    return new Stack<FloodlightContext>();
                }
            };

    /**
     * flcontext_alloc - pop a context off the stack, if required create a new one
     *
     * @return FloodlightContext
     */
    protected static FloodlightContext flcontext_alloc() {
        FloodlightContext flcontext = null;

        if (flcontext_cache.get().empty()) {
            flcontext = new FloodlightContext();
        } else {
            flcontext = flcontext_cache.get().pop();
        }

        return flcontext;
    }

    /**
     * flcontext_free - Free the context to the current thread
     *
     * @param flcontext
     */
    protected void flcontext_free(FloodlightContext flcontext) {
        flcontext.getStorage().clear();
        flcontext_cache.get().push(flcontext);
    }

    /**
     * Handle replies to certain OFMessages, and pass others off to listeners
     *
     * @param sw       The switch for the message
     * @param m        The message
     * @param bContext The floodlight context. If null then floodlight context would
     *                 be allocated in this function
     * @throws IOException
     */
    @LogMessageDocs({
            @LogMessageDoc(level = "ERROR",
                    message = "Ignoring PacketIn (Xid = {xid}) because the data" +
                            " field is empty.",
                    explanation = "The switch sent an improperly-formatted PacketIn" +
                            " message",
                    recommendation = LogMessageDoc.CHECK_SWITCH),
            @LogMessageDoc(level = "WARN",
                    message = "Unhandled OF Message: {} from {}",
                    explanation = "The switch sent a message not handled by " +
                            "the controller")
    })
    @SuppressWarnings("fallthrough")
    protected void handleMessage(IOFSwitch sw, OFMessage m,
                                 FloodlightContext bContext)
            throws IOException {
        Ethernet eth = null;

        switch (m.getType()) {
            case PACKET_IN:
                OFPacketIn pi = (OFPacketIn) m;

                if (pi.getPacketData().length <= 0) {
                    log.error("Ignoring PacketIn (Xid = " + pi.getXid() +
                            ") because the data field is empty.");
                    return;
                }

                if (Controller.ALWAYS_DECODE_ETH) {
                    eth = new Ethernet();
                    eth.deserialize(pi.getPacketData(), 0,
                            pi.getPacketData().length);
                }
                // fall through to default case...

            default:

                List<IOFMessageListener> listeners = null;
                if (messageListeners.containsKey(m.getType())) {
                    listeners = messageListeners.get(m.getType()).
                            getOrderedListeners();
                }

                FloodlightContext bc = null;
                if (listeners != null) {
                    // Check if floodlight context is passed from the calling 
                    // function, if so use that floodlight context, otherwise 
                    // allocate one
                    if (bContext == null) {
                        bc = flcontext_alloc();
                    } else {
                        bc = bContext;
                    }
                    if (eth != null) {
                        IFloodlightProviderService.bcStore.put(bc,
                                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                                eth);
                    }

                    // Get the starting time (overall and per-component) of 
                    // the processing chain for this packet if performance
                    // monitoring is turned on

                    Command cmd;
                    for (IOFMessageListener listener : listeners) {
                        if (listener instanceof IOFSwitchFilter) {
                            if (!((IOFSwitchFilter) listener).isInterested(sw)) {
                                continue;
                            }
                        }


                        cmd = listener.receive(sw, m, bc);


                        if (Command.STOP.equals(cmd)) {
                            break;
                        }
                    }

                } else {
                    log.warn("Unhandled OF Message: {} from {}", m, sw);
                }

                if ((bContext == null) && (bc != null)) flcontext_free(bc);
        }
    }

    /**
     * Log an OpenFlow error message from a switch
     *
     * @param sw    The switch that sent the error
     * @param error The error message
     */
    @LogMessageDoc(level = "ERROR",
            message = "Error {error type} {error code} from {switch}",
            explanation = "The switch responded with an unexpected error" +
                    "to an OpenFlow message from the controller",
            recommendation = "This could indicate improper network operation. " +
                    "If the problem persists restarting the switch and " +
                    "controller may help."
    )
    protected void logError(IOFSwitch sw, OFError error) {
        int etint = 0xffff & error.getErrorType();
        if (etint < 0 || etint >= OFErrorType.values().length) {
            log.error("Unknown error code {} from sw {}", etint, sw);
        }
        OFErrorType et = OFErrorType.values()[etint];
        switch (et) {
            case OFPET_HELLO_FAILED:
                OFHelloFailedCode hfc =
                        OFHelloFailedCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[]{et, hfc, sw});
                break;
            case OFPET_BAD_REQUEST:
                OFBadRequestCode brc =
                        OFBadRequestCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[]{et, brc, sw});
                break;
            case OFPET_BAD_ACTION:
                OFBadActionCode bac =
                        OFBadActionCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[]{et, bac, sw});
                break;
            case OFPET_FLOW_MOD_FAILED:
                OFFlowModFailedCode fmfc =
                        OFFlowModFailedCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[]{et, fmfc, sw});
                break;
            case OFPET_PORT_MOD_FAILED:
                OFPortModFailedCode pmfc =
                        OFPortModFailedCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[]{et, pmfc, sw});
                break;
            case OFPET_QUEUE_OP_FAILED:
                OFQueueOpFailedCode qofc =
                        OFQueueOpFailedCode.values()[0xffff & error.getErrorCode()];
                log.error("Error {} {} from {}", new Object[]{et, qofc, sw});
                break;
            default:
                break;
        }
    }

    /**
     * Add a switch to the active switch list and call the switch listeners.
     * This happens either when a switch first connects (and the controller is
     * not in the slave role) or when the role of the controller changes from
     * slave to master.
     *
     * @param sw the switch that has been added
     */
    // TODO: need to rethink locking and the synchronous switch update.
    //       We can / should also handle duplicate DPIDs in connectedSwitches
    @LogMessageDoc(level = "ERROR",
            message = "New switch added {switch} for already-added switch {switch}",
            explanation = "A switch with the same DPID as another switch " +
                    "connected to the controller.  This can be caused by " +
                    "multiple switches configured with the same DPID, or " +
                    "by a switch reconnected very quickly after " +
                    "disconnecting.",
            recommendation = "If this happens repeatedly, it is likely there " +
                    "are switches with duplicate DPIDs on the network.  " +
                    "Reconfigure the appropriate switches.  If it happens " +
                    "very rarely, then it is likely this is a transient " +
                    "network problem that can be ignored."
    )
    protected void addSwitch(IOFSwitch sw) {
        // XXX Workaround to prevent race condition where a link is detected
        // and attempted to be written to the database before the port is in
        // the database. We now suppress link discovery on ports until we're
        // sure they're in the database.
        for (OFPhysicalPort port : sw.getPorts()) {
            linkDiscovery.disableDiscoveryOnPort(sw.getId(), port.getPortNumber());
        }

        // TODO: is it safe to modify the HashMap without holding 
        // the old switch's lock?
        OFSwitchImpl oldSw = (OFSwitchImpl) this.activeSwitches.put(sw.getId(), sw);
        if (sw == oldSw) {
            // Note == for object equality, not .equals for value
            log.info("New add switch for pre-existing switch {}", sw);
            return;
        }

        if (oldSw != null) {
            oldSw.getListenerWriteLock().lock();
            try {
                log.error("New switch added {} for already-added switch {}",
                        sw, oldSw);
                // Set the connected flag to false to suppress calling
                // the listeners for this switch in processOFMessage
                oldSw.setConnected(false);

                oldSw.cancelAllStatisticsReplies();

                //updateInactiveSwitchInfo(oldSw);

                // we need to clean out old switch state definitively 
                // before adding the new switch
                // FIXME: It seems not completely kosher to call the
                // switch listeners here. I thought one of the points of
                // having the asynchronous switch update mechanism was so
                // the addedSwitch and removedSwitch were always called
                // from a single thread to simplify concurrency issues
                // for the listener.
                if (switchListeners != null) {
                    for (IOFSwitchListener listener : switchListeners) {
                        listener.removedSwitch(oldSw);
                    }
                }
                // will eventually trigger a removeSwitch(), which will cause
                // a "Not removing Switch ... already removed debug message.
                // TODO: Figure out a way to handle this that avoids the
                // spurious debug message.
                log.debug("Closing {} because a new IOFSwitch got added " +
                        "for this dpid", oldSw);
                oldSw.getChannel().close();
            } finally {
                oldSw.getListenerWriteLock().unlock();
            }
        }

        //updateActiveSwitchInfo(sw);
        SwitchUpdate update = new SwitchUpdate(sw, SwitchUpdateType.ADDED);
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            log.error("Failure adding update to queue", e);
        }
    }

    /**
     * Remove a switch from the active switch list and call the switch listeners.
     * This happens either when the switch is disconnected or when the
     * controller's role for the switch changes from master to slave.
     *
     * @param sw the switch that has been removed
     */
    protected void removeSwitch(IOFSwitch sw) {
        // No need to acquire the listener lock, since
        // this method is only called after netty has processed all
        // pending messages
        log.debug("removeSwitch: {}", sw);
        if (!this.activeSwitches.remove(sw.getId(), sw) || !sw.isConnected()) {
            log.debug("Not removing switch {}; already removed", sw);
            return;
        }
        // We cancel all outstanding statistics replies if the switch transition
        // from active. In the future we might allow statistics requests 
        // from slave controllers. Then we need to move this cancelation
        // to switch disconnect
        sw.cancelAllStatisticsReplies();

        // FIXME: I think there's a race condition if we call updateInactiveSwitchInfo
        // here if role support is enabled. In that case if the switch is being
        // removed because we've been switched to being in the slave role, then I think
        // it's possible that the new master may have already been promoted to master
        // and written out the active switch state to storage. If we now execute
        // updateInactiveSwitchInfo we may wipe out all of the state that was
        // written out by the new master. Maybe need to revisit how we handle all
        // of the switch state that's written to storage.

        //updateInactiveSwitchInfo(sw);
        SwitchUpdate update = new SwitchUpdate(sw, SwitchUpdateType.REMOVED);
        try {
            this.updates.put(update);
        } catch (InterruptedException e) {
            log.error("Failure adding update to queue", e);
        }
    }

    // ***************
    // IFloodlightProvider
    // ***************

    @Override
    public synchronized void addOFMessageListener(OFType type,
                                                  IOFMessageListener listener) {
        ListenerDispatcher<OFType, IOFMessageListener> ldd =
                messageListeners.get(type);
        if (ldd == null) {
            ldd = new ListenerDispatcher<OFType, IOFMessageListener>();
            messageListeners.put(type, ldd);
        }
        ldd.addListener(type, listener);
    }

    @Override
    public synchronized void removeOFMessageListener(OFType type,
                                                     IOFMessageListener listener) {
        ListenerDispatcher<OFType, IOFMessageListener> ldd =
                messageListeners.get(type);
        if (ldd != null) {
            ldd.removeListener(listener);
        }
    }

    private void logListeners() {
        for (Map.Entry<OFType,
                ListenerDispatcher<OFType,
                        IOFMessageListener>> entry
                : messageListeners.entrySet()) {

            OFType type = entry.getKey();
            ListenerDispatcher<OFType, IOFMessageListener> ldd =
                    entry.getValue();

            StringBuffer sb = new StringBuffer();
            sb.append("OFListeners for ");
            sb.append(type);
            sb.append(": ");
            for (IOFMessageListener l : ldd.getOrderedListeners()) {
                sb.append(l.getName());
                sb.append(",");
            }
            log.debug(sb.toString());
        }
    }

    public void removeOFMessageListeners(OFType type) {
        messageListeners.remove(type);
    }

    @Override
    public Map<Long, IOFSwitch> getSwitches() {
        return Collections.unmodifiableMap(this.activeSwitches);
    }

    @Override
    public void addOFSwitchListener(IOFSwitchListener listener) {
        this.switchListeners.add(listener);
    }

    @Override
    public void removeOFSwitchListener(IOFSwitchListener listener) {
        this.switchListeners.remove(listener);
    }

    @Override
    public Map<OFType, List<IOFMessageListener>> getListeners() {
        Map<OFType, List<IOFMessageListener>> lers =
                new HashMap<OFType, List<IOFMessageListener>>();
        for (Entry<OFType, ListenerDispatcher<OFType, IOFMessageListener>> e :
                messageListeners.entrySet()) {
            lers.put(e.getKey(), e.getValue().getOrderedListeners());
        }
        return Collections.unmodifiableMap(lers);
    }

    @Override
    @LogMessageDocs({
            @LogMessageDoc(message = "Failed to inject OFMessage {message} onto " +
                    "a null switch",
                    explanation = "Failed to process a message because the switch " +
                            " is no longer connected."),
            @LogMessageDoc(level = "ERROR",
                    message = "Error reinjecting OFMessage on switch {switch}",
                    explanation = "An I/O error occured while attempting to " +
                            "process an OpenFlow message",
                    recommendation = LogMessageDoc.CHECK_SWITCH)
    })
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg,
                                   FloodlightContext bc) {
        if (sw == null) {
            log.info("Failed to inject OFMessage {} onto a null switch", msg);
            return false;
        }

        // FIXME: Do we need to be able to inject messages to switches
        // where we're the slave controller (i.e. they're connected but
        // not active)?
        // FIXME: Don't we need synchronization logic here so we're holding
        // the listener read lock when we call handleMessage? After some
        // discussions it sounds like the right thing to do here would be to
        // inject the message as a netty upstream channel event so it goes
        // through the normal netty event processing, including being
        // handled 
        if (!activeSwitches.containsKey(sw.getId())) return false;

        try {
            // Pass Floodlight context to the handleMessages()
            handleMessage(sw, msg, bc);
        } catch (IOException e) {
            log.error("Error reinjecting OFMessage on switch {}",
                    HexString.toHexString(sw.getId()));
            return false;
        }
        return true;
    }

    @Override
    @LogMessageDoc(message = "Calling System.exit",
            explanation = "The controller is terminating")
    public synchronized void terminate() {
        log.info("Calling System.exit");
        System.exit(1);
    }

    @Override
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg) {
        // call the overloaded version with floodlight context set to null    
        return injectOfMessage(sw, msg, null);
    }

    @Override
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m,
                                      FloodlightContext bc) {
        if (log.isTraceEnabled()) {
            String str = OFMessage.getDataAsString(sw, m, bc);
            log.trace("{}", str);
        }

        List<IOFMessageListener> listeners = null;
        if (messageListeners.containsKey(m.getType())) {
            listeners =
                    messageListeners.get(m.getType()).getOrderedListeners();
        }

        if (listeners != null) {
            for (IOFMessageListener listener : listeners) {
                if (listener instanceof IOFSwitchFilter) {
                    if (!((IOFSwitchFilter) listener).isInterested(sw)) {
                        continue;
                    }
                }
                if (Command.STOP.equals(listener.receive(sw, m, bc))) {
                    break;
                }
            }
        }
    }

    @Override
    public BasicFactory getOFMessageFactory() {
        return factory;
    }

    @Override
    public String getControllerId() {
        return controllerId;
    }

    // **************
    // Initialization
    // **************

    /**
     * Sets the initial role based on properties in the config params.
     * It looks for two different properties.
     * If the "role" property is specified then the value should be
     * either "EQUAL", "MASTER", or "SLAVE" and the role of the
     * controller is set to the specified value. If the "role" property
     * is not specified then it looks next for the "role.path" property.
     * In this case the value should be the path to a property file in
     * the file system that contains a property called "floodlight.role"
     * which can be one of the values listed above for the "role" property.
     * The idea behind the "role.path" mechanism is that you have some
     * separate heartbeat and master controller election algorithm that
     * determines the role of the controller. When a role transition happens,
     * it updates the current role in the file specified by the "role.path"
     * file. Then if floodlight restarts for some reason it can get the
     * correct current role of the controller from the file.
     *
     * @param configParams The config params for the FloodlightProvider service
     * @return A valid role if role information is specified in the
     * config params, otherwise null
     */
    @LogMessageDocs({
            @LogMessageDoc(message = "Controller role set to {role}",
                    explanation = "Setting the initial HA role to "),
            @LogMessageDoc(level = "ERROR",
                    message = "Invalid current role value: {role}",
                    explanation = "An invalid HA role value was read from the " +
                            "properties file",
                    recommendation = LogMessageDoc.CHECK_CONTROLLER)
    })
    protected Role getInitialRole(Map<String, String> configParams) {
        Role role = null;
        String roleString = configParams.get("role");
        FileInputStream fs = null;
        if (roleString == null) {
            String rolePath = configParams.get("rolepath");
            if (rolePath != null) {
                Properties properties = new Properties();
                try {
                    fs = new FileInputStream(rolePath);
                    properties.load(fs);
                    roleString = properties.getProperty("floodlight.role");
                } catch (IOException exc) {
                    // Don't treat it as an error if the file specified by the
                    // rolepath property doesn't exist. This lets us enable the
                    // HA mechanism by just creating/setting the floodlight.role
                    // property in that file without having to modify the
                    // floodlight properties.
                } finally {
                    if (fs != null) {
                        try {
                            fs.close();
                        } catch (IOException e) {
                            log.error("Exception while closing resource ", e);
                        }
                    }
                }
            }
        }

        if (roleString != null) {
            // Canonicalize the string to the form used for the enum constants
            roleString = roleString.trim().toUpperCase();
            try {
                role = Role.valueOf(roleString);
            } catch (IllegalArgumentException exc) {
                log.error("Invalid current role value: {}", roleString);
            }
        }

        log.info("Controller role set to {}", role);

        return role;
    }

    /**
     * Tell controller that we're ready to accept switches loop
     *
     * @throws IOException
     */
    @LogMessageDocs({
            @LogMessageDoc(message = "Listening for switch connections on {address}",
                    explanation = "The controller is ready and listening for new" +
                            " switch connections"),
            @LogMessageDoc(message = "Storage exception in controller " +
                    "updates loop; terminating process",
                    explanation = ERROR_DATABASE,
                    recommendation = LogMessageDoc.CHECK_CONTROLLER),
            @LogMessageDoc(level = "ERROR",
                    message = "Exception in controller updates loop",
                    explanation = "Failed to dispatch controller event",
                    recommendation = LogMessageDoc.GENERIC_ACTION)
    })
    public void run() {
        if (log.isDebugEnabled()) {
            logListeners();
        }

        try {
            final ServerBootstrap bootstrap = createServerBootStrap();

            bootstrap.setOption("reuseAddr", true);
            bootstrap.setOption("child.keepAlive", true);
            bootstrap.setOption("child.tcpNoDelay", true);
            bootstrap.setOption("child.sendBufferSize", Controller.SEND_BUFFER_SIZE);

            ChannelPipelineFactory pfact =
                    new OpenflowPipelineFactory(this, null);
            bootstrap.setPipelineFactory(pfact);
            InetSocketAddress sa = new InetSocketAddress(openFlowPort);
            final ChannelGroup cg = new DefaultChannelGroup();
            cg.add(bootstrap.bind(sa));

            log.info("Listening for switch connections on {}", sa);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // main loop
        while (true) {
            try {
                IUpdate update = updates.take();
                update.dispatch();
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                log.error("Exception in controller updates loop", e);
            }
        }
    }

    private ServerBootstrap createServerBootStrap() {
        if (workerThreads == 0) {
            return new ServerBootstrap(
                    new NioServerSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool()));
        } else {
            return new ServerBootstrap(
                    new NioServerSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool(), workerThreads));
        }
    }

    public void setConfigParams(Map<String, String> configParams) {
        String ofPort = configParams.get("openflowport");
        if (ofPort != null) {
            this.openFlowPort = Integer.parseInt(ofPort);
        }
        log.debug("OpenFlow port set to {}", this.openFlowPort);
        String threads = configParams.get("workerthreads");
        if (threads != null) {
            this.workerThreads = Integer.parseInt(threads);
        }
        log.debug("Number of worker threads set to {}", this.workerThreads);
        String controllerId = configParams.get("controllerid");
        if (controllerId != null) {
            this.controllerId = controllerId;
        } else {
            //Try to get the hostname of the machine and use that for controller ID
            try {
                String hostname = java.net.InetAddress.getLocalHost().getHostName();
                this.controllerId = hostname;
            } catch (UnknownHostException e) {
                // Can't get hostname, we'll just use the default
            }
        }

        log.debug("ControllerId set to {}", this.controllerId);
    }

    private void initVendorMessages() {
        // Configure openflowj to be able to parse the role request/reply
        // vendor messages.
        OFBasicVendorId niciraVendorId = new OFBasicVendorId(
                OFNiciraVendorData.NX_VENDOR_ID, 4);
        OFVendorId.registerVendorId(niciraVendorId);
        OFBasicVendorDataType roleRequestVendorData =
                new OFBasicVendorDataType(
                        OFRoleRequestVendorData.NXT_ROLE_REQUEST,
                        OFRoleRequestVendorData.getInstantiable());
        niciraVendorId.registerVendorDataType(roleRequestVendorData);
        OFBasicVendorDataType roleReplyVendorData =
                new OFBasicVendorDataType(
                        OFRoleReplyVendorData.NXT_ROLE_REPLY,
                        OFRoleReplyVendorData.getInstantiable());
        niciraVendorId.registerVendorDataType(roleReplyVendorData);
    }

    /**
     * Initialize internal data structures
     */
    public void init(Map<String, String> configParams) {
        // These data structures are initialized here because other
        // module's startUp() might be called before ours
        this.messageListeners =
                new ConcurrentHashMap<OFType,
                        ListenerDispatcher<OFType,
                                IOFMessageListener>>();
        this.switchListeners = new CopyOnWriteArraySet<IOFSwitchListener>();
        this.activeSwitches = new ConcurrentHashMap<Long, IOFSwitch>();
        this.connectedSwitches = new HashSet<OFSwitchImpl>();
        this.controllerNodeIPsCache = new HashMap<String, String>();
        this.updates = new LinkedBlockingQueue<IUpdate>();
        this.factory = new BasicFactory();
        setConfigParams(configParams);
        //Set the controller's role to MASTER so it always tries to do role requests.
        this.role = Role.MASTER;
        this.roleChanger = new RoleChanger();
        initVendorMessages();
        this.systemStartTime = System.currentTimeMillis();
    }

    /**
     * Startup all of the controller's components
     */
    @LogMessageDoc(message = "Waiting for storage source",
            explanation = "The system database is not yet ready",
            recommendation = "If this message persists, this indicates " +
                    "that the system database has failed to start. " +
                    LogMessageDoc.CHECK_CONTROLLER)
    public void startupComponents() {
        try {
            registryService.registerController(controllerId);
        } catch (RegistryException e) {
            log.warn("Registry service error: {}", e.getMessage());
        }

        // Add our REST API
        restApi.addRestletRoutable(new CoreWebRoutable());
    }

    @Override
    public Map<String, String> getControllerNodeIPs() {
        // We return a copy of the mapping so we can guarantee that
        // the mapping return is the same as one that will be (or was)
        // dispatched to IHAListeners
        HashMap<String, String> retval = new HashMap<String, String>();
        synchronized (controllerNodeIPsCache) {
            retval.putAll(controllerNodeIPsCache);
        }
        return retval;
    }

    @Override
    public long getSystemStartTime() {
        return (this.systemStartTime);
    }

    @Override
    public void setAlwaysClearFlowsOnSwAdd(boolean value) {
        this.alwaysClearFlowsOnSwAdd = value;
    }

    public boolean getAlwaysClearFlowsOnSwAdd() {
        return this.alwaysClearFlowsOnSwAdd;
    }
}
