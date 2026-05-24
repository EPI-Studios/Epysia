package fr.epistudio.epysia.exceptions;

public class EpysiaException extends RuntimeException {
    public EpysiaException(String message) {
        super(message);
    }

    public EpysiaException(String message, Throwable cause) {
        super(message, cause);
    }
}
