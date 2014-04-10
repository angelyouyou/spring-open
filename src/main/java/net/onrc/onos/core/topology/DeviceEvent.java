package net.onrc.onos.core.topology;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.util.MACAddress;
import net.onrc.onos.core.topology.PortEvent.SwitchPort;

/**
 * Self-contained Device event(s) Object
 * <p/>
 * Device event differ from other events.
 * Device Event represent add/remove of attachmentPoint or ipAddress.
 * Not add/remove of the DeviceObject itself.
 * <p/>
 * Multiple attachmentPoints can be specified to batch events into 1 object.
 * Each should be treated as independent events.
 * <p/>
 * TODO: We probably want common base class/interface for Self-Contained Event Object
 */
public class DeviceEvent {
    private final MACAddress mac;
    protected List<SwitchPort> attachmentPoints;
    protected Set<InetAddress> ipAddresses;
    private long lastSeenTime;

    /**
     * Default constructor for Serializer to use.
     */
    @Deprecated
    public DeviceEvent() {
        mac = null;
    }

    public DeviceEvent(MACAddress mac) {
        if (mac == null) {
            throw new IllegalArgumentException("Device mac cannot be null");
        }
        this.mac = mac;
        this.attachmentPoints = new LinkedList<>();
        this.ipAddresses = new HashSet<>();
    }

    public MACAddress getMac() {
        return mac;
    }

    public List<SwitchPort> getAttachmentPoints() {
        return attachmentPoints;
    }

    public Set<InetAddress> getIpAddresses() {
        return ipAddresses;
    }

    public void setAttachmentPoints(List<SwitchPort> attachmentPoints) {
        this.attachmentPoints = attachmentPoints;
    }

    public void addAttachmentPoint(SwitchPort attachmentPoint) {
        // may need to maintain uniqness
        this.attachmentPoints.add(0, attachmentPoint);
    }


    public boolean addIpAddress(InetAddress addr) {
        return this.ipAddresses.add(addr);
    }

    public boolean removeIpAddress(InetAddress addr) {
        return this.ipAddresses.remove(addr);
    }

    @Override
    public String toString() {
        return "[DeviceEvent " + mac + " attachmentPoints:" + attachmentPoints + " ipAddr:" + ipAddresses + "]";
    }

    // Assuming mac is unique cluster-wide
    public static ByteBuffer getDeviceID(final byte[] mac) {
        return (ByteBuffer) ByteBuffer.allocate(2 + mac.length).putChar('D').put(mac).flip();
    }

    public byte[] getID() {
        return getDeviceID(mac.toBytes()).array();
    }

    public ByteBuffer getIDasByteBuffer() {
        return getDeviceID(mac.toBytes());
    }

    public long getLastSeenTime() {
        return lastSeenTime;
    }

    public void setLastSeenTime(long lastSeenTime) {
        this.lastSeenTime = lastSeenTime;
    }
}