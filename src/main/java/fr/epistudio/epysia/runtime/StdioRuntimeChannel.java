package fr.epistudio.epysia.runtime;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.InputStream;

public final class StdioRuntimeChannel implements RuntimeChannel {

    private final PrintStream eventStream;
    private final ConcurrentLinkedQueue<RuntimeCommand> incoming = new ConcurrentLinkedQueue<>();
    private final Thread readerThread;
    private volatile boolean running = true;

    public StdioRuntimeChannel() {
        this(System.out, System.in);
    }

    public StdioRuntimeChannel(PrintStream eventStream, InputStream commandStream) {
        this.eventStream = eventStream;
        this.readerThread = new Thread(() -> readLoop(commandStream), "epysia-runtime-stdio-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    @Override
    public void send(RuntimeEvent event) {
        eventStream.println(encode(event));
        eventStream.flush();
    }

    @Override
    public Optional<RuntimeCommand> pollCommand() {
        return Optional.ofNullable(incoming.poll());
    }

    @Override
    public void close() {
        running = false;
        readerThread.interrupt();
    }

    private void readLoop(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                decode(line).ifPresent(incoming::add);
            }
        } catch (IOException ignored) {
        }
    }

    private static String encode(RuntimeEvent event) {
        JsonWriter writer = new JsonWriter().beginObject();
        switch (event) {
            case RuntimeEvent.Log log -> writer
                    .key("type").valueString("log")
                    .key("level").valueString(log.level())
                    .key("message").valueString(log.message())
                    .key("stack").valueString(log.stackTrace());
            case RuntimeEvent.FrameStats stats -> writer
                    .key("type").valueString("frameStats")
                    .key("fps").valueNumber(stats.framesPerSecond())
                    .key("ms").valueNumber(stats.frameMillis());
            case RuntimeEvent.Ready ready -> writer
                    .key("type").valueString("ready")
                    .key("title").valueString(ready.windowTitle())
                    .key("width").valueNumber(ready.width())
                    .key("height").valueNumber(ready.height());
            case RuntimeEvent.Stopped stopped -> writer
                    .key("type").valueString("stopped")
                    .key("reason").valueString(stopped.reason());
        }
        writer.endObject();
        return writer.toString().replace("\n", "").replace("\r", "");
    }

    @SuppressWarnings("unchecked")
    private static Optional<RuntimeCommand> decode(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> root = new JsonReader(line).readRootObject();
            Object type = root.get("type");
            if (!(type instanceof String typeName)) {
                return Optional.empty();
            }
            return switch (typeName) {
                case "pause" -> Optional.of(new RuntimeCommand.Pause());
                case "resume" -> Optional.of(new RuntimeCommand.Resume());
                case "quit" -> Optional.of(new RuntimeCommand.Quit());
                default -> Optional.empty();
            };
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
