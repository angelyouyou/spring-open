package net.onrc.onos.core.flowprogrammer.web;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.util.HexString;
import org.restlet.resource.Get;

/**
 * FlowProgrammer REST API implementation: Send barrier message to switch.
 * <p/>
 * GET /wm/fprog/pusher/barrier/{dpid}/json"
 */
public class SendBarrierResource extends PusherResource {
    /**
     * Implement the API.
     *
     * @return true if succeeded, false if failed.
     */
    @Get("json")
    public OFBarrierReply retrieve() {
        if (!init()) {
            return null;
        }
        long dpid;
        try {
            dpid = HexString.toLong((String) getRequestAttributes().get("dpid"));
        } catch (NumberFormatException e) {
            log.error("Invalid number format");
            return null;
        }

        IOFSwitch sw = provider.getSwitches().get(dpid);
        if (sw == null) {
            log.error("Invalid dpid");
            return null;
        }

        return pusher.barrier(sw);
    }
}
