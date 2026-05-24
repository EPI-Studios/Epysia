package fr.epistudio.epysia.editor.play;

import fr.epistudio.epysia.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class PlayProcessLauncher {

    private static final String RUNNER_MAIN_CLASS = PlayRunner.class.getName();

    private final Logger logger;
    private final AtomicReference<Process> activeProcess = new AtomicReference<>();
    private Thread stdoutPumpThread;
    private Thread stderrPumpThread;
    private Thread watchdogThread;
    private Runnable onProcessExited;

    public PlayProcessLauncher(Logger logger) {
        this.logger = logger;
    }

    public void setOnProcessExited(Runnable callback) {
        this.onProcessExited = callback;
    }

    public boolean isRunning() {
        Process process = activeProcess.get();
        return process != null && process.isAlive();
    }

    public synchronized void launch(Path scenePath, String windowTitle) {
        if (isRunning()) {
            logger.warn("[Play] " +"Process already running; ignoring launch request.");
            return;
        }
        List<String> command = buildCommand(scenePath, windowTitle);
        logger.info("[Play] " +"Launching: " + String.join(" ", command));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        Process process;
        try {
            process = builder.start();
        } catch (Exception error) {
            logger.error("[Play] " +"Failed to start subprocess: " + error.getMessage());
            return;
        }
        activeProcess.set(process);
        stdoutPumpThread = startPump("play-stdout", process.getInputStream(), "stdout");
        stderrPumpThread = startPump("play-stderr", process.getErrorStream(), "stderr");
        watchdogThread = new Thread(() -> waitForExit(process), "play-watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
    }

    public synchronized void stop() {
        Process process = activeProcess.get();
        if (process == null || !process.isAlive()) {
            return;
        }
        logger.info("[Play] " +"Stopping play subprocess (pid=" + process.pid() + ")");
        process.destroy();
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private List<String> buildCommand(Path scenePath, String windowTitle) {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>();
        command.add(javaBinary);
        command.add("--enable-native-access=ALL-UNNAMED");
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            command.add("-XstartOnFirstThread");
        }
        command.add("-cp");
        command.add(classpath);
        command.add(RUNNER_MAIN_CLASS);
        command.add("--scene");
        command.add(scenePath.toAbsolutePath().toString());
        if (windowTitle != null && !windowTitle.isBlank()) {
            command.add("--title");
            command.add(windowTitle);
        }
        return command;
    }

    private Thread startPump(String name, InputStream stream, String tag) {
        Thread thread = new Thread(() -> pumpStream(stream, tag), name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void pumpStream(InputStream stream, String tag) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if ("stderr".equals(tag)) {
                    logger.warn("[game] " + line);
                } else {
                    logger.info("[game] " + line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void waitForExit(Process process) {
        try {
            int exit = process.waitFor();
            logger.info("[Play] " +"Subprocess exited with code " + exit);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            activeProcess.compareAndSet(process, null);
            Runnable callback = onProcessExited;
            if (callback != null) {
                callback.run();
            }
        }
    }
}
