package net.onrc.onos.ofcontroller.flowmanager;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;

import org.openflow.protocol.OFMessage;

/**
 * Represents table of message queues attached to each switch. 
 * Each message should be ADD/DELETE of flow.
 * (MODIFY of flow might be handled, but future work)
 * @author Naoki Shiota
 *
 */
@Deprecated
public class FlowQueueTable {
	
	public enum QueueState {
		READY,				// Synchronized and working
		SYNCHRONIZING,		// Synchronization is running
//		SUSPENDED,			// Synchronized but stopping sending messages
	}
	
	private class QueueInfo {
		QueueState state;
		
		// Max rate of sending message (bytes/sec). 0 implies no limitation.
		long max_rate = 0;
	}
	
	private Map< IOFSwitch, Queue<OFMessage> > queues
		= new HashMap< IOFSwitch, Queue<OFMessage> >();
	private Map<IOFSwitch, QueueInfo> queue_info
		= new HashMap<IOFSwitch, QueueInfo>();
	
	public FlowQueueTable() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Add flow queue for given switch.
	 * @param sw
	 */
	public void addSwitchQueue(IOFSwitch sw) {
		QueueInfo info = new QueueInfo();
		
		if (queues.containsKey(sw)) {
			return;
		}
		
		queues.put(sw, new ArrayDeque<OFMessage>());
		queue_info.put(sw, info);
	}
	
	/**
	 * Delete flow queue for given switch.
	 * @param sw
	 */
	public void deleteSwitchQueue(IOFSwitch sw) {
		if (! queues.containsKey(sw)) {
			return;
		}
		
		queues.remove(sw);
		queue_info.remove(sw);
	}

	/**
	 * Get flow queue for given switch.
	 * @param sw
	 * @return
	 */
	public Queue<OFMessage> getQueue(IOFSwitch sw) {
		return queues.get(sw);
	}
	
	public Set<IOFSwitch> getSwitches() {
		return queues.keySet();
	}

	/**
	 * Get state of flow queue for given switch.
	 * @param sw
	 */
	public QueueState getQueueState(IOFSwitch sw) {
		QueueInfo info = queue_info.get(sw);
		if (info == null) {
			return null;
		}
		
		return info.state;
	}
	
	/**
	 * Set state of flow queue for given switch.
	 * @param sw
	 * @param state
	 */
	public void setQueueState(IOFSwitch sw, QueueState state) {
		QueueInfo info = queue_info.get(sw);
		if (info == null) {
			return;
		}
		
		info.state = state;
	}

	/**
	 * Get maximum rate for given switch.
	 * @param sw
	 */
	public long getQueueRate(IOFSwitch sw) {
		QueueInfo info = queue_info.get(sw);
		if (info == null) {
			return 0;
		}
		
		return info.max_rate;
	}

	/**
	 * Set maximum rate for given switch.
	 * @param sw
	 * @param rate
	 */
	public void setQueueRate(IOFSwitch sw, long rate) {
		QueueInfo info = queue_info.get(sw);
		if (info == null) {
			return;
		}
		
		info.max_rate = rate;
	}
	
	/**
	 * Get a lock for queue for given switch.
	 * If locked already, wait for unlock.
	 * @param sw
	 */
	public void lockQueue(IOFSwitch sw) {
		// TODO not yet implement
	}

	/**
	 * Get a lock for queue for given switch.
	 * If locked already, return false at once.
	 * @param sw
	 * @return
	 */
	public boolean lockQueueIfAvailable(IOFSwitch sw) {
		// TODO not yet implement
		return false;
	}

	/** Release a lock for queue for given switch.
	 * @param sw
	 */
	public void unlockQueue(IOFSwitch sw) {
		// TODO not yet implement
	}
}
