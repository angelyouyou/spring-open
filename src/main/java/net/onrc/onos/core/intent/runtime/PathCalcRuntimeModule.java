package net.onrc.onos.core.intent.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.onrc.onos.core.datagrid.IDatagridService;
import net.onrc.onos.core.datagrid.IEventChannel;
import net.onrc.onos.core.datagrid.IEventChannelListener;
import net.onrc.onos.core.intent.Intent;
import net.onrc.onos.core.intent.Intent.IntentState;
import net.onrc.onos.core.intent.IntentMap;
import net.onrc.onos.core.intent.IntentOperation;
import net.onrc.onos.core.intent.IntentOperation.Operator;
import net.onrc.onos.core.intent.IntentOperationList;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Toshio Koide (t-koide@onlab.us)
 */
public class PathCalcRuntimeModule implements IFloodlightModule, IPathCalcRuntimeService, ITopologyListener, IEventChannelListener<Long, IntentStateList> {
    static class PerfLog {
        private String step;
        private long time;

        public PerfLog(String step) {
            this.step = step;
            this.time = System.nanoTime();
        }

        public void logThis() {
            log.debug("Time:{}, Step:{}", time, step);
        }
    }

    static class PerfLogger {
        private LinkedList<PerfLog> logData = new LinkedList<>();

        public PerfLogger(String logPhase) {
            log("start_" + logPhase);
        }

        public void log(String step) {
            logData.add(new PerfLog(step));
        }

        public void flushLog() {
            log("finish");
            for (PerfLog perfLog : logData) {
                perfLog.logThis();
            }
            logData.clear();
        }
    }

    private PathCalcRuntime runtime;
    private IDatagridService datagridService;
    private ITopologyService topologyService;
    private IntentMap highLevelIntents;
    private PathIntentMap pathIntents;
    private IControllerRegistryService controllerRegistry;
    private PersistIntent persistIntent;
    private IRestApiService restApi;

    private IEventChannel<Long, IntentOperationList> opEventChannel;
    private final ReentrantLock lock = new ReentrantLock(true);
    private HashSet<LinkEvent> unmatchedLinkEvents = new HashSet<>();
    private Map<String, Set<Long>> intentInstalledMap = new ConcurrentHashMap<String, Set<Long>>();
    private static final String INTENT_OP_EVENT_CHANNEL_NAME = "onos.pathintent";
    private static final String INTENT_STATE_EVENT_CHANNEL_NAME = "onos.pathintent_state";
    private static final Logger log = LoggerFactory.getLogger(PathCalcRuntimeModule.class);

    // ================================================================================
    // private methods
    // ================================================================================

    private void reroutePaths(Collection<Intent> oldPaths) {
        if (oldPaths == null || oldPaths.isEmpty()) {
            return;
        }

        IntentOperationList reroutingOperation = new IntentOperationList();
        for (Intent intent : oldPaths) {
            PathIntent pathIntent = (PathIntent) intent;
            if (pathIntent.isPathFrozen()) {
                continue;
            }
            if (pathIntent.getState().equals(IntentState.INST_ACK) && // XXX: path intents in flight
                    !reroutingOperation.contains(pathIntent.getParentIntent())) {
                reroutingOperation.add(Operator.ADD, pathIntent.getParentIntent());
            }
        }
        executeIntentOperations(reroutingOperation);
    }


    // ================================================================================
    // IFloodlightModule implementations
    // ================================================================================

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<>(1);
        l.add(IPathCalcRuntimeService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<>();
        m.put(IPathCalcRuntimeService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<>(2);
        l.add(IDatagridService.class);
        l.add(IRestApiService.class);
        l.add(ITopologyService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        datagridService = context.getServiceImpl(IDatagridService.class);
        topologyService = context.getServiceImpl(ITopologyService.class);
        controllerRegistry = context.getServiceImpl(IControllerRegistryService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        highLevelIntents = new IntentMap();
        runtime = new PathCalcRuntime(topologyService.getTopology());
        pathIntents = new PathIntentMap();
        opEventChannel = datagridService.createChannel(INTENT_OP_EVENT_CHANNEL_NAME, Long.class, IntentOperationList.class);
        datagridService.addListener(INTENT_STATE_EVENT_CHANNEL_NAME, this, Long.class, IntentStateList.class);
        topologyService.registerTopologyListener(this);
        persistIntent = new PersistIntent(controllerRegistry);
        restApi.addRestletRoutable(new IntentWebRoutable());
    }

    // ================================================================================
    // IPathCalcRuntimeService implementations
    // ================================================================================

    @Override
    public IntentOperationList executeIntentOperations(IntentOperationList list) {

        if (list == null || list.size() == 0) {
            return null;
        }

        lock.lock(); // TODO optimize locking using smaller steps
        try {
            log.trace("lock executeIntentOperations, lock obj is already locked? {}", lock.isLocked());
            // update the map of high-level intents

            highLevelIntents.executeOperations(list);

            // change states of high-level intents
            IntentStateList states = new IntentStateList();
            for (IntentOperation op : list) {
                switch (op.operator) {
                    case ADD:
                        switch (op.intent.getState()) {
                            case CREATED:
                                states.put(op.intent.getId(), IntentState.INST_REQ);
                                break;
                            case INST_ACK:
                                states.put(op.intent.getId(), IntentState.REROUTE_REQ);
                                break;
                            default:
                                break;
                        }
                        break;
                    case REMOVE:
                        switch (op.intent.getState()) {
                            case CREATED:
                                states.put(op.intent.getId(), IntentState.DEL_REQ);
                                break;
                            default:
                                break;
                        }
                        break;
                    default:
                        break;
                }
            }
            highLevelIntents.changeStates(states);

            // calculate path-intents (low-level operations)
            IntentOperationList pathIntentOperations = runtime.calcPathIntents(list, highLevelIntents, pathIntents);

            // persist calculated low-level operations into data store
            long key = persistIntent.getKey();
            persistIntent.persistIfLeader(key, pathIntentOperations);

            // remove error-intents and reflect them to high-level intents
            states.clear();
            Iterator<IntentOperation> i = pathIntentOperations.iterator();
            while (i.hasNext()) {
                IntentOperation op = i.next();
                if (op.operator.equals(Operator.ERROR)) {
                    states.put(op.intent.getId(), IntentState.INST_NACK);
                    i.remove();
                }
            }
            highLevelIntents.changeStates(states);

            // update the map of path intents and publish the path operations
            pathIntents.executeOperations(pathIntentOperations);

            // send notification
            // XXX: Send notifications using the same key every time
            // and receive them by entryAdded() and entryUpdated()
            opEventChannel.addEntry(0L, pathIntentOperations);
            //opEventChannel.removeEntry(key);
            return pathIntentOperations;
        } finally {
            lock.unlock();
            log.trace("unlock executeIntentOperations");
        }
    }

    @Override
    public IntentMap getHighLevelIntents() {
        return highLevelIntents;
    }

    @Override
    public IntentMap getPathIntents() {
        return pathIntents;
    }

    @Override
    public void purgeIntents() {
        highLevelIntents.purge();
        pathIntents.purge();
    }

    // ================================================================================
    // ITopologyListener implementations
    // ================================================================================

    // CHECKSTYLE:OFF suppress warning about too many parameters
    @Override
    public void topologyEvents(Collection<SwitchEvent> addedSwitchEvents,
                                   Collection<SwitchEvent> removedSwitchEvents,
                                   Collection<PortEvent> addedPortEvents,
                                   Collection<PortEvent> removedPortEvents,
                                   Collection<LinkEvent> addedLinkEvents,
                                   Collection<LinkEvent> removedLinkEvents,
                                   Collection<DeviceEvent> addedDeviceEvents,
                                   Collection<DeviceEvent> removedDeviceEvents) {
    // CHECKSTYLE:ON

        PerfLogger p = new PerfLogger("networkGraphEvents");
        HashSet<Intent> affectedPaths = new HashSet<>();

        boolean rerouteAll = false;
        for (LinkEvent le : addedLinkEvents) {
            LinkEvent rev = new LinkEvent(le.getDst().getDpid(), le.getDst().getNumber(), le.getSrc().getDpid(), le.getSrc().getNumber());
            if (unmatchedLinkEvents.contains(rev)) {
                rerouteAll = true;
                unmatchedLinkEvents.remove(rev);
                log.debug("Found matched LinkEvent: {} {}", rev, le);
            } else {
                unmatchedLinkEvents.add(le);
                log.debug("Adding unmatched LinkEvent: {}", le);
            }
        }
        for (LinkEvent le : removedLinkEvents) {
            if (unmatchedLinkEvents.contains(le)) {
                unmatchedLinkEvents.remove(le);
                log.debug("Removing LinkEvent: {}", le);
            }
        }
        if (unmatchedLinkEvents.size() > 0) {
            log.debug("Unmatched link events: {} events", unmatchedLinkEvents.size());
        }

        if (rerouteAll) { //addedLinkEvents.size() > 0) { // ||
//                              addedPortEvents.size() > 0 ||
//                              addedSwitchEvents.size() > 0) {
            p.log("begin_getAllIntents");
            affectedPaths.addAll(getPathIntents().getAllIntents());
            p.log("end_getAllIntents");
        } else if (removedSwitchEvents.size() > 0 ||
                removedLinkEvents.size() > 0 ||
                removedPortEvents.size() > 0) {
            p.log("begin_getIntentsByLink");
            for (LinkEvent linkEvent : removedLinkEvents) {
                affectedPaths.addAll(pathIntents.getIntentsByLink(linkEvent));
            }
            p.log("end_getIntentsByLink");

            p.log("begin_getIntentsByPort");
            for (PortEvent portEvent : removedPortEvents) {
                affectedPaths.addAll(pathIntents.getIntentsByPort(portEvent.getDpid(), portEvent.getNumber()));
            }
            p.log("end_getIntentsByPort");

            p.log("begin_getIntentsByDpid");
            for (SwitchEvent switchEvent : removedSwitchEvents) {
                affectedPaths.addAll(pathIntents.getIntentsByDpid(switchEvent.getDpid()));
            }
            p.log("end_getIntentsByDpid");
        }
        p.log("begin_reroutePaths");
        reroutePaths(affectedPaths);
        p.log("end_reroutePaths");
        p.flushLog();
    }

    // ================================================================================
    // IEventChannelListener implementations
    // ================================================================================

    @Override
    public void entryAdded(IntentStateList value) {
        entryUpdated(value);
    }

    @Override
    public void entryRemoved(IntentStateList value) {
        // do nothing
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void entryUpdated(IntentStateList value) {
        // TODO draw state transition diagram in multiple ONOS instances and update this method

        lock.lock(); // TODO optimize locking using smaller steps
        try {
            log.trace("lock entryUpdated, lock obj is already locked? {}", lock.isLocked());
            // reflect state changes of path-level intent into application-level intents
            IntentStateList highLevelIntentStates = new IntentStateList();
            IntentStateList pathIntentStates = new IntentStateList();
            for (Entry<String, IntentState> entry : value.entrySet()) {
                String pathIntentId = entry.getKey();
                IntentState nextPathIntentState = entry.getValue();
                PathIntent pathIntent = (PathIntent) pathIntents.getIntent(pathIntentId);
                if (pathIntent == null) {
                    continue;
                }

                Intent parentIntent = pathIntent.getParentIntent();
                if (parentIntent == null ||
                        !(parentIntent instanceof ShortestPathIntent)) {
                    continue;
                }

                boolean isChildIntent = ((ShortestPathIntent) parentIntent).getPathIntentId().equals(pathIntentId);
                switch (nextPathIntentState) {
                    case INST_ACK:
                        Set<Long> installedDpids = calcInstalledDpids(pathIntent, value.domainSwitchDpids);
                        if (!isFlowInstalled(pathIntent, installedDpids)) {
                            break;
                        }
                        // FALLTHROUGH
                    case INST_NACK:
                        // FALLTHROUGH
                    case DEL_PENDING:
                        if (isChildIntent) {
                            log.debug("put the state highLevelIntentStates ID {}, state {}", parentIntent.getId(), nextPathIntentState);
                            highLevelIntentStates.put(parentIntent.getId(), nextPathIntentState);
                        }
                        log.debug("put the state pathIntentStates ID {}, state {}", pathIntentId, nextPathIntentState);
                        pathIntentStates.put(pathIntentId, nextPathIntentState);
                        break;
                    case DEL_ACK:
                        if (isChildIntent) {
                            if (intentInstalledMap.containsKey(parentIntent.getId())) {
                                 intentInstalledMap.remove(parentIntent.getId());
                            }
                            log.debug("put the state highLevelIntentStates ID {}, state {}", parentIntent.getId(), nextPathIntentState);
                            highLevelIntentStates.put(parentIntent.getId(), nextPathIntentState);
                        }
                        log.debug("put the state pathIntentStates ID {}, state {}", pathIntentId, nextPathIntentState);
                        pathIntentStates.put(pathIntentId, nextPathIntentState);
                        break;
                    case CREATED:
                        break;
                    case DEL_REQ:
                        break;
                    case INST_REQ:
                        break;
                    case REROUTE_REQ:
                        break;
                    default:
                        break;
                }
            }
            highLevelIntents.changeStates(highLevelIntentStates);
            pathIntents.changeStates(pathIntentStates);
        } finally {
            lock.unlock();
            log.trace("unlock entryUpdated");
        }
    }

    /***
     * This function is to check whether the entire path's flow entries are installed or not.
     *
     * @param pathIntent : The pathIntent to be checked
     * @param installedDpids : The dpids installed on one ONOS instance
     * @return The result of whether a pathIntent has been installed or not.
     */
    private boolean isFlowInstalled(PathIntent pathIntent, Set<Long> installedDpids) {
        String parentIntentId = pathIntent.getParentIntent().getId();
        log.debug("parentIntentId {}", parentIntentId);

        if (intentInstalledMap.containsKey(parentIntentId)) {
            if (!installedDpids.isEmpty()) {
                intentInstalledMap.get(parentIntentId).addAll(installedDpids);
            }
        } else {
            // This is the creation of an entry.
            intentInstalledMap.put(parentIntentId, installedDpids);
        }

        Set<Long> allSwitchesForPath = new HashSet<Long>();
        ShortestPathIntent spfIntent = (ShortestPathIntent) pathIntent.getParentIntent();

        for (LinkEvent linkEvent : pathIntent.getPath()) {
            long sw = linkEvent.getSrc().getDpid();
            allSwitchesForPath.add(sw);
        }
        allSwitchesForPath.add(spfIntent.getDstSwitchDpid());

        if (log.isTraceEnabled()) {
            log.trace("All switches {}, installed installedDpids {}", allSwitchesForPath, intentInstalledMap.get(parentIntentId));
        }

        if (allSwitchesForPath.equals(intentInstalledMap.get(parentIntentId))) {
            intentInstalledMap.remove(parentIntentId);
            return true;
        }

        return false;
    }

    private Set<Long> calcInstalledDpids(PathIntent pathIntent, Set<Long> domainSwitchDpids) {
        Set<Long> allSwitchesForPath = new HashSet<Long>();
        ShortestPathIntent spfIntent = (ShortestPathIntent) pathIntent.getParentIntent();

        for (LinkEvent linkEvent : pathIntent.getPath()) {
            long sw = linkEvent.getSrc().getDpid();

            if (domainSwitchDpids.contains(sw)) {
                allSwitchesForPath.add(sw);
            }
        }

        if (domainSwitchDpids.contains(spfIntent.getDstSwitchDpid())) {
            allSwitchesForPath.add(spfIntent.getDstSwitchDpid());
        }

        if (log.isTraceEnabled()) {
            log.trace("All switches for a path {}, domain switch dpids {}", allSwitchesForPath, domainSwitchDpids);
        }

        return allSwitchesForPath;
    }
}
