package net.onrc.onos.core.util;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * The class representing a Caller ID for an ONOS component.
 * This class is immutable.
 */
public final class CallerId {
    private final String value;

    /**
     * Default constructor.
     */
    public CallerId() {
        this.value = null;
    }

    /**
     * Copy constructor.
     *
     * @param otherCallerId the caller ID copied from
     */
    public CallerId(CallerId otherCallerId) {
        // Note: make a full copy if we change value to a mutable type
        value = otherCallerId.value;
    }

    /**
     * Constructor from a string value.
     *
     * @param value the value to use.
     */
    public CallerId(String value) {
        this.value = value;
    }

    /**
     * Get the value of the Caller ID.
     *
     * @return the value of the Caller ID.
     */
    @JsonProperty("value")
    public String value() {
        return value;
    }

    /**
     * Convert the Caller ID value to a string.
     *
     * @return the Caller ID value to a string.
     */
    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CallerId)) {
            return false;
        }

        CallerId otherCallerId = (CallerId) other;

        return value.equals(otherCallerId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
