package net.onrc.onos.core.flowprogrammer.web;

import net.onrc.onos.core.util.Dpid;

import org.projectfloodlight.openflow.util.HexString;
import org.restlet.resource.Get;

/**
 * FlowProgrammer REST API implementation: Resume sending message to switch.
 * <p/>
 * GET /wm/fprog/pusher/resume/{dpid}/json"
 */
public class ResumePusherResource extends PusherResource {
    /**
     * Implement the API.
     *
     * @return true if succeeded, false if failed.
     */
    @Get("json")
    public boolean retrieve() {
        if (!init()) {
            return false;
        }

        long dpid;
        try {
            dpid = HexString.toLong((String) getRequestAttributes().get("dpid"));
        } catch (NumberFormatException e) {
            log.error("Invalid number format");
            return false;
        }

        return pusher.resume(new Dpid(dpid));
    }
}
