package fr.epistudio.epysia.logging;

import java.util.Locale;

public final class LevelFilteredLogger implements Logger {

    public enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        OFF
    }

    private final Logger delegate;
    private final Level threshold;

    public LevelFilteredLogger(Logger delegate, Level threshold) {
        this.delegate = delegate;
        this.threshold = threshold;
    }

    public static Level parse(String value, Level fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Level.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }

    public static Logger wrap(Logger delegate, String level) {
        Level threshold = parse(level, Level.INFO);
        return threshold == Level.INFO ? delegate : new LevelFilteredLogger(delegate, threshold);
    }

    @Override
    public void info(String message) {
        if (allows(Level.INFO)) {
            delegate.info(message);
        }
    }

    @Override
    public void warn(String message) {
        if (allows(Level.WARN)) {
            delegate.warn(message);
        }
    }

    @Override
    public void error(String message) {
        if (allows(Level.ERROR)) {
            delegate.error(message);
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        if (allows(Level.ERROR)) {
            delegate.error(message, cause);
        }
    }

    private boolean allows(Level level) {
        return level.ordinal() >= threshold.ordinal();
    }
}
