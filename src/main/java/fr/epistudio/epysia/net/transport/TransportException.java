package fr.epistudio.epysia.net.transport;

import fr.epistudio.epysia.exceptions.EpysiaException;

public final class TransportException extends EpysiaException {
    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
