package fr.epistudio.epysia.logging;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class ConsoleLogger implements Logger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final PrintStream sink;

    public ConsoleLogger() {
        this(System.err);
    }

    public ConsoleLogger(PrintStream sink) {
        this.sink = sink;
    }

    @Override
    public void info(String message) {
        write("INFO", message, null);
    }

    @Override
    public void warn(String message) {
        write("WARN", message, null);
    }

    @Override
    public void error(String message) {
        write("ERROR", message, null);
    }

    @Override
    public void error(String message, Throwable cause) {
        write("ERROR", message, cause);
    }

    private void write(String level, String message, Throwable cause) {
        String timestamp = LocalTime.now().format(TIMESTAMP_FORMAT);
        sink.println("[" + timestamp + "] " + level + " " + message);
        if (cause != null) {
            sink.println(formatStackTrace(cause));
        }
    }

    private static String formatStackTrace(Throwable cause) {
        StringWriter writer = new StringWriter();
        cause.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
