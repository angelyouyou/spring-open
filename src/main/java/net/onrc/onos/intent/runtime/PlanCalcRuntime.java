package net.onrc.onos.intent.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.util.MACAddress;
import net.onrc.onos.intent.FlowEntry;
import net.onrc.onos.intent.Intent;
import net.onrc.onos.intent.IntentOperation;
import net.onrc.onos.intent.IntentOperation.Operator;
import net.onrc.onos.intent.IntentOperationList;
import net.onrc.onos.intent.PathIntent;
import net.onrc.onos.intent.ShortestPathIntent;
import net.onrc.onos.ofcontroller.networkgraph.Link;
import net.onrc.onos.ofcontroller.networkgraph.LinkEvent;
import net.onrc.onos.ofcontroller.networkgraph.NetworkGraph;
import net.onrc.onos.ofcontroller.networkgraph.Port;
import net.onrc.onos.ofcontroller.networkgraph.Switch;

/**
 *
 * @author Brian O'Connor <bocon@onlab.us>
 *
 */

public class PlanCalcRuntime {

    NetworkGraph graph;

    public PlanCalcRuntime(NetworkGraph graph) {
	this.graph = graph;
    }

    public List<Set<FlowEntry>> computePlan(IntentOperationList intentOps) {
	Set<Collection<FlowEntry>> flowEntries = computeFlowEntries(intentOps);
	return buildPhases(flowEntries);
    }

    private Set<Collection<FlowEntry>> computeFlowEntries(IntentOperationList intentOps) {
	Set<Collection<FlowEntry>> flowEntries = new HashSet<>();
	for(IntentOperation i : intentOps) {
	    PathIntent intent = (PathIntent) i.intent;
	    Intent parent = intent.getParentIntent();
	    Port srcPort, dstPort, lastDstPort = null;
	    MACAddress srcMac, dstMac;
	    if(parent instanceof ShortestPathIntent) {
		ShortestPathIntent pathIntent = (ShortestPathIntent) parent;
		Switch srcSwitch = graph.getSwitch(pathIntent.getSrcSwitchDpid());
		srcPort = srcSwitch.getPort(pathIntent.getSrcPortNumber());
		srcMac = MACAddress.valueOf(pathIntent.getSrcMac());
		dstMac = MACAddress.valueOf(pathIntent.getDstMac());
		Switch dstSwitch = graph.getSwitch(pathIntent.getDstSwitchDpid());
		lastDstPort = dstSwitch.getPort(pathIntent.getDstPortNumber());
	    }
	    else {
		// TODO: log this error
		continue;
	    }
	    List<FlowEntry> entries = new ArrayList<>();
	    for(LinkEvent linkEvent : intent.getPath()) {
		Link link = graph.getLink(linkEvent.getSrc().getDpid(),
			  linkEvent.getSrc().getNumber(),
			  linkEvent.getDst().getDpid(),
			  linkEvent.getDst().getNumber());
		Switch sw = link.getSrcSwitch();
		dstPort = link.getSrcPort();
		FlowEntry fe = new FlowEntry(sw, srcPort, dstPort, srcMac, dstMac, i.operator);
		entries.add(fe);
		srcPort = link.getDstPort();
	    }
	    if(lastDstPort != null) {
		Switch sw = lastDstPort.getSwitch();
		dstPort = lastDstPort;
		FlowEntry fe = new FlowEntry(sw, srcPort, dstPort, srcMac, dstMac, i.operator);
		entries.add(fe);
	    }
	    // install flow entries in reverse order
	    Collections.reverse(entries);
	    flowEntries.add(entries);
	}
	return flowEntries;
    }

    private List<Set<FlowEntry>> buildPhases(Set<Collection<FlowEntry>> flowEntries) {
	Map<FlowEntry, Integer> map = new HashMap<>();
	List<Set<FlowEntry>> plan = new ArrayList<>();
	for(Collection<FlowEntry> c : flowEntries) {
	    for(FlowEntry e : c) {
		Integer i = map.get(e);
		if(i == null) {
		    i = Integer.valueOf(0);
		}
		switch(e.getOperator()) {
		case ADD:
		    i += 1;
		    break;
		case REMOVE:
		    i -= 1;
		    break;
		}
		map.put(e, i);
		System.out.println(e + " " + e.getOperator());
	    }
	}
		
	// really simple first iteration of plan
	//TODO: optimize the map in phases
	Set<FlowEntry> phase = new HashSet<>();
	for(FlowEntry e : map.keySet()) {
	    Integer i = map.get(e);
	    if(i == 0) {
		continue;
	    }
	    else if(i > 0) {
		e.setOperator(Operator.ADD);
	    }
	    else if(i < 0) {
		e.setOperator(Operator.REMOVE);
	    }
	    phase.add(e);
	}
	plan.add(phase);
		
	return plan;
    }
}