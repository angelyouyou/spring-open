package net.onrc.onos.core.datagrid.web;

import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;

import net.onrc.onos.core.intent.Intent;
import net.onrc.onos.core.intent.Intent.IntentState;
import net.onrc.onos.core.intent.IntentMap;
import net.onrc.onos.core.intent.Path;
import net.onrc.onos.core.intent.PathIntent;
import net.onrc.onos.core.intent.ShortestPathIntent;
import net.onrc.onos.core.intent.runtime.IPathCalcRuntimeService;
import net.onrc.onos.core.topology.LinkEvent;
import net.onrc.onos.core.util.CallerId;
import net.onrc.onos.core.util.Dpid;
import net.onrc.onos.core.util.FlowEntry;
import net.onrc.onos.core.util.FlowId;
import net.onrc.onos.core.util.FlowPath;
import net.onrc.onos.core.util.FlowPathType;
import net.onrc.onos.core.util.FlowPathUserState;
import net.onrc.onos.core.util.PortNumber;
import net.onrc.onos.core.util.SwitchPort;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API call to get a summary of Flow Paths.
 * <p/>
 * NOTE: This REST API call is needed for the ONOS GUI.
 * <p/>
 * GET /wm/onos/datagrid/get/ng-flows/summary/json
 */
public class GetNGFlowsSummaryResource extends ServerResource {
    public static final Logger log = LoggerFactory.getLogger(GetNGFlowsSummaryResource.class);

    @Get("json")
    public ArrayList<FlowPath> retrieve() {
        ArrayList<FlowPath> result = new ArrayList<>();
        SortedMap<Long, FlowPath> sortedFlowPaths = new TreeMap<>();

        IPathCalcRuntimeService pathRuntime =
                (IPathCalcRuntimeService) getContext().
                        getAttributes().get(IPathCalcRuntimeService.class.getCanonicalName());
        log.debug("Get NG Flows Summary");


        IntentMap parentIntentMap = pathRuntime.getHighLevelIntents();
        IntentMap intentMap = pathRuntime.getPathIntents();
        for (Intent parentIntent : parentIntentMap.getAllIntents()) {
            // Get only installed Shortest Paths
            if (parentIntent.getState() != IntentState.INST_ACK) {
                continue;
            }
            if (!(parentIntent instanceof ShortestPathIntent)) {
                continue;
            }
            ShortestPathIntent spIntent = (ShortestPathIntent) parentIntent;

            // Get the Path Intent
            Intent intent = intentMap.getIntent(spIntent.getPathIntentId());
            if (!(intent instanceof PathIntent)) {
                continue;
            }
            PathIntent pathIntent = (PathIntent) intent;

            // Decode the Shortest Path ID
            String applnIntentId = parentIntent.getId();
            String intentId = applnIntentId.split(":")[1];

            // Create the Flow Path
            FlowId flowId = new FlowId(intentId);
            FlowPath flowPath = new FlowPath();
            flowPath.setFlowId(flowId);
            sortedFlowPaths.put(flowPath.flowId().value(), flowPath);

            flowPath.setInstallerId(new CallerId("E"));
            flowPath.setFlowEntryActions(null);
            flowPath.setFlowPathType(FlowPathType.FP_TYPE_SHORTEST_PATH);
            flowPath.setFlowPathUserState(FlowPathUserState.FP_USER_ADD);

            // Setup the Source and Destination DPID and Port
            Dpid srcDpid = new Dpid(spIntent.getSrcSwitchDpid());
            PortNumber srcPort = new PortNumber((short) spIntent.getSrcPortNumber());
            Dpid dstDpid = new Dpid(spIntent.getDstSwitchDpid());
            PortNumber dstPort = new PortNumber((short) spIntent.getDstPortNumber());
            SwitchPort srcSwitchPort = new SwitchPort(srcDpid, srcPort);
            SwitchPort dstSwitchPort = new SwitchPort(dstDpid, dstPort);
            flowPath.dataPath().setSrcPort(srcSwitchPort);
            flowPath.dataPath().setDstPort(dstSwitchPort);

            // Extract the Flow Entries
            Path path = pathIntent.getPath();
            FlowEntry flowEntry;
            ArrayList<FlowEntry> flowEntries = new ArrayList<>();
            for (LinkEvent linkEvent : path) {
                Dpid dpid = linkEvent.getSrc().getDpid();
                flowEntry = new FlowEntry();
                flowEntry.setDpid(dpid);
                flowEntries.add(flowEntry);
            }
            // Add the final Flow Entry
            flowEntry = new FlowEntry();
            flowEntry.setDpid(new Dpid(spIntent.getDstSwitchDpid()));
            flowEntries.add(flowEntry);
            flowPath.dataPath().setFlowEntries(flowEntries);
        }

        // Prepare the return result
        for (FlowPath flowPath : sortedFlowPaths.values()) {
            result.add(flowPath);
        }

        return result;
    }
}
