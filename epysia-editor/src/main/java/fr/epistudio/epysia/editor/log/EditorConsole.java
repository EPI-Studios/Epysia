package fr.epistudio.epysia.editor.log;

import fr.epistudio.epysia.editor.play.PlayLogLine;
import fr.epistudio.epysia.logging.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class EditorConsole {

    private static final int MAX_QUEUED_LINES = 4096;

    private final ConcurrentLinkedQueue<PlayLogLine> pendingLines = new ConcurrentLinkedQueue<>();
    private final Logger loggerAdapter = new ConsoleLoggerAdapter();

    public Logger logger() {
        return loggerAdapter;
    }

    public PlayLogLine pollLine() {
        return pendingLines.poll();
    }

    public void info(String message) {
        emit(PlayLogLine.Level.INFO, message);
    }

    public void warn(String message) {
        emit(PlayLogLine.Level.WARN, message);
    }

    public void error(String message) {
        emit(PlayLogLine.Level.ERROR, message);
    }

    public void system(String message) {
        emit(PlayLogLine.Level.SYSTEM, message);
    }

    private void emit(PlayLogLine.Level level, String message) {
        while (pendingLines.size() >= MAX_QUEUED_LINES) {
            pendingLines.poll();
        }
        pendingLines.add(new PlayLogLine(level, message));
    }

    private final class ConsoleLoggerAdapter implements Logger {

        @Override
        public void info(String message) {
            emit(PlayLogLine.Level.INFO, message);
        }

        @Override
        public void warn(String message) {
            emit(PlayLogLine.Level.WARN, message);
        }

        @Override
        public void error(String message) {
            emit(PlayLogLine.Level.ERROR, message);
        }

        @Override
        public void error(String message, Throwable cause) {
            StringWriter writer = new StringWriter();
            cause.printStackTrace(new PrintWriter(writer));
            emit(PlayLogLine.Level.ERROR, message + " - " + writer);
        }
    }
}
