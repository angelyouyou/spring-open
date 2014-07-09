package net.onrc.onos.core.linkdiscovery.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.onrc.onos.core.linkdiscovery.ILinkDiscoveryService;
import net.onrc.onos.core.linkdiscovery.Link;
import net.onrc.onos.core.linkdiscovery.LinkInfo;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class LinksResource extends ServerResource {

    @Get("json")
    public Set<LinkWithType> retrieve() {
        ILinkDiscoveryService ld = (ILinkDiscoveryService) getContext().getAttributes().
                get(ILinkDiscoveryService.class.getCanonicalName());
        Map<Link, LinkInfo> links = new HashMap<Link, LinkInfo>();
        Set<LinkWithType> returnLinkSet = new HashSet<LinkWithType>();

        if (ld != null) {
            links.putAll(ld.getLinks());
            for (Entry<Link, LinkInfo> e : links.entrySet()) {
                Link link = e.getKey();
                LinkInfo info = e.getValue();
                LinkWithType lwt = new LinkWithType(link,
                        info.getSrcPortState(),
                        info.getDstPortState(),
                        info.getLinkType());
                returnLinkSet.add(lwt);
            }
        }
        return returnLinkSet;
    }
}
