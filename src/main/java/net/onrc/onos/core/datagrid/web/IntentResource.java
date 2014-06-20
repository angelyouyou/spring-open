package net.onrc.onos.core.datagrid.web;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import net.floodlightcontroller.util.MACAddress;
import net.onrc.onos.core.intent.ConstrainedShortestPathIntent;
import net.onrc.onos.core.intent.Intent;
import net.onrc.onos.core.intent.IntentMap;
import net.onrc.onos.core.intent.IntentOperation;
import net.onrc.onos.core.intent.IntentOperationList;
import net.onrc.onos.core.intent.ShortestPathIntent;
import net.onrc.onos.core.intent.runtime.IPathCalcRuntimeService;
import net.onrc.onos.core.util.Dpid;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * old REST API implementation for the Intents.
 */
public class IntentResource extends ServerResource {
    private static final Logger log = LoggerFactory.getLogger(IntentResource.class);
    // TODO need to assign proper application id.
    private static final String APPLN_ID = "1";

    @Post("json")
    public String store(String jsonIntent) throws IOException {
        IPathCalcRuntimeService pathRuntime = (IPathCalcRuntimeService) getContext()
                .getAttributes().get(IPathCalcRuntimeService.class.getCanonicalName());
        if (pathRuntime == null) {
            log.warn("Failed to get path calc runtime");
            return "";
        }
        String reply = "";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jNode = null;
        try {
            jNode = mapper.readValue(jsonIntent, JsonNode.class);
        } catch (JsonGenerationException ex) {
            log.error("JsonGeneration exception ", ex);
        } catch (JsonMappingException ex) {
            log.error("JsonMappingException occurred", ex);
        } catch (IOException ex) {
            log.error("IOException occurred", ex);
        }

        if (jNode != null) {
            reply = parseJsonNode(jNode.getElements(), pathRuntime);
        }
        return reply;
    }

    @Delete("json")
    public String store() {
        IPathCalcRuntimeService pathRuntime = (IPathCalcRuntimeService) getContext().
                getAttributes().get(IPathCalcRuntimeService.class.getCanonicalName());
        pathRuntime.purgeIntents();
        // TODO no reply yet from the purge intents call
        return "";

    }

    @Get("json")
    public String retrieve() throws IOException {
        IPathCalcRuntimeService pathRuntime = (IPathCalcRuntimeService) getContext().
                getAttributes().get(IPathCalcRuntimeService.class.getCanonicalName());

        String intentCategory = (String) getRequestAttributes().get("category");
        IntentMap intentMap = null;
        if (intentCategory.equals("high")) {
            intentMap = pathRuntime.getHighLevelIntents();
        } else {
            intentMap = pathRuntime.getPathIntents();
        }
        ObjectMapper mapper = new ObjectMapper();
        String restStr = "";

        String intentId = (String) getRequestAttributes().get("intent_id");
        ArrayNode arrayNode = mapper.createArrayNode();
        Collection<Intent> intents = intentMap.getAllIntents();
        if (!intents.isEmpty()) {
            if ((intentId != null)) {
                String applnIntentId = APPLN_ID + ":" + intentId;
                Intent intent = intentMap.getIntent(applnIntentId);
                if (intent != null) {
                    ObjectNode node = mapper.createObjectNode();
                    // TODO refactor/remove duplicate code.
                    node.put("intent_id", intentId);
                    node.put("status", intent.getState().toString());
                    LinkedList<String> logs = intent.getLogs();
                    ArrayNode logNode = mapper.createArrayNode();
                    for (String intentLog : logs) {
                        logNode.add(intentLog);
                    }
                    node.put("log", logNode);
                    arrayNode.add(node);
                }
            } else {
                for (Intent intent : intents) {
                    ObjectNode node = mapper.createObjectNode();
                    String applnIntentId = intent.getId();
                    intentId = applnIntentId.split(":")[1];
                    node.put("intent_id", intentId);
                    node.put("status", intent.getState().toString());
                    LinkedList<String> logs = intent.getLogs();
                    ArrayNode logNode = mapper.createArrayNode();
                    for (String intentLog : logs) {
                        logNode.add(intentLog);
                    }
                    node.put("log", logNode);
                    arrayNode.add(node);
                }
            }
            restStr = mapper.writeValueAsString(arrayNode);
        }
        return restStr;
    }

    private String parseJsonNode(Iterator<JsonNode> nodes,
                                 IPathCalcRuntimeService pathRuntime) throws IOException {
        IntentOperationList operations = new IntentOperationList();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        while (nodes.hasNext()) {
            JsonNode node = nodes.next();
            if (node.isObject()) {
                JsonNode data;
                Iterator<String> fieldNames = node.getFieldNames();
                Map<String, Object> fields = new HashMap<>();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();
                    data = node.get(fieldName);
                    parseFields(data, fieldName, fields);
                }
                Intent intent = processIntent(fields, operations);
                appendIntentStatus(intent, (String) fields.get("intent_id"), mapper, arrayNode);
            }
        }
        pathRuntime.executeIntentOperations(operations);
        return mapper.writeValueAsString(arrayNode);
    }

    private void appendIntentStatus(Intent intent, final String intentId,
                                    ObjectMapper mapper, ArrayNode arrayNode) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        node.put("intent_id", intentId);
        node.put("status", intent.getState().toString());
        LinkedList<String> logs = intent.getLogs();
        ArrayNode logNode = mapper.createArrayNode();
        for (String intentLog : logs) {
            logNode.add(intentLog);
        }
        node.put("log", logNode);
        arrayNode.add(node);
    }

    private Intent processIntent(Map<String, Object> fields, IntentOperationList operations) {
        String intentType = (String) fields.get("intent_type");
        String intentOp = (String) fields.get("intent_op");
        Intent intent;
        String intentId = (String) fields.get("intent_id");
        boolean pathFrozen = false;
        if (intentId.startsWith("F")) { // TODO define REST API for frozen intents
            pathFrozen = true;
            intentId = intentId.substring(1);
        }
        String applnIntentId = APPLN_ID + ":" + intentId;

        IntentOperation.Operator operation = IntentOperation.Operator.ADD;
        if ((intentOp.equals("remove"))) {
            operation = IntentOperation.Operator.REMOVE;
        }
        Dpid srcSwitchDpid = new Dpid((String) fields.get("srcSwitch"));
        Dpid dstSwitchDpid = new Dpid((String) fields.get("dstSwitch"));

        if (intentType.equals("shortest_intent_type")) {
            ShortestPathIntent spi = new ShortestPathIntent(applnIntentId,
                    srcSwitchDpid.value(),
                    (long) fields.get("srcPort"),
                    MACAddress.valueOf((String) fields.get("srcMac")).toLong(),
                    dstSwitchDpid.value(),
                    (long) fields.get("dstPort"),
                    MACAddress.valueOf((String) fields.get("dstMac")).toLong());
            spi.setPathFrozen(pathFrozen);
            operations.add(operation, spi);
            intent = spi;
        } else {
            ConstrainedShortestPathIntent cspi = new ConstrainedShortestPathIntent(applnIntentId,
                    Long.decode((String) fields.get("srcSwitch")),
                    (long) fields.get("srcPort"),
                    MACAddress.valueOf((String) fields.get("srcMac")).toLong(),
                    Long.decode((String) fields.get("dstSwitch")),
                    (long) fields.get("dstPort"),
                    MACAddress.valueOf((String) fields.get("dstMac")).toLong(),
                    (double) fields.get("bandwidth"));
            cspi.setPathFrozen(pathFrozen);
            operations.add(operation, cspi);
            intent = cspi;
        }
        return intent;
    }

    private void parseFields(JsonNode node, String fieldName, Map<String, Object> fields) {
        if ((node.isTextual())) {
            fields.put(fieldName, node.getTextValue());
        } else if ((node.isInt())) {
            fields.put(fieldName, (long) node.getIntValue());
        } else if (node.isDouble()) {
            fields.put(fieldName, node.getDoubleValue());
        } else if ((node.isLong())) {
            fields.put(fieldName, node.getLongValue());
        }
    }
}
