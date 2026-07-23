package fr.epistudio.epysia.logging;

import java.util.List;

public final class CompositeLogger implements Logger {

    private final List<Logger> delegates;

    public CompositeLogger(List<Logger> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void info(String message) {
        for (Logger delegate : delegates) {
            delegate.info(message);
        }
    }

    @Override
    public void warn(String message) {
        for (Logger delegate : delegates) {
            delegate.warn(message);
        }
    }

    @Override
    public void error(String message) {
        for (Logger delegate : delegates) {
            delegate.error(message);
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        for (Logger delegate : delegates) {
            delegate.error(message, cause);
        }
    }
}
