package fr.epistudio.epysia.net.security;

import fr.epistudio.epysia.exceptions.EpysiaException;

public final class MessageAuthenticationException extends EpysiaException {
    public MessageAuthenticationException(String message) {
        super(message);
    }
}
