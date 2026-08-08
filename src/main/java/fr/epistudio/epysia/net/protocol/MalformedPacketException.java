package fr.epistudio.epysia.net.protocol;

import fr.epistudio.epysia.exceptions.EpysiaException;

public final class MalformedPacketException extends EpysiaException {
    public MalformedPacketException(String message) {
        super(message);
    }
}
