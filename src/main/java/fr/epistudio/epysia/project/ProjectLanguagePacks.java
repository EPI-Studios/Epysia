package fr.epistudio.epysia.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public record ProjectLanguagePacks(Map<String, String> versionsById) {

    private static final String COMMENT_PREFIX = "#";
    private static final String SEPARATOR = ":";
    private static final String FILE_HEADER =
            "# One language pack per line: identifier:version";

    public ProjectLanguagePacks {
        versionsById = Map.copyOf(versionsById);
    }

    public static ProjectLanguagePacks none() {
        return new ProjectLanguagePacks(Map.of());
    }

    public static ProjectLanguagePacks read(Path manifestFile) {
        if (manifestFile == null || !Files.isRegularFile(manifestFile)) {
            return none();
        }
        try {
            return new ProjectLanguagePacks(parse(Files.readAllLines(manifestFile)));
        } catch (IOException exception) {
            return none();
        }
    }

    private static Map<String, String> parse(Iterable<String> lines) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX)) {
                continue;
            }
            int separator = trimmed.indexOf(SEPARATOR);
            if (separator > 0 && separator < trimmed.length() - 1) {
                parsed.put(trimmed.substring(0, separator), trimmed.substring(separator + 1));
            }
        }
        return parsed;
    }

    public void writeTo(Path manifestFile) throws IOException {
        Files.createDirectories(manifestFile.getParent());
        StringBuilder builder = new StringBuilder(FILE_HEADER).append(System.lineSeparator());
        for (Map.Entry<String, String> pinned : new TreeMap<>(versionsById).entrySet()) {
            builder.append(pinned.getKey()).append(SEPARATOR).append(pinned.getValue())
                    .append(System.lineSeparator());
        }
        Files.writeString(manifestFile, builder.toString());
    }

    public boolean isEmpty() {
        return versionsById.isEmpty();
    }

    public Optional<String> versionOf(String identifier) {
        return Optional.ofNullable(versionsById.get(identifier));
    }

    public ProjectLanguagePacks with(String identifier, String version) {
        Map<String, String> updated = new LinkedHashMap<>(versionsById);
        updated.put(identifier, version);
        return new ProjectLanguagePacks(updated);
    }

    public ProjectLanguagePacks without(String identifier) {
        Map<String, String> updated = new LinkedHashMap<>(versionsById);
        updated.remove(identifier);
        return new ProjectLanguagePacks(updated);
    }
}
