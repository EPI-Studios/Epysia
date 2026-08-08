package fr.epistudio.epysia.editor.play;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;

public final class PlayController {

    private static final String TEMP_PREFIX = "epysia-playmode-";
    private static final String TEMP_SUFFIX = ".epyscene";
    private static final long GRACEFUL_STOP_SECONDS = 2L;
    private static final int MAX_QUEUED_LINES = 4096;

    public enum State { IDLE, RUNNING }

    private final Project project;
    private final Supplier<Scene> activeScene;
    private final SceneSerializer serializer;
    private final EngineServices services;
    private final ConcurrentLinkedQueue<PlayLogLine> pendingLines = new ConcurrentLinkedQueue<>();

    private Scene playingScene;
    private State state = State.IDLE;
    private static final String LOCAL_HOST = "127.0.0.1";
    private final NetworkPlaySettings networkSettings = new NetworkPlaySettings();
    private final List<Process> companions = new ArrayList<>();
    private Process process;
    private Path tempScenePath;
    private String snapshot;
    private Thread stdoutReader;
    private Thread stderrReader;
    private Thread cleanupHook;

    public PlayController(Project project, Supplier<Scene> activeScene, SceneSerializer serializer, EngineServices services) {
        this.project = project;
        this.activeScene = activeScene;
        this.serializer = serializer;
        this.services = services;
    }

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public PlayLogLine pollLine() {
        return pendingLines.poll();
    }

    public void injectSystemLog(String message) {
        emit(PlayLogLine.Level.SYSTEM, message);
    }

    public void start() throws IOException {
        if (state == State.RUNNING) {
            return;
        }
        Scene scene = activeScene.get();
        playingScene = scene;
        snapshot = serializer.serialize(scene, gameObject -> true);
        tempScenePath = Files.createTempFile(TEMP_PREFIX, TEMP_SUFFIX);
        Files.writeString(tempScenePath, snapshot);
        process = launchEditorInstance();
        stdoutReader = startReader(process.getInputStream(), "epysia-play-stdout", false);
        stderrReader = startReader(process.getErrorStream(), "epysia-play-stderr", true);
        state = State.RUNNING;
        registerCleanupHook();
        emit(PlayLogLine.Level.SYSTEM, "Play started (pid " + process.pid() + ")");
        launchCompanionInstances();
    }

    public NetworkPlaySettings networkSettings() {
        return networkSettings;
    }

    private Process launchEditorInstance() throws IOException {
        Path root = project.rootDirectory();
        return switch (networkSettings.editorRole()) {
            case SINGLE_PLAYER -> PlayProcessLauncher.launch(tempScenePath, root, playWindowTitle());
            case LISTEN_SERVER -> PlayProcessLauncher.launchListenServer(tempScenePath, root,
                    playWindowTitle() + " (host)", networkSettings.port());
            case CLIENT, DEDICATED_SERVER -> PlayProcessLauncher.launchClient(tempScenePath, root,
                    playWindowTitle() + " (client 1)", LOCAL_HOST, networkSettings.port());
        };
    }

    private void launchCompanionInstances() {
        if (!networkSettings.networked()) {
            return;
        }
        launchDedicatedServerIfRequested();
        for (int index = 0; index < networkSettings.extraClients(); index++) {
            launchExtraClient(index + 2);
        }
    }

    private void launchDedicatedServerIfRequested() {
        if (!networkSettings.needsDedicatedServerProcess()) {
            return;
        }
        trackCompanion("server", () -> PlayProcessLauncher.launchDedicatedServer(
                tempScenePath, project.rootDirectory(), networkSettings.port()));
    }

    private void launchExtraClient(int ordinal) {
        trackCompanion("client " + ordinal, () -> PlayProcessLauncher.launchClient(
                tempScenePath, project.rootDirectory(),
                playWindowTitle() + " (client " + ordinal + ")", LOCAL_HOST, networkSettings.port()));
    }

    private void trackCompanion(String label, ProcessFactory factory) {
        try {
            Process companion = factory.start();
            companions.add(companion);
            emit(PlayLogLine.Level.SYSTEM, "Started " + label + " (pid " + companion.pid() + ")");
        } catch (IOException failure) {
            emit(PlayLogLine.Level.ERROR, "Could not start " + label + ": " + failure.getMessage());
        }
    }

    private void stopCompanions() {
        for (Process companion : companions) {
            companion.destroy();
        }
        companions.clear();
    }

    @FunctionalInterface
    private interface ProcessFactory {
        Process start() throws IOException;
    }

    public void stop() {
        if (state != State.RUNNING) {
            return;
        }
        stopCompanions();
        terminateProcess();
        joinReaders();
        restoreSnapshot();
        deleteTempScene();
        removeCleanupHook();
        state = State.IDLE;
        emit(PlayLogLine.Level.SYSTEM, "Play stopped");
    }

    public void pollExit() {
        if (state != State.RUNNING || process.isAlive()) {
            return;
        }
        joinReaders();
        stopCompanions();
        emit(PlayLogLine.Level.SYSTEM, "Subprocess exited with code " + process.exitValue());
        restoreSnapshot();
        deleteTempScene();
        removeCleanupHook();
        state = State.IDLE;
    }

    private void registerCleanupHook() {
        Process hookProcess = process;
        Path hookTempScene = tempScenePath;
        cleanupHook = new Thread(() -> orphanCleanup(hookProcess, hookTempScene), "epysia-play-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanupHook);
    }

    private void removeCleanupHook() {
        if (cleanupHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(cleanupHook);
        } catch (IllegalStateException shutdownAlreadyInProgress) {
        }
        cleanupHook = null;
    }

    private static void orphanCleanup(Process process, Path tempScene) {
        process.destroyForcibly();
        if (tempScene == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempScene);
        } catch (IOException ignored) {
        }
    }

    private String playWindowTitle() {
        return "Epysia - Play (" + project.name() + ")";
    }

    private Thread startReader(InputStream stream, String threadName, boolean stderrTreatAsError) {
        Thread thread = new Thread(() -> readLoop(stream, stderrTreatAsError), threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void readLoop(InputStream stream, boolean stderrTreatAsError) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (stderrTreatAsError) {
                    emit(PlayLogLine.Level.ERROR, line);
                } else {
                    emitParsedEvent(line);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void emitParsedEvent(String line) {
        if (line.isBlank()) {
            return;
        }
        if (!line.startsWith("{")) {
            emit(PlayLogLine.Level.INFO, line);
            return;
        }
        try {
            Map<String, Object> root = new JsonReader(line).readRootObject();
            Object type = root.get("type");
            if (!(type instanceof String typeName)) {
                emit(PlayLogLine.Level.INFO, line);
                return;
            }
            switch (typeName) {
                case "log" -> emit(parseLevel(root.get("level")), stringOr(root.get("message"), ""));
                case "frameStats" -> {
                }
                case "ready" -> emit(PlayLogLine.Level.SYSTEM,
                        "Ready: " + stringOr(root.get("title"), "")
                                + " " + numberOr(root.get("width")) + "x" + numberOr(root.get("height")));
                case "stopped" -> emit(PlayLogLine.Level.SYSTEM,
                        "Subprocess reported stopped: " + stringOr(root.get("reason"), ""));
                default -> emit(PlayLogLine.Level.INFO, line);
            }
        } catch (RuntimeException ignored) {
            emit(PlayLogLine.Level.INFO, line);
        }
    }

    private static PlayLogLine.Level parseLevel(Object value) {
        if (!(value instanceof String label)) {
            return PlayLogLine.Level.INFO;
        }
        return switch (label.toUpperCase()) {
            case "ERROR" -> PlayLogLine.Level.ERROR;
            case "WARN", "WARNING" -> PlayLogLine.Level.WARN;
            default -> PlayLogLine.Level.INFO;
        };
    }

    private static String stringOr(Object value, String fallback) {
        return value instanceof String stringValue ? stringValue : fallback;
    }

    private static int numberOr(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void emit(PlayLogLine.Level level, String message) {
        if (pendingLines.size() >= MAX_QUEUED_LINES) {
            pendingLines.poll();
        }
        pendingLines.add(new PlayLogLine(level, message));
    }

    private void terminateProcess() {
        process.destroy();
        try {
            if (!process.waitFor(GRACEFUL_STOP_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void joinReaders() {
        joinSilently(stdoutReader);
        joinSilently(stderrReader);
        stdoutReader = null;
        stderrReader = null;
    }

    private static void joinSilently(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(500L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void restoreSnapshot() {
        if (snapshot == null || playingScene == null) {
            return;
        }
        serializer.deserialize(playingScene, snapshot, services);
        snapshot = null;
        playingScene = null;
    }

    private void deleteTempScene() {
        if (tempScenePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempScenePath);
        } catch (IOException error) {
            services.logger().warn("[Play] Could not delete temp scene " + tempScenePath + ": " + error.getMessage());
        }
        tempScenePath = null;
    }
}
