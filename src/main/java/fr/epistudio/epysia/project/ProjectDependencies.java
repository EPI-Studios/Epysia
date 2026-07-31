package fr.epistudio.epysia.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record ProjectDependencies(List<String> coordinates) {

    private static final String COMMENT_PREFIX = "#";
    private static final String FILE_HEADER = "# One Maven coordinate per line: group:artifact:version";

    public static ProjectDependencies none() {
        return new ProjectDependencies(List.of());
    }

    public static ProjectDependencies read(Path dependenciesFile) {
        if (dependenciesFile == null || !Files.isRegularFile(dependenciesFile)) {
            return none();
        }
        try {
            return new ProjectDependencies(parse(Files.readAllLines(dependenciesFile)));
        } catch (IOException exception) {
            return none();
        }
    }

    private static List<String> parse(List<String> lines) {
        List<String> coordinates = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith(COMMENT_PREFIX)) {
                coordinates.add(trimmed);
            }
        }
        return List.copyOf(coordinates);
    }

    public void writeTo(Path dependenciesFile) throws IOException {
        Files.createDirectories(dependenciesFile.getParent());
        StringBuilder builder = new StringBuilder(FILE_HEADER).append(System.lineSeparator());
        for (String coordinate : coordinates) {
            builder.append(coordinate).append(System.lineSeparator());
        }
        Files.writeString(dependenciesFile, builder.toString());
    }

    public boolean isEmpty() {
        return coordinates.isEmpty();
    }

    public ProjectDependencies with(String coordinate) {
        if (coordinates.contains(coordinate)) {
            return this;
        }
        List<String> updated = new ArrayList<>(coordinates);
        updated.add(coordinate);
        updated.sort(String::compareTo);
        return new ProjectDependencies(List.copyOf(updated));
    }

    public ProjectDependencies without(String coordinate) {
        List<String> updated = new ArrayList<>(coordinates);
        updated.remove(coordinate);
        return new ProjectDependencies(List.copyOf(updated));
    }

    public static boolean isWellFormed(String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length < 3 || parts.length > 5) {
            return false;
        }
        for (String part : parts) {
            if (part.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
