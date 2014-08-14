package net.onrc.onos.api.newintent;

/**
 * Base intent implementation.
 */
public abstract class AbstractIntent implements Intent {

    private final IntentId id;

    /**
     * Creates a base intent with the specified identifier.
     *
     * @param id intent identifier
     */
    protected AbstractIntent(IntentId id) {
        this.id = id;
    }

    @Override
    public IntentId getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractIntent that = (AbstractIntent) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
