package fr.epistudio.epysia.project;

import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProjectStore {

    public static final String CURRENT_ENGINE_VERSION = "0.1";
    private static final String RECENTS_FILENAME = "recents.json";
    private static final String EPYSIA_DIRECTORY_NAME = ".epysia";

    private final Path recentsFile;

    public ProjectStore() {
        this(defaultRecentsFile());
    }

    public ProjectStore(Path recentsFile) {
        this.recentsFile = recentsFile;
    }

    public List<Project> loadRecents() {
        if (!Files.isRegularFile(recentsFile)) {
            return new ArrayList<>();
        }
        try {
            String body = Files.readString(recentsFile);
            Map<String, Object> root = new JsonReader(body).readRootObject();
            return readRecentsArray(root);
        } catch (IOException exception) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Project> readRecentsArray(Map<String, Object> root) {
        Object raw = root.get("projects");
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Project> result = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Map<?, ?> map) {
                readRecentEntry((Map<String, Object>) map).ifPresent(result::add);
            }
        }
        result.sort(Comparator.comparingLong(Project::lastOpenedMillis).reversed());
        return result;
    }

    private Optional<Project> readRecentEntry(Map<String, Object> entry) {
        Object rawPath = entry.get("path");
        if (!(rawPath instanceof String pathString)) {
            return Optional.empty();
        }
        Path rootDirectory = Path.of(pathString);
        if (!Files.isDirectory(rootDirectory)) {
            return Optional.empty();
        }
        long lastOpenedMillis = entry.get("lastOpenedMillis") instanceof Number number ? number.longValue() : 0L;
        return readProjectFromDisk(rootDirectory, lastOpenedMillis);
    }

    public Optional<Project> readProjectFromDisk(Path rootDirectory, long fallbackLastOpenedMillis) {
        Path marker = rootDirectory.resolve(Project.MARKER_FILENAME);
        if (!Files.isRegularFile(marker)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> root = new JsonReader(Files.readString(marker)).readRootObject();
            String name = root.get("name") instanceof String value ? value : rootDirectory.getFileName().toString();
            String engineVersion = root.get("engineVersion") instanceof String value ? value : CURRENT_ENGINE_VERSION;
            return Optional.of(new Project(name, rootDirectory, engineVersion, fallbackLastOpenedMillis));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public void clearRecents() throws IOException {
        saveRecents(List.of());
    }

    public void saveRecents(List<Project> projects) throws IOException {
        Files.createDirectories(recentsFile.getParent());
        JsonWriter writer = new JsonWriter().beginObject().key("projects").beginArray();
        for (Project project : projects) {
            writer.beginObject()
                    .key("path").valueString(project.rootDirectory().toAbsolutePath().toString())
                    .key("lastOpenedMillis").valueNumber(project.lastOpenedMillis())
                    .endObject();
        }
        writer.endArray().endObject();
        Files.writeString(recentsFile, writer.toString());
    }

    public void recordOpened(Project project) throws IOException {
        List<Project> recents = loadRecents();
        recents.removeIf(existing -> existing.rootDirectory().equals(project.rootDirectory()));
        recents.add(0, project.withLastOpenedNow());
        saveRecents(recents);
    }

    public Project createProject(String name, Path rootDirectory) throws IOException {
        if (Files.exists(rootDirectory) && !isDirectoryEmpty(rootDirectory)) {
            throw new IOException("Target directory is not empty: " + rootDirectory);
        }
        Files.createDirectories(rootDirectory);
        Project project = new Project(name, rootDirectory, CURRENT_ENGINE_VERSION, System.currentTimeMillis());
        writeMarker(project);
        Files.createDirectories(project.scenesDirectory());
        Files.createDirectories(project.scriptsDirectory());
        return project;
    }

    private void writeMarker(Project project) throws IOException {
        writeMarker(project, EditorSettings.defaults());
    }

    private void writeMarker(Project project, EditorSettings settings) throws IOException {
        JsonWriter writer = new JsonWriter().beginObject()
                .key("name").valueString(project.name())
                .key("engineVersion").valueString(project.engineVersion());
        writeSettingsKeys(writer, settings);
        writer.endObject();
        Files.writeString(project.markerFile(), writer.toString());
    }

    private void writeSettingsKeys(JsonWriter writer, EditorSettings settings) {
        writer.key("layerNames").beginArray();
        for (String name : settings.layerNames()) {
            writer.valueString(name);
        }
        writer.endArray();
        writer.key("collisionMatrix").beginArray();
        for (int row : settings.collisionMatrix()) {
            writer.valueNumber(row);
        }
        writer.endArray();
    }

    public EditorSettings readSettings(Project project) {
        Path marker = project.markerFile();
        if (!Files.isRegularFile(marker)) {
            return EditorSettings.defaults();
        }
        try {
            Map<String, Object> root = new JsonReader(Files.readString(marker)).readRootObject();
            return parseSettings(root);
        } catch (IOException | RuntimeException exception) {
            return EditorSettings.defaults();
        }
    }

    private EditorSettings parseSettings(Map<String, Object> root) {
        EditorSettings defaults = EditorSettings.defaults();
        List<String> names = new ArrayList<>(defaults.layerNames());
        if (root.get("layerNames") instanceof List<?> rawNames) {
            for (int i = 0; i < EditorSettings.LAYER_COUNT && i < rawNames.size(); i++) {
                if (rawNames.get(i) instanceof String name) {
                    names.set(i, name);
                }
            }
        }
        int[] matrix = defaults.collisionMatrix();
        if (root.get("collisionMatrix") instanceof List<?> rawMatrix) {
            for (int i = 0; i < EditorSettings.LAYER_COUNT && i < rawMatrix.size(); i++) {
                if (rawMatrix.get(i) instanceof Number number) {
                    matrix[i] = number.intValue();
                }
            }
        }
        return new EditorSettings(names, matrix);
    }

    public void writeSettings(Project project, EditorSettings settings) throws IOException {
        Project resolved = readProjectFromDisk(project.rootDirectory(), project.lastOpenedMillis())
                .orElse(project);
        writeMarker(resolved, settings);
    }

    private boolean isDirectoryEmpty(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.findAny().isEmpty();
        }
    }

    private static Path defaultRecentsFile() {
        return Path.of(System.getProperty("user.home"), EPYSIA_DIRECTORY_NAME, RECENTS_FILENAME);
    }

    public Path recentsFile() {
        return recentsFile;
    }
}
