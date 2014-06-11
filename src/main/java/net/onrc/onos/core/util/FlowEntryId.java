package net.onrc.onos.core.util;

import java.math.BigInteger;

import net.onrc.onos.core.util.serializers.FlowEntryIdDeserializer;
import net.onrc.onos.core.util.serializers.FlowEntryIdSerializer;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * The class representing a Flow Entry ID.
 * This class is immutable.
 */
@JsonDeserialize(using = FlowEntryIdDeserializer.class)
@JsonSerialize(using = FlowEntryIdSerializer.class)
public final class FlowEntryId {
    private static final long INVALID = -1;
    private final long value;

    /**
     * Default constructor.
     */
    public FlowEntryId() {
        this.value = FlowEntryId.INVALID;
    }

    /**
     * Constructor from an integer value.
     *
     * @param value the value to use.
     */
    public FlowEntryId(long value) {
        this.value = value;
    }

    /**
     * Constructor from a string.
     *
     * @param value the value to use.
     */
    public FlowEntryId(String value) {
        //
        // Use the help of BigInteger to parse strings representing
        // large unsigned hex long values.
        //
        char c = 0;
        if (value.length() > 2) {
            c = value.charAt(1);
        }
        if ((c == 'x') || (c == 'X')) {
            this.value = new BigInteger(value.substring(2), 16).longValue();
        } else {
            this.value = Long.decode(value);
        }
    }

    /**
     * Get the value of the Flow Entry ID.
     *
     * @return the value of the Flow Entry ID.
     */
    public long value() {
        return value;
    }

    /**
     * Test whether the Flow Entry ID is valid.
     *
     * @return true if the Flow Entry ID is valid, otherwise false.
     */
    @JsonIgnore
    public boolean isValid() {
        return (this.value() != FlowEntryId.INVALID);
    }

    /**
     * Returns true of the object is another Flow Entry ID with
     * the same value; otherwise, returns false.
     *
     * @param obj to compare
     */
    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass() == this.getClass()) {
            FlowEntryId entry = (FlowEntryId) obj;
            return this.value() == entry.value();
        }
        return false;
    }

    /**
     * Return the hash code of the Flow Entry ID.
     */
    @Override
    public int hashCode() {
        return Long.valueOf(value).hashCode();
    }

    /**
     * Convert the Flow Entry ID value to a hexadecimal string.
     *
     * @return the Flow Entry ID value to a hexadecimal string.
     */
    @Override
    public String toString() {
        return "0x" + Long.toHexString(this.value);
    }
}
