package fr.epistudio.epysia.editor.assets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class AssetChangeWatcher {

    private static final float POLL_SECONDS = 1.5f;
    private static final int MAXIMUM_DEPTH = 12;

    private final Path root;
    private final Map<Path, Long> modifiedByFile = new HashMap<>();
    private float sinceLastPoll;
    private boolean seeded;

    public AssetChangeWatcher(Path root) {
        this.root = root;
    }

    public List<Path> poll(float deltaTimeSeconds) {
        sinceLastPoll += deltaTimeSeconds;
        if (sinceLastPoll < POLL_SECONDS) {
            return List.of();
        }
        sinceLastPoll = 0.0f;
        List<Path> changed = scan();
        if (!seeded) {
            seeded = true;
            return List.of();
        }
        return changed;
    }

    private List<Path> scan() {
        List<Path> changed = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, MAXIMUM_DEPTH)) {
            walk.filter(Files::isRegularFile)
                    .filter(AssetFileNames::isWatchable)
                    .forEach(file -> recordIfChanged(file, changed));
        } catch (IOException unreadable) {
            return List.of();
        }
        return changed;
    }

    private void recordIfChanged(Path file, List<Path> changed) {
        long modified = modifiedMillis(file);
        Long previous = modifiedByFile.put(file, modified);
        if (previous != null && previous != modified) {
            changed.add(file);
        }
    }

    private static long modifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }
}
