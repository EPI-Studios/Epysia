package fr.epistudio.epysia.save;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class SaveGames {

    public static final String DIRECTORY_NAME = "saves";
    public static final String EXTENSION = ".save.json";

    private static final String TEMPORARY_EXTENSION = ".tmp";

    private final Path directory;

    public SaveGames(Path directory) {
        this.directory = directory;
    }

    public static SaveGames beside(Path gameDirectory) {
        return new SaveGames(gameDirectory.resolve(DIRECTORY_NAME));
    }

    public Path directory() {
        return directory;
    }

    public boolean exists(String slot) {
        return Files.isRegularFile(fileFor(slot));
    }

    public Optional<String> read(String slot) {
        Path file = fileFor(slot);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    public void write(String slot, String contents) throws IOException {
        Files.createDirectories(directory);
        Path file = fileFor(slot);
        Path temporary = file.resolveSibling(file.getFileName() + TEMPORARY_EXTENSION);
        Files.writeString(temporary, contents);
        moveIntoPlace(temporary, file);
    }

    public boolean delete(String slot) {
        try {
            return Files.deleteIfExists(fileFor(slot));
        } catch (IOException undeletable) {
            return false;
        }
    }

    public List<String> slots() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            List<String> names = new ArrayList<>();
            entries.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .forEach(name -> names.add(name.substring(0, name.length() - EXTENSION.length())));
            names.sort(String::compareTo);
            return List.copyOf(names);
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    public long modifiedAtMillis(String slot) {
        try {
            return Files.getLastModifiedTime(fileFor(slot)).toMillis();
        } catch (IOException unknown) {
            return 0L;
        }
    }

    private static void moveIntoPlace(Path temporary, Path file) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path fileFor(String slot) {
        return directory.resolve(sanitize(slot) + EXTENSION);
    }

    private static String sanitize(String slot) {
        String trimmed = slot == null ? "" : slot.trim().toLowerCase(Locale.ROOT);
        String safe = trimmed.replaceAll("[^a-z0-9._-]", "-");
        return safe.isEmpty() ? "slot" : safe;
    }
}
