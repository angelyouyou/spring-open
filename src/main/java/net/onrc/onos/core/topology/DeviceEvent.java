package net.onrc.onos.core.topology;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import net.floodlightcontroller.util.MACAddress;
import net.onrc.onos.core.topology.web.serializers.DeviceEventSerializer;
import net.onrc.onos.core.util.SwitchPort;

import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * Self-contained Device event(s) Object
 * <p/>
 * Device event differ from other events.
 * Device Event represent add/remove of attachmentPoint.
 * Not add/remove of the DeviceObject itself.
 * <p/>
 * Multiple attachmentPoints can be specified to batch events into 1 object.
 * Each should be treated as independent events.
 * <p/>
 * TODO: We probably want common base class/interface for Self-Contained Event Object
 */
@JsonSerialize(using = DeviceEventSerializer.class)
public class DeviceEvent {
    private final MACAddress mac;
    protected List<SwitchPort> attachmentPoints;
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
    }

    public MACAddress getMac() {
        return mac;
    }

    public List<SwitchPort> getAttachmentPoints() {
        return attachmentPoints;
    }

    public void setAttachmentPoints(List<SwitchPort> attachmentPoints) {
        this.attachmentPoints = attachmentPoints;
    }

    public void addAttachmentPoint(SwitchPort attachmentPoint) {
        // may need to maintain uniqness
        this.attachmentPoints.add(0, attachmentPoint);
    }

    @Override
    public String toString() {
        return "[DeviceEvent " + mac + " attachmentPoints:" + attachmentPoints + "]";
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
