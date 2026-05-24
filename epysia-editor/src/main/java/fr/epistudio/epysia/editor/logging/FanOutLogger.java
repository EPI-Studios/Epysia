package fr.epistudio.epysia.editor.logging;

import fr.epistudio.epysia.logging.Logger;

import java.util.List;

public final class FanOutLogger implements Logger {

    private final List<Logger> targets;

    public FanOutLogger(List<Logger> targets) {
        this.targets = List.copyOf(targets);
    }

    @Override
    public void info(String message) {
        for (Logger target : targets) {
            target.info(message);
        }
    }

    @Override
    public void warn(String message) {
        for (Logger target : targets) {
            target.warn(message);
        }
    }

    @Override
    public void error(String message) {
        for (Logger target : targets) {
            target.error(message);
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        for (Logger target : targets) {
            target.error(message, cause);
        }
    }
}
