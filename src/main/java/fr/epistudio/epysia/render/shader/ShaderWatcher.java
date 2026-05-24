package fr.epistudio.epysia.render.shader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ShaderWatcher {

    private static final long POLL_INTERVAL_MILLIS = 250L;

    private final Optional<Path> filesystemRoot;
    private final Map<String, Long> lastModifiedByPath = new ConcurrentHashMap<>();
    private final Map<String, List<Runnable>> listenersByPath = new HashMap<>();
    private final ConcurrentLinkedQueue<String> pendingChanges = new ConcurrentLinkedQueue<>();
    private final Set<String> watchedPaths = new HashSet<>();
    private Thread pollingThread;
    private volatile boolean stopped;

    public ShaderWatcher(Optional<Path> filesystemRoot) {
        this.filesystemRoot = filesystemRoot.filter(Files::isDirectory);
        startPollingThread();
    }

    private void startPollingThread() {
        if (filesystemRoot.isEmpty()) {
            return;
        }
        pollingThread = new Thread(this::pollLoop, "epysia-shader-watcher");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    public boolean active() {
        return filesystemRoot.isPresent();
    }

    public synchronized void watch(List<String> relativePaths, Runnable onChange) {
        if (filesystemRoot.isEmpty()) {
            return;
        }
        Path root = filesystemRoot.get();
        for (String relativePath : relativePaths) {
            watchedPaths.add(relativePath);
            lastModifiedByPath.putIfAbsent(relativePath, readMTime(root, relativePath));
            listenersByPath.computeIfAbsent(relativePath, ignored -> new ArrayList<>()).add(onChange);
        }
    }

    public void poll() {
        String relativePath;
        while ((relativePath = pendingChanges.poll()) != null) {
            notifyListeners(relativePath);
        }
    }

    private void pollLoop() {
        Path root = filesystemRoot.orElseThrow();
        while (!stopped) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            scanWatchedPaths(root);
        }
    }

    private void scanWatchedPaths(Path root) {
        Set<String> snapshot;
        synchronized (this) {
            snapshot = new HashSet<>(watchedPaths);
        }
        for (String relativePath : snapshot) {
            long current = readMTime(root, relativePath);
            Long previous = lastModifiedByPath.get(relativePath);
            if (previous != null && current > previous) {
                lastModifiedByPath.put(relativePath, current);
                pendingChanges.add(relativePath);
            }
        }
    }

    private synchronized void notifyListeners(String relativePath) {
        for (Runnable listener : listenersByPath.getOrDefault(relativePath, List.of())) {
            listener.run();
        }
    }

    public void stop() {
        stopped = true;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    private static long readMTime(Path root, String relativePath) {
        Path absolute = root.resolve(relativePath);
        try {
            return Files.getLastModifiedTime(absolute).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }
}
