package net.onrc.onos.api.newintent;

import com.google.common.base.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A class to represent an intent related event.
 */
public class IntentEvent {

    // TODO: determine a suitable parent class; if one does not exist, consider introducing one

    private final long time;
    private final Intent intent;
    private final IntentState state;
    private final IntentState previous;

    /**
     * Creates an event describing a state change of an intent.
     *
     * @param intent subject intent
     * @param state new intent state
     * @param previous previous intent state
     * @param time time the event created in milliseconds since start of epoch
     * @throws NullPointerException if the intent or state is null
     */
    public IntentEvent(Intent intent, IntentState state, IntentState previous, long time) {
        this.intent = checkNotNull(intent);
        this.state = checkNotNull(state);
        this.previous = previous;
        this.time = time;
    }

    /**
     * Returns the state of the intent which caused the event.
     *
     * @return the state of the intent
     */
    public IntentState getState() {
        return state;
    }

    /**
     * Returns the previous state of the intent which caused the event.
     *
     * @return the previous state of the intent
     */
    public IntentState getPreviousState() {
        return previous;
    }

    /**
     * Returns the intent associated with the event.
     *
     * @return the intent
     */
    public Intent getIntent() {
        return intent;
    }

    /**
     * Returns the time at which the event was created.
     *
     * @return the time in milliseconds since start of epoch
     */
    public long getTime() {
        return time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        IntentEvent that = (IntentEvent) o;
        return Objects.equal(this.intent, that.intent)
                && Objects.equal(this.state, that.state)
                && Objects.equal(this.previous, that.previous)
                && Objects.equal(this.time, that.time);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(intent, state, previous, time);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(getClass())
                .add("intent", intent)
                .add("state", state)
                .add("previous", previous)
                .add("time", time)
                .toString();
    }
}
