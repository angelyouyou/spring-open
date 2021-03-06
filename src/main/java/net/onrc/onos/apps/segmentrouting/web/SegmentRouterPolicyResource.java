package net.onrc.onos.apps.segmentrouting.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.onrc.onos.apps.segmentrouting.ISegmentRoutingService;
import net.onrc.onos.apps.segmentrouting.SegmentRoutingPolicy;
import net.onrc.onos.apps.segmentrouting.SegmentRoutingPolicy.PolicyType;
import net.onrc.onos.apps.segmentrouting.SegmentRoutingPolicyTunnel;
import net.onrc.onos.core.matchaction.match.PacketMatch;
import net.onrc.onos.core.packet.IPv4;
import net.onrc.onos.core.util.IPv4Net;

import org.codehaus.jackson.map.ObjectMapper;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for return router statistics
 *
 */
public class SegmentRouterPolicyResource extends ServerResource {
    protected final static Logger log =
            LoggerFactory.getLogger(SegmentRouterPolicyResource.class);

    @Post("json")
    public String createPolicy(String policyParams) {
        log.debug("createPolicy with policyParams {}", policyParams);
        ISegmentRoutingService segmentRoutingService =
                (ISegmentRoutingService) getContext().getAttributes().
                        get(ISegmentRoutingService.class.getCanonicalName());
        ObjectMapper mapper = new ObjectMapper();
        SegmentRouterPolicyRESTParams createParams = null;
        try {
            if (policyParams != null) {
                createParams = mapper.readValue(policyParams,
                        SegmentRouterPolicyRESTParams.class);
            }
        } catch (IOException ex) {
            log.error("Exception occurred parsing inbound JSON", ex);
            return "fail";
        }

        log.debug("createPolicy of type {} with params id {} src_ip {} dst_ip {}"
                + "proto {} src_port {} dst_port {} priority {} tunnel_id {}",
                createParams.getPolicy_type(),
                createParams.getPolicy_id(), createParams.getSrc_ip(),
                createParams.getDst_ip(), createParams.getProto_type(),
                createParams.getSrc_tp_port(), createParams.getDst_tp_port(),
                createParams.getPriority(), createParams.getTunnel_id());

        IPv4Net src_ip = (createParams.getSrc_ip() != null) ?
                new IPv4Net(createParams.getSrc_ip()) : null;
        IPv4Net dst_ip = (createParams.getDst_ip() != null) ?
                new IPv4Net(createParams.getDst_ip()) : null;
        Byte protoType = (createParams.getProto_type() != null) ?
                getProtoTypeByte(createParams.getProto_type()) : null;
        boolean result = segmentRoutingService.createPolicy(
                createParams.getPolicy_id(), null, null, null,
                src_ip, dst_ip, protoType,
                createParams.getSrc_tp_port(),
                createParams.getDst_tp_port(),
                createParams.getPriority(),
                createParams.getTunnel_id());
        return (result == true) ? "success" : "fail";
    }

    private Byte getProtoTypeByte(String protoType) {
        Byte protoTypeByte = null;
        switch (protoType) {
        case "tcp":
            protoTypeByte = IPv4.PROTOCOL_TCP;
            break;
        case "udp":
            protoTypeByte = IPv4.PROTOCOL_UDP;
            break;
        }
        return protoTypeByte;
    }

    @Delete("json")
    public String deletePolicy(String policyParams) {
        ISegmentRoutingService segmentRoutingService =
                (ISegmentRoutingService) getContext().getAttributes().
                        get(ISegmentRoutingService.class.getCanonicalName());
        ObjectMapper mapper = new ObjectMapper();
        SegmentRouterPolicyRESTParams createParams = null;
        try {
            if (policyParams != null) {
                createParams = mapper.readValue(policyParams,
                        SegmentRouterPolicyRESTParams.class);
            }
        } catch (IOException ex) {
            log.error("Exception occurred parsing inbound JSON", ex);
            return "fail";
        }

        log.debug("deletePolicy with Id {}", createParams.getPolicy_id());
        boolean result = segmentRoutingService.removePolicy(
                createParams.getPolicy_id());
        return (result == true) ? "deleted" : "fail";
    }

    @Get("json")
    public Object getPolicy() {
        ISegmentRoutingService segmentRoutingService =
                (ISegmentRoutingService) getContext().getAttributes().
                        get(ISegmentRoutingService.class.getCanonicalName());
        List<SegmentRouterPolicyInfo> policyList = new ArrayList<SegmentRouterPolicyInfo>();
        Collection<SegmentRoutingPolicy> policies = segmentRoutingService.getPoclicyTable();
        Iterator<SegmentRoutingPolicy> piI = policies.iterator();
        while(piI.hasNext()){
            SegmentRoutingPolicy policy = piI.next();
            String policyId = policy.getPolicyId();
            String tunnelId = null;
            if (policy.getType() == PolicyType.TUNNEL_FLOW) {
                tunnelId = ((SegmentRoutingPolicyTunnel)policy).getTunnelId();
            }
            int priority = policy.getPriority();
            String policyType = policy.getType().name();
            PacketMatch flowEntries = policy.getMatch();
            SegmentRouterPolicyInfo pInfo = new SegmentRouterPolicyInfo(policyId, policyType, tunnelId,
                    priority,  flowEntries);
            policyList.add(pInfo);
        }
        log.debug("getPolicy with params");
        return policyList;
    }
}
