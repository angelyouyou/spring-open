package net.onrc.onos.core.topology.web;

import java.io.IOException;

import net.onrc.onos.core.topology.INetworkGraphService;
import net.onrc.onos.core.topology.NetworkGraph;
import net.onrc.onos.core.topology.serializers.DeviceSerializer;

import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.module.SimpleModule;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkGraphDevicesResource extends ServerResource {
    private static final Logger log = LoggerFactory.getLogger(NetworkGraphDevicesResource.class);

    @Get("json")
    public String retrieve() {
        INetworkGraphService networkGraphService = (INetworkGraphService) getContext().getAttributes().
                get(INetworkGraphService.class.getCanonicalName());

        NetworkGraph graph = networkGraphService.getNetworkGraph();

        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule("module", new Version(1, 0, 0, null));
        module.addSerializer(new DeviceSerializer());
        mapper.registerModule(module);

        graph.acquireReadLock();
        try {
            return mapper.writeValueAsString(graph.getDevices());
        } catch (IOException e) {
            log.error("Error writing device list to JSON", e);
            return "";
        } finally {
            graph.releaseReadLock();
        }
    }

}
