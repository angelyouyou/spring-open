package net.onrc.onos.ofcontroller.flowprogrammer.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class FlowProgrammerWebRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/setrate/{dpid}/{rate}/json", SetPushRateResource.class);
		router.attach("/suspend/{dpid}/json", SuspendPusherResource.class);
		router.attach("/resume/{dpid}/json", ResumePusherResource.class);
		router.attach("/barrier/{dpid}/json", SendBarrierResource.class);
		return router;
	}

	@Override
	public String basePath() {
		return "/wm/fprog";
	}

}