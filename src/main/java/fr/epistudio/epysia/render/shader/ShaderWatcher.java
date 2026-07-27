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
    private final Map<String, Runnable> listenerByKey = new HashMap<>();
    private final Map<String, List<String>> pathsByListenerKey = new HashMap<>();
    private final ConcurrentLinkedQueue<String> pendingChanges = new ConcurrentLinkedQueue<>();
    private final Set<String> watchedPaths = new HashSet<>();
    private Thread pollingThread;
    private volatile boolean stopped;

    public ShaderWatcher(Optional<Path> filesystemRoot) {
        this.filesystemRoot = filesystemRoot.filter(Files::isDirectory);
        startPollingThread();
    }

    private void startPollingThread() {
        pollingThread = new Thread(this::pollLoop, "epysia-shader-watcher");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    public boolean active() {
        return true;
    }

    public synchronized void watch(List<String> shaderPaths, Runnable onChange) {
        watch(shaderPaths, String.valueOf(System.identityHashCode(onChange)), onChange);
    }

    public synchronized void watch(List<String> shaderPaths, String listenerKey, Runnable onChange) {
        unwatch(listenerKey);
        for (String shaderPath : shaderPaths) {
            watchedPaths.add(shaderPath);
            lastModifiedByPath.putIfAbsent(shaderPath, readModifiedMillis(shaderPath));
            listenersByPath.computeIfAbsent(shaderPath, ignored -> new ArrayList<>()).add(onChange);
            pathsByListenerKey.computeIfAbsent(listenerKey, ignored -> new ArrayList<>()).add(shaderPath);
        }
        listenerByKey.put(listenerKey, onChange);
    }

    public synchronized void unwatch(String listenerKey) {
        Runnable previous = listenerByKey.remove(listenerKey);
        if (previous == null) {
            return;
        }
        for (String shaderPath : pathsByListenerKey.getOrDefault(listenerKey, List.of())) {
            List<Runnable> listeners = listenersByPath.get(shaderPath);
            if (listeners != null) {
                listeners.remove(previous);
            }
        }
        pathsByListenerKey.remove(listenerKey);
    }

    public synchronized int listenerCount() {
        int total = 0;
        for (List<Runnable> listeners : listenersByPath.values()) {
            total += listeners.size();
        }
        return total;
    }

    public synchronized void notifyFileSaved(Path savedFile) {
        for (String shaderPath : watchedPaths) {
            if (referencesFile(shaderPath, savedFile)) {
                lastModifiedByPath.put(shaderPath, readModifiedMillis(shaderPath));
                pendingChanges.add(shaderPath);
            }
        }
    }

    public synchronized void notifyPathChanged(String shaderPath) {
        if (watchedPaths.contains(shaderPath)) {
            pendingChanges.add(shaderPath);
        }
    }

    private boolean referencesFile(String shaderPath, Path savedFile) {
        return resolveFile(shaderPath)
                .map(resolved -> sameFile(resolved, savedFile))
                .orElse(false);
    }

    private static boolean sameFile(Path first, Path second) {
        try {
            return Files.isSameFile(first, second);
        } catch (IOException unreadable) {
            return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
        }
    }

    public void poll() {
        String relativePath;
        while ((relativePath = pendingChanges.poll()) != null) {
            notifyListeners(relativePath);
        }
    }

    private void pollLoop() {
        while (!stopped) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            scanWatchedPaths();
        }
    }

    private void scanWatchedPaths() {
        Set<String> snapshot;
        synchronized (this) {
            snapshot = new HashSet<>(watchedPaths);
        }
        for (String shaderPath : snapshot) {
            long current = readModifiedMillis(shaderPath);
            Long previous = lastModifiedByPath.get(shaderPath);
            if (previous != null && current > previous) {
                lastModifiedByPath.put(shaderPath, current);
                pendingChanges.add(shaderPath);
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

    private long readModifiedMillis(String shaderPath) {
        return resolveFile(shaderPath).map(ShaderWatcher::modifiedMillis).orElse(0L);
    }

    private Optional<Path> resolveFile(String shaderPath) {
        Path candidate = Path.of(shaderPath);
        if (candidate.isAbsolute()) {
            return Optional.of(candidate);
        }
        return filesystemRoot.map(root -> root.resolve(candidate));
    }

    private static long modifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }
}
