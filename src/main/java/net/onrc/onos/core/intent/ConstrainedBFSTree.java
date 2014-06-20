package net.onrc.onos.core.intent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import net.onrc.onos.core.topology.Link;
import net.onrc.onos.core.topology.LinkEvent;
import net.onrc.onos.core.topology.Switch;

/**
 * This class creates bandwidth constrained breadth first tree and returns paths
 * from root switch to leaf switches which satisfies the bandwidth condition. If
 * bandwidth parameter is not specified, the normal breadth first tree will be
 * calculated. The paths are snapshot paths at the point of the class
 * instantiation.
 */
public class ConstrainedBFSTree {
    LinkedList<Switch> switchQueue = new LinkedList<>();
    HashSet<Switch> switchSearched = new HashSet<>();
    HashMap<Long, LinkEvent> upstreamLinks = new HashMap<>();
    HashMap<Switch, Path> paths = new HashMap<>();
    Switch rootSwitch;
    PathIntentMap intents = null;
    double bandwidth = 0.0; // 0.0 means no limit for bandwidth (normal BFS tree)

    /**
     * Constructor.
     *
     * @param rootSwitch root of the BFS tree
     */
    public ConstrainedBFSTree(Switch rootSwitch) {
        this.rootSwitch = rootSwitch;
        calcTree();
    }

    /**
     * Constructor.
     *
     * @param rootSwitch root switch of the BFS tree
     * @param intents map of Intents
     * @param bandwidth bandwidth constraint
     */
    public ConstrainedBFSTree(Switch rootSwitch, PathIntentMap intents, double bandwidth) {
        this.rootSwitch = rootSwitch;
        this.intents = intents;
        this.bandwidth = bandwidth;
        calcTree();
    }

    /**
     * Calculates the BFS tree using any provided constraints and Intents.
     */
    protected void calcTree() {
        switchQueue.add(rootSwitch);
        switchSearched.add(rootSwitch);
        while (!switchQueue.isEmpty()) {
            Switch sw = switchQueue.poll();
            for (Link link : sw.getOutgoingLinks()) {
                Switch reachedSwitch = link.getDstPort().getSwitch();
                if (switchSearched.contains(reachedSwitch)) {
                    continue;
                }
                if (intents != null &&
                        intents.getAvailableBandwidth(link) < bandwidth) {
                    continue;
                }
                switchQueue.add(reachedSwitch);
                switchSearched.add(reachedSwitch);
                upstreamLinks.put(reachedSwitch.getDpid(), new LinkEvent(link));
            }
        }
    }

    /**
     * Return the computed path from the root switch to the leaf switch.
     *
     * @param leafSwitch the leaf switch
     * @return the Path from the root switch to the leaf switch
     */
    public Path getPath(Switch leafSwitch) {
        Path path = paths.get(leafSwitch);
        Long rootSwitchDpid = rootSwitch.getDpid();
        if (path == null && switchSearched.contains(leafSwitch)) {
            path = new Path();
            Long sw = leafSwitch.getDpid();
            while (!sw.equals(rootSwitchDpid)) {
                LinkEvent upstreamLink = upstreamLinks.get(sw);
                path.add(0, upstreamLink);
                sw = upstreamLink.getSrc().getDpid();
            }
            paths.put(leafSwitch, path);
        }
        return path;
    }
}
