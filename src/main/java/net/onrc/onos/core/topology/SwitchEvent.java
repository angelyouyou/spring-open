package net.onrc.onos.core.topology;

import net.onrc.onos.core.topology.web.serializers.SwitchEventSerializer;
import net.onrc.onos.core.util.Dpid;

import java.nio.ByteBuffer;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * Self-contained Switch Object.
 * <p/>
 * TODO: Rename to match what it is. (Switch/Port/Link/Host)Snapshot?
 * FIXME: Current implementation directly use this object as
 *        Replication message, but should be sending update operation info.
 */
@JsonSerialize(using = SwitchEventSerializer.class)
public class SwitchEvent extends TopologyElement<SwitchEvent> {
    private final Dpid dpid;

    /**
     * Default constructor for Serializer to use.
     */
    @Deprecated
    protected SwitchEvent() {
        dpid = null;
    }

    /**
     * Creates the switch object.
     *
     * @param dpid Dpid to identify this switch
     */
    public SwitchEvent(Dpid dpid) {
        this.dpid = checkNotNull(dpid);
    }

    /**
     * Creates an unfrozen copy of given Object.
     *
     * @param original to make copy of.
     */
    public SwitchEvent(SwitchEvent original) {
        super(original);
        this.dpid = original.dpid;
    }

    /**
     * Gets the DPID identifying this switch.
     *
     * @return DPID
     */
    public Dpid getDpid() {
        return dpid;
    }

    /**
     * Gets the event origin DPID.
     *
     * @return the event origin DPID.
     */
    public Dpid getOriginDpid() {
        return this.dpid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (getClass() != o.getClass()) {
            return false;
        }
        SwitchEvent other = (SwitchEvent) o;

        // compare attributes
        if (!super.equals(o)) {
            return false;
        }

        return Objects.equals(this.dpid, other.dpid);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Objects.hashCode(dpid);
    }

    @Override
    public String toString() {
        return "[SwitchEvent " + dpid + "]";
    }

    public static final int SWITCHID_BYTES = 2 + 8;

    public static ByteBuffer getSwitchID(Dpid dpid) {
        return getSwitchID(dpid.value());
    }

    public static ByteBuffer getSwitchID(Long dpid) {
        if (dpid == null) {
            throw new IllegalArgumentException("dpid cannot be null");
        }
        return (ByteBuffer) ByteBuffer.allocate(SwitchEvent.SWITCHID_BYTES)
                .putChar('S').putLong(dpid).flip();
    }

    public byte[] getID() {
        return getSwitchID(dpid.value()).array();
    }

    public ByteBuffer getIDasByteBuffer() {
        return getSwitchID(dpid.value());
    }
}
