package net.onrc.onos.apps.sdnip.web;

import java.util.Iterator;

import net.onrc.onos.apps.sdnip.ISdnIpService;
import net.onrc.onos.apps.sdnip.IPatriciaTree;
import net.onrc.onos.apps.sdnip.Prefix;
import net.onrc.onos.apps.sdnip.RestClient;
import net.onrc.onos.apps.sdnip.RibEntry;
import net.onrc.onos.apps.sdnip.RibUpdate;
import net.onrc.onos.apps.sdnip.RibUpdate.Operation;

import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST resource that handles REST calls from BGPd. This is the interface BGPd
 * uses to push RIB entries (routes) to SDN-IP.
 */
public class IncomingRequestResource extends ServerResource {
    private static final Logger log = LoggerFactory.getLogger(IncomingRequestResource.class);

    /**
     * Gets the contents of SDN-IP's route table.
     *
     * @return the contents of SDN-IP's route table formatted as JSON
     */
    @Get
    public String handleGetMethod() {
        String dest = (String) getRequestAttributes().get("dest");
        StringBuilder output = new StringBuilder(80);
        ISdnIpService sdnIp = (ISdnIpService) getContext()
                .getAttributes().
                get(ISdnIpService.class.getCanonicalName());

        if (dest == null) {
            IPatriciaTree<RibEntry> ptree = sdnIp.getPtree();
            output.append("{\n  \"rib\": [\n");
            boolean printed = false;

            synchronized (ptree) {
                Iterator<IPatriciaTree.Entry<RibEntry>> it = ptree.iterator();
                while (it.hasNext()) {
                    IPatriciaTree.Entry<RibEntry> entry = it.next();

                    if (printed) {
                        output.append(",\n");
                    }

                    output.append("    {\"prefix\": \"");
                    output.append(entry.getPrefix());
                    output.append("\", \"nexthop\": \"");
                    output.append(entry.getValue().getNextHop().getHostAddress());
                    output.append("\"}");

                    printed = true;
                }
            }

            output.append("\n  ]\n}\n");
        } else {
            // TODO Needs to be changed to use the new RestClient.get().

            // the dest here refers to router-id
            // bgpdRestIp includes port number, such as 1.1.1.1:8080
            String bgpdRestIp = sdnIp.getBgpdRestIp();
            String url = "http://" + bgpdRestIp + "/wm/bgp/" + dest;

            // Doesn't actually do anything with the response
            RestClient.get(url);

            output.append("Get rib from bgpd finished!\n");
        }

        return output.toString();
    }

    /**
     * Handler to receive a new RIB entry. The details of the RIB entry are
     * given as part of the URL.
     *
     * @return a String describing the result of the action
     */
    @Post
    public String handlePostMethod() {
        ISdnIpService sdnIp = (ISdnIpService) getContext()
                .getAttributes().
                get(ISdnIpService.class.getCanonicalName());

        String strSysuptime = (String) getRequestAttributes().get("sysuptime");
        String strSequence = (String) getRequestAttributes().get("sequence");
        String routerId = (String) getRequestAttributes().get("routerid");
        String prefix = (String) getRequestAttributes().get("prefix");
        String mask = (String) getRequestAttributes().get("mask");
        String nexthop = (String) getRequestAttributes().get("nexthop");
        String capability = (String) getRequestAttributes().get("capability");

        log.debug("sysuptime: {}", strSysuptime);
        log.debug("sequence: {}", strSequence);

        String reply = "";

        if (capability == null) {
            // this is a prefix add
            Prefix p;
            long sysUpTime, sequenceNum;
            try {
                p = new Prefix(prefix, Integer.valueOf(mask));
                sysUpTime = Long.parseLong(strSysuptime);
                sequenceNum = Long.parseLong(strSequence);
            } catch (NumberFormatException e) {
                reply = "[POST: mask format is wrong]";
                log.info(reply);
                return reply + "\n";
            } catch (IllegalArgumentException e1) {
                reply = "[POST: prefix format is wrong]";
                log.info(reply);
                return reply + "\n";
            }

            RibEntry rib = new RibEntry(routerId, nexthop, sysUpTime,
                    sequenceNum);

            sdnIp.newRibUpdate(new RibUpdate(Operation.UPDATE, p, rib));

            reply = "[POST: " + prefix + "/" + mask + ":" + nexthop + "]";
            log.info(reply);
        } else {
            reply = "[POST-capability: " + capability + "]\n";
            log.info(reply);
            // to store the number in the top node of the Ptree
        }
        /*else if ("1".equals(capability)) {
            reply = "[POST-capability: " + capability + "]\n";
            log.info(reply);
            // to store the number in the top node of the Ptree
        }*/

        return reply + "\n";
    }

    /**
     * Handler to remove a RIB entry from ONOS's route table. The details of
     * the route to remove are passed as part of the URL.
     *
     * @return a String describing the result of the action
     */
    @Delete
    public String handleDeleteMethod() {
        ISdnIpService sdnIp = (ISdnIpService) getContext()
                .getAttributes().
                get(ISdnIpService.class.getCanonicalName());

        String strSysuptime = (String) getRequestAttributes().get("sysuptime");
        String strSequence = (String) getRequestAttributes().get("sequence");
        String routerId = (String) getRequestAttributes().get("routerid");
        String prefix = (String) getRequestAttributes().get("prefix");
        String mask = (String) getRequestAttributes().get("mask");
        String nextHop = (String) getRequestAttributes().get("nexthop");
        String capability = (String) getRequestAttributes().get("capability");

        log.debug("sysuptime: {}", strSysuptime);
        log.debug("sequence: {}", strSequence);

        //String reply = "";
        StringBuilder replyStringBuilder = new StringBuilder(80);

        if (capability == null) {
            // this is a prefix delete
            Prefix p;
            long sysUpTime, sequenceNum;
            try {
                p = new Prefix(prefix, Integer.valueOf(mask));
                sysUpTime = Long.parseLong(strSysuptime);
                sequenceNum = Long.parseLong(strSequence);
            } catch (NumberFormatException e) {
                String reply = "[DELE: mask format is wrong]";
                log.info(reply);
                return reply + "\n";
            } catch (IllegalArgumentException e1) {
                String reply = "[DELE: prefix format is wrong]";
                log.info(reply);
                return reply + "\n";
            }

            RibEntry r = new RibEntry(routerId, nextHop, sysUpTime, sequenceNum);

            sdnIp.newRibUpdate(new RibUpdate(Operation.DELETE, p, r));

            replyStringBuilder.append("[DELE: ")
                .append(prefix)
                .append('/')
                .append(mask)
                .append(':')
                .append(nextHop)
                .append(']');
        } else {
            // clear the local rib: Ptree
            sdnIp.clearPtree();
            replyStringBuilder.append("[DELE-capability: ")
                    .append(capability)
                    .append("; The local RibEntry is cleared!]\n");

            // to store the number in the top node of the Ptree
        }

        log.info(replyStringBuilder.toString());
        replyStringBuilder.append('\n');
        return replyStringBuilder.toString();
    }
}
