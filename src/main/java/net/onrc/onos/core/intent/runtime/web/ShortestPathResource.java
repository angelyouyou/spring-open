package net.onrc.onos.core.intent.runtime.web;

import static net.onrc.onos.core.topology.web.TopologyResource.eval;
import java.util.LinkedList;
import java.util.List;

import net.onrc.onos.core.intent.ConstrainedBFSTree;
import net.onrc.onos.core.intent.Path;
import net.onrc.onos.core.topology.ITopologyService;
import net.onrc.onos.core.topology.Link;
import net.onrc.onos.core.topology.LinkData;
import net.onrc.onos.core.topology.Switch;
import net.onrc.onos.core.topology.MutableTopology;
import net.onrc.onos.core.util.Dpid;

import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class to access Shortest-Path information between switches.
 */
public class ShortestPathResource extends ServerResource {
    private static final Logger log = LoggerFactory.getLogger(ShortestPathResource.class);

    /**
     * Gets the Shortest-Path infomration between switches.
     *
     * @return a Representation with the Shortest-Path information between
     * switches if found, otherwise null. The Shortest-Path information is an
     * ordered collection of Links.
     */
    @Get("json")
    public Representation retrieve() {
        ITopologyService topologyService =
            (ITopologyService) getContext().getAttributes()
                .get(ITopologyService.class.getCanonicalName());

        //
        // Fetch the attributes
        //
        String srcDpidStr = (String) getRequestAttributes().get("src-dpid");
        String dstDpidStr = (String) getRequestAttributes().get("dst-dpid");
        Dpid srcDpid = new Dpid(srcDpidStr);
        Dpid dstDpid = new Dpid(dstDpidStr);
        log.debug("Getting Shortest Path {}--{}", srcDpidStr, dstDpidStr);

        //
        // Do the Shortest Path computation and return the result: a list of
        // links.
        //
        MutableTopology mutableTopology = topologyService.getTopology();
        mutableTopology.acquireReadLock();
        try {
            Switch srcSwitch = mutableTopology.getSwitch(srcDpid);
            Switch dstSwitch = mutableTopology.getSwitch(dstDpid);
            if ((srcSwitch == null) || (dstSwitch == null)) {
                return null;
            }
            ConstrainedBFSTree bfsTree = new ConstrainedBFSTree(srcSwitch);
            Path path = bfsTree.getPath(dstSwitch);
            if (path == null) {
                return null;
            }
            List<Link> links = new LinkedList<>();
            for (LinkData linkData : path) {
                Link link = mutableTopology.getLink(
                        linkData.getSrc().getDpid(),
                        linkData.getSrc().getPortNumber(),
                        linkData.getDst().getDpid(),
                        linkData.getDst().getPortNumber());
                if (link == null) {
                    return null;
                }
                links.add(link);
            }
            return eval(toRepresentation(links, null));
        } finally {
            mutableTopology.releaseReadLock();
        }
    }
}
