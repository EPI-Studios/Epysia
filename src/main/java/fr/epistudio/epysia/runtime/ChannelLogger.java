package fr.epistudio.epysia.runtime;

import fr.epistudio.epysia.logging.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class ChannelLogger implements Logger {

    private final RuntimeChannel channel;
    private final Logger localFallback;

    public ChannelLogger(RuntimeChannel channel, Logger localFallback) {
        this.channel = channel;
        this.localFallback = localFallback;
    }

    @Override
    public void info(String message) {
        channel.send(RuntimeEvent.Log.info(message));
        if (localFallback != null) {
            localFallback.info(message);
        }
    }

    @Override
    public void warn(String message) {
        channel.send(RuntimeEvent.Log.warn(message));
        if (localFallback != null) {
            localFallback.warn(message);
        }
    }

    @Override
    public void error(String message) {
        channel.send(RuntimeEvent.Log.error(message, ""));
        if (localFallback != null) {
            localFallback.error(message);
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        channel.send(RuntimeEvent.Log.error(message, stackTraceOf(cause)));
        if (localFallback != null) {
            localFallback.error(message, cause);
        }
    }

    private static String stackTraceOf(Throwable cause) {
        if (cause == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        cause.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
