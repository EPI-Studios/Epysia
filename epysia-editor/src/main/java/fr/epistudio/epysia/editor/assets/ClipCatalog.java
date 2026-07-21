package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.assets.epyclip.EpyClipReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class ClipCatalog {

    private static final String CLIP_EXTENSION = ".epyclip";
    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of("build", ".gradle", ".git", ".idea", "target", ".worktrees", ".epysia");

    public record ClipEntry(Path path, String name, long skeletonChecksum) {
    }

    private record CachedClip(long modifiedMillis, ClipEntry entry) {
    }

    private final Path rootDirectory;
    private final Map<Path, CachedClip> cache = new HashMap<>();

    public ClipCatalog(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public List<ClipEntry> all() {
        List<ClipEntry> entries = new ArrayList<>();
        for (Path path : listClipFiles()) {
            readEntry(path).ifPresent(entries::add);
        }
        entries.sort(Comparator.comparing(ClipEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public List<ClipEntry> matching(long skeletonChecksum) {
        return all().stream()
                .filter(entry -> entry.skeletonChecksum() == skeletonChecksum)
                .toList();
    }

    private List<Path> listClipFiles() {
        try (Stream<Path> walk = Files.walk(rootDirectory)) {
            return walk.filter(Files::isRegularFile)
                    .filter(ClipCatalog::isClip)
                    .filter(path -> !isExcluded(path))
                    .toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    private static boolean isClip(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(CLIP_EXTENSION);
    }

    private boolean isExcluded(Path path) {
        for (Path segment : rootDirectory.relativize(path)) {
            if (EXCLUDED_DIRECTORIES.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Optional<ClipEntry> readEntry(Path path) {
        long modified = modifiedMillisOf(path);
        CachedClip cached = cache.get(path);
        if (cached != null && cached.modifiedMillis() == modified) {
            return Optional.of(cached.entry());
        }
        return refreshEntry(path, modified);
    }

    private Optional<ClipEntry> refreshEntry(Path path, long modified) {
        try {
            Clip clip = EpyClipReader.readFile(path);
            ClipEntry entry = new ClipEntry(path, displayName(clip, path), clip.skeletonChecksum());
            cache.put(path, new CachedClip(modified, entry));
            return Optional.of(entry);
        } catch (RuntimeException unreadable) {
            cache.remove(path);
            return Optional.empty();
        }
    }

    private static String displayName(Clip clip, Path path) {
        return clip.name().isEmpty() ? stem(path) : clip.name();
    }

    private static String stem(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static long modifiedMillisOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }
}
