package fr.epistudio.epysia.project;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public record ProjectLibraries(List<Path> archives) {

    public static final String ARCHIVE_SUFFIX = ".jar";

    public static ProjectLibraries none() {
        return new ProjectLibraries(List.of());
    }

    public static ProjectLibraries in(Path librariesDirectory) {
        if (librariesDirectory == null || !Files.isDirectory(librariesDirectory)) {
            return none();
        }
        try (Stream<Path> entries = Files.list(librariesDirectory)) {
            return new ProjectLibraries(entries.filter(ProjectLibraries::isArchive)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList());
        } catch (IOException exception) {
            return none();
        }
    }

    public static ProjectLibraries forProjectRoot(Path projectRoot) {
        List<Path> merged = new ArrayList<>(in(projectRoot.resolve(Project.LIBRARIES_DIRECTORY_NAME)).archives());
        merged.addAll(in(projectRoot.resolve(Project.LIBRARIES_CACHE_DIRECTORY_NAME)).archives());
        merged.addAll(inTree(projectRoot.resolve(Project.LANGUAGE_PACKS_DIRECTORY_NAME)).archives());
        return new ProjectLibraries(List.copyOf(merged));
    }

    public static ProjectLibraries inTree(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return none();
        }
        List<Path> merged = new ArrayList<>(in(root).archives());
        try (Stream<Path> entries = Files.list(root)) {
            entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(directory -> merged.addAll(in(directory).archives()));
        } catch (IOException unreadable) {
            return new ProjectLibraries(List.copyOf(merged));
        }
        return new ProjectLibraries(List.copyOf(merged));
    }

    public static boolean isArchive(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ARCHIVE_SUFFIX);
    }

    public boolean isEmpty() {
        return archives.isEmpty();
    }

    public String classpathSuffix() {
        StringBuilder builder = new StringBuilder();
        for (Path archive : archives) {
            builder.append(File.pathSeparator).append(archive.toAbsolutePath());
        }
        return builder.toString();
    }

    public List<URL> urls() {
        List<URL> urls = new ArrayList<>(archives.size());
        for (Path archive : archives) {
            toUrl(archive).ifPresent(urls::add);
        }
        return urls;
    }

    private static Optional<URL> toUrl(Path archive) {
        try {
            return Optional.of(archive.toUri().toURL());
        } catch (MalformedURLException exception) {
            return Optional.empty();
        }
    }
}
