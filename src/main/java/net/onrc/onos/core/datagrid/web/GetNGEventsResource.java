package net.onrc.onos.core.datagrid.web;

import java.util.Collection;

import net.onrc.onos.core.datagrid.IDatagridService;
import net.onrc.onos.core.datagrid.IEventChannel;
import net.onrc.onos.core.topology.TopologyEvent;
import net.onrc.onos.core.topology.TopologyManager;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetNGEventsResource extends ServerResource {

    public static final Logger log = LoggerFactory.getLogger(GetNGEventsResource.class);

    @Get("json")
    public String retrieve() {
        IDatagridService datagridService =
                (IDatagridService) getContext().getAttributes().
                        get(IDatagridService.class.getCanonicalName());


        log.debug("Get topology events");

        IEventChannel<byte[], TopologyEvent> channel = datagridService.createChannel(TopologyManager.EVENT_CHANNEL_NAME,
                byte[].class, TopologyEvent.class);

        Collection<TopologyEvent> entries = channel.getAllEntries();

        StringBuilder result = new StringBuilder();
        for (TopologyEvent event : entries) {
            result.append(event.toString() + "\n");
        }

        return result.toString();
    }
}
