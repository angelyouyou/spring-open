package net.onrc.onos.core.datagrid.web;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 * REST API implementation for the Datagrid.
 */
public class DatagridWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/add/intents/json", IntentResource.class);
        router.attach("/get/intent/{intent_id}/json", IntentResource.class);
        router.attach("/get/ng-events/json", GetNGEventsResource.class);
        router.attach("/get/intents/{category}/json", IntentResource.class);
        router.attach("/get/intent/{category}/{intent_id}/json", IntentResource.class);
        router.attach("/delete/intents/json", IntentResource.class);
        return router;
    }

    /**
     * Set the base path for the Topology.
     */
    @Override
    public String basePath() {
        return "/wm/onos/datagrid";
    }
}
