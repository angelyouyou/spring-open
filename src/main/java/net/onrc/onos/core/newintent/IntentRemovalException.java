package net.onrc.onos.core.newintent;

import net.onrc.onos.api.newintent.IntentException;

/**
 * An exception thrown when intent removal failed.
 */
public class IntentRemovalException extends IntentException {
    private static final long serialVersionUID = -5259226322037891951L;

    public IntentRemovalException() {
        super();
    }

    public IntentRemovalException(String message) {
        super(message);
    }

    public IntentRemovalException(String message, Throwable cause) {
        super(message, cause);
    }
}
