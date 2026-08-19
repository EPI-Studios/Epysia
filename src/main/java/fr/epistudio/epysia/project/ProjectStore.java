package fr.epistudio.epysia.project;

import fr.epistudio.epysia.input.action.InputAction;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.input.action.InputActionsJsonCodec;
import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.render.GraphicsApi;
import fr.epistudio.epysia.scene.serialization.JsonWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProjectStore {
    private List<InputAction> pendingInputActions;

    private static final Set<String> MARKER_KEYS =
            Set.of("name", "engineVersion", "layerNames", "collisionMatrix", "quality", "inputActions",
                    "network", "steam", "render", "release");

    public static final String CURRENT_ENGINE_VERSION = "0.1";
    private static final String RECENTS_FILENAME = "recents.json";
    private static final String PINNED_KEY = "pinned";
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
        saveRecents(projects, loadPinnedPaths());
    }

    public void removeRecent(Path rootDirectory) throws IOException {
        List<Project> recents = loadRecents();
        recents.removeIf(project -> project.rootDirectory().equals(rootDirectory));
        Set<String> pinned = new LinkedHashSet<>(loadPinnedPaths());
        pinned.remove(absolutePathOf(rootDirectory));
        saveRecents(recents, pinned);
    }

    public Set<String> loadPinnedPaths() {
        if (!Files.isRegularFile(recentsFile)) {
            return Set.of();
        }
        try {
            Object raw = new JsonReader(Files.readString(recentsFile)).readRootObject().get(PINNED_KEY);
            if (!(raw instanceof List<?> entries)) {
                return Set.of();
            }
            Set<String> pinned = new LinkedHashSet<>();
            for (Object entry : entries) {
                if (entry instanceof String path) {
                    pinned.add(path);
                }
            }
            return pinned;
        } catch (IOException unreadable) {
            return Set.of();
        }
    }

    public boolean isPinned(Path rootDirectory) {
        return loadPinnedPaths().contains(absolutePathOf(rootDirectory));
    }

    public void setPinned(Path rootDirectory, boolean pinned) throws IOException {
        Set<String> paths = new LinkedHashSet<>(loadPinnedPaths());
        if (pinned) {
            paths.add(absolutePathOf(rootDirectory));
        } else {
            paths.remove(absolutePathOf(rootDirectory));
        }
        saveRecents(loadRecents(), paths);
    }

    private void saveRecents(List<Project> projects, Set<String> pinned) throws IOException {
        Files.createDirectories(recentsFile.getParent());
        JsonWriter writer = new JsonWriter().beginObject().key("projects").beginArray();
        for (Project project : projects) {
            writer.beginObject()
                    .key("path").valueString(absolutePathOf(project.rootDirectory()))
                    .key("lastOpenedMillis").valueNumber(project.lastOpenedMillis())
                    .endObject();
        }
        writer.endArray().key(PINNED_KEY).beginArray();
        for (String path : pinned) {
            writer.valueString(path);
        }
        writer.endArray().endObject();
        Files.writeString(recentsFile, writer.toString());
    }

    private static String absolutePathOf(Path directory) {
        return directory.toAbsolutePath().toString();
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
        Files.createDirectories(project.librariesDirectory());
        return project;
    }

    private void writeMarker(Project project) throws IOException {
        writeMarker(project, EditorSettings.defaults());
    }

    private void writeMarker(Project project, EditorSettings settings) throws IOException {
        writeMarker(project, settings, readQuality(project));
    }

    private void writeMarker(Project project, EditorSettings settings, ProjectQuality quality) throws IOException {
        JsonWriter writer = new JsonWriter().beginObject()
                .key("name").valueString(project.name())
                .key("engineVersion").valueString(project.engineVersion());
        writeSettingsKeys(writer, settings);
        writeQualityKeys(writer, quality);
        writeNetworkKeys(writer, readNetwork(project));
        writeSteamKeys(writer, readSteam(project));
        writeRenderKeys(writer, readRender(project));
        writeReleaseKeys(writer, readRelease(project));
        writer.key("inputActions");
        new InputActionsJsonCodec().write(writer,
                pendingInputActions == null ? readInputActions(project) : pendingInputActions);
        writeForeignKeys(writer, readMarkerRoot(project));
        writer.endObject();
        Files.writeString(project.markerFile(), writer.toString());
    }

    private Map<String, Object> readMarkerRoot(Project project) {
        Path marker = project.markerFile();
        if (!Files.isRegularFile(marker)) {
            return Map.of();
        }
        try {
            return new JsonReader(Files.readString(marker)).readRootObject();
        } catch (IOException | RuntimeException exception) {
            return Map.of();
        }
    }

    private void writeForeignKeys(JsonWriter writer, Map<String, Object> root) {
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            if (MARKER_KEYS.contains(entry.getKey())) {
                continue;
            }
            writer.key(entry.getKey());
            writeValue(writer, entry.getValue());
        }
    }

    private void writeValue(JsonWriter writer, Object value) {
        switch (value) {
            case null -> writer.valueNull();
            case String text -> writer.valueString(text);
            case Boolean flag -> writer.valueBoolean(flag);
            case Number number -> writer.valueNumber(number.doubleValue() == Math.rint(number.doubleValue())
                    ? number.longValue() : number.floatValue());
            case List<?> items -> {
                writer.beginArray();
                for (Object item : items) {
                    writeValue(writer, item);
                }
                writer.endArray();
            }
            case Map<?, ?> members -> {
                writer.beginObject();
                for (Map.Entry<?, ?> member : members.entrySet()) {
                    writer.key(String.valueOf(member.getKey()));
                    writeValue(writer, member.getValue());
                }
                writer.endObject();
            }
            default -> writer.valueString(String.valueOf(value));
        }
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

    private void writeQualityKeys(JsonWriter writer, ProjectQuality quality) {
        writer.key("quality").beginObject()
                .key("gravity").beginArray()
                .valueNumber(quality.gravityX()).valueNumber(quality.gravityY()).valueNumber(quality.gravityZ())
                .endArray()
                .key("fixedTimestepHertz").valueNumber(quality.fixedTimestepHertz())
                .key("shadowMapSize").valueNumber(quality.shadowMapSize())
                .key("cascadeCount").valueNumber(quality.cascadeCount())
                .key("windowTitle").valueString(quality.windowTitle())
                .key("windowWidth").valueNumber(quality.windowWidth())
                .key("windowHeight").valueNumber(quality.windowHeight())
                .key("verticalSync").valueBoolean(quality.verticalSync())
                .key("maximumFrameRate").valueNumber(quality.maximumFrameRate())
                .key("nearestTextureFilter").valueBoolean(quality.nearestTextureFilter())
                .key("depthPrepass").valueBoolean(quality.depthPrepass())
                .key("gpuCulling").valueBoolean(quality.renderTuning().gpuCulling())
                .key("sceneIndex").valueBoolean(quality.renderTuning().sceneIndex())
                .key("multiDraw").valueBoolean(quality.renderTuning().multiDraw())
                .key("instancing").valueBoolean(quality.renderTuning().instancing())
                .key("pipelineMemo").valueBoolean(quality.renderTuning().pipelineMemo())
                .key("gpuCullMinimumInstances").valueNumber(quality.renderTuning().gpuCullMinimumInstances())
                .key("cachedTransformLookup").valueBoolean(quality.renderTuning().cachedTransformLookup())
                .key("sharedMaterialDigest").valueBoolean(quality.renderTuning().sharedMaterialDigest())
                .key("skinOnce").valueBoolean(quality.renderTuning().skinOnce())
                .key("animationCulling").valueBoolean(quality.renderTuning().animationCulling())
                .key("animationFullRateDistance").valueNumber(quality.renderTuning().animationFullRateDistance())
                .key("frontToBackOpaque").valueBoolean(quality.renderTuning().frontToBackOpaque())
                .key("shadowLayerReuse").valueBoolean(quality.renderTuning().shadowLayerReuse())
                .key("ringInstanceBuffers").valueBoolean(quality.renderTuning().ringInstanceBuffers())
                .key("ringObjectUniforms").valueBoolean(quality.renderTuning().ringObjectUniforms())
                .key("parallelAnimation").valueBoolean(quality.renderTuning().parallelAnimation())
                .key("shadowFilterSamples").valueNumber(quality.shadowFilterSamples())
                .key("filteredCascades").valueNumber(quality.filteredCascades())
                .key("shadowDepthSteps").valueNumber(quality.shadowDepthSteps())
                .endObject();
    }

    public List<InputAction> readInputActions(Project project) {
        Object raw = readMarkerRoot(project).get("inputActions");
        return raw instanceof List<?> entries
                ? new InputActionsJsonCodec().read(entries)
                : InputActions.defaultActions();
    }

    public void writeInputActions(Project project, List<InputAction> actions) throws IOException {
        pendingInputActions = List.copyOf(actions);
        try {
            writeMarker(project, readSettings(project), readQuality(project));
        } finally {
            pendingInputActions = null;
        }
    }

    public ProjectQuality readQuality(Project project) {
        Map<String, Object> root = readMarkerRoot(project);
        if (!(root.get("quality") instanceof Map<?, ?> quality)) {
            return ProjectQuality.defaults();
        }
        ProjectQuality defaults = ProjectQuality.defaults();
        float x = defaults.gravityX();
        float y = defaults.gravityY();
        float z = defaults.gravityZ();
        if (quality.get("gravity") instanceof List<?> gravity && gravity.size() >= 3) {
            x = numberAt(gravity, 0, x);
            y = numberAt(gravity, 1, y);
            z = numberAt(gravity, 2, z);
        }
        return new ProjectQuality(x, y, z,
                intMember(quality, "fixedTimestepHertz", defaults.fixedTimestepHertz()),
                intMember(quality, "shadowMapSize", defaults.shadowMapSize()),
                intMember(quality, "cascadeCount", defaults.cascadeCount()),
                quality.get("windowTitle") instanceof String title ? title : defaults.windowTitle(),
                intMember(quality, "windowWidth", defaults.windowWidth()),
                intMember(quality, "windowHeight", defaults.windowHeight()),
                quality.get("verticalSync") instanceof Boolean sync ? sync : defaults.verticalSync(),
                intMember(quality, "maximumFrameRate", defaults.maximumFrameRate()),
                quality.get("nearestTextureFilter") instanceof Boolean nearest
                        ? nearest : defaults.nearestTextureFilter(),
                quality.get("depthPrepass") instanceof Boolean prepass
                        ? prepass : defaults.depthPrepass(),
                intMember(quality, "shadowFilterSamples", defaults.shadowFilterSamples()),
                intMember(quality, "filteredCascades", defaults.filteredCascades()),
                intMember(quality, "shadowDepthSteps", defaults.shadowDepthSteps()),
                readRenderTuning(quality, defaults.renderTuning())).clamped();
    }

    private static RenderTuning readRenderTuning(Map<?, ?> quality, RenderTuning defaults) {
        return new RenderTuning(
                booleanMember(quality, "gpuCulling", defaults.gpuCulling()),
                intMember(quality, "gpuCullMinimumInstances", defaults.gpuCullMinimumInstances()),
                booleanMember(quality, "sceneIndex", defaults.sceneIndex()),
                booleanMember(quality, "multiDraw", defaults.multiDraw()),
                booleanMember(quality, "instancing", defaults.instancing()),
                booleanMember(quality, "pipelineMemo", defaults.pipelineMemo()),
                booleanMember(quality, "cachedTransformLookup", defaults.cachedTransformLookup()),
                booleanMember(quality, "sharedMaterialDigest", defaults.sharedMaterialDigest()),
                booleanMember(quality, "skinOnce", defaults.skinOnce()),
                booleanMember(quality, "animationCulling", defaults.animationCulling()),
                floatMember(quality, "animationFullRateDistance", defaults.animationFullRateDistance()),
                booleanMember(quality, "frontToBackOpaque", defaults.frontToBackOpaque()),
                booleanMember(quality, "shadowLayerReuse", defaults.shadowLayerReuse()),
                booleanMember(quality, "ringInstanceBuffers", defaults.ringInstanceBuffers()),
                booleanMember(quality, "ringObjectUniforms", defaults.ringObjectUniforms()),
                booleanMember(quality, "parallelAnimation", defaults.parallelAnimation()));
    }

    private static float floatMember(Map<?, ?> source, String key, float fallback) {
        return source.get(key) instanceof Number value ? value.floatValue() : fallback;
    }

    private static boolean booleanMember(Map<?, ?> source, String key, boolean fallback) {
        return source.get(key) instanceof Boolean value ? value : fallback;
    }

    public void writeQuality(Project project, ProjectQuality quality) throws IOException {
        writeMarker(project, readSettings(project), quality.clamped());
    }

    public NetworkSettings readNetwork(Project project) {
        if (pendingNetwork != null) {
            return pendingNetwork;
        }
        Map<String, Object> root = readMarkerRoot(project);
        if (!(root.get("network") instanceof Map<?, ?> network)) {
            return NetworkSettings.defaults();
        }
        NetworkSettings defaults = NetworkSettings.defaults();
        return new NetworkSettings(
                intMember(network, "port", defaults.port()),
                intMember(network, "maximumPeers", defaults.maximumPeers()),
                intMember(network, "networkTickRate", defaults.networkTickRate()),
                intMember(network, "snapshotRate", defaults.snapshotRate()),
                intMember(network, "interpolationDelayTicks", defaults.interpolationDelayTicks()),
                floatMember(network, "timeoutSeconds", defaults.timeoutSeconds()),
                network.get("joinSecret") instanceof String secret ? secret : defaults.joinSecret())
                .clamped();
    }

    public void writeNetwork(Project project, NetworkSettings network) throws IOException {
        pendingNetwork = network.clamped();
        try {
            writeMarker(project, readSettings(project), readQuality(project));
        } finally {
            pendingNetwork = null;
        }
    }

    public SteamSettings readSteam(Project project) {
        if (pendingSteam != null) {
            return pendingSteam;
        }
        Map<String, Object> root = readMarkerRoot(project);
        if (!(root.get("steam") instanceof Map<?, ?> steam)) {
            return SteamSettings.defaults();
        }
        SteamSettings defaults = SteamSettings.defaults();
        return new SteamSettings(
                intMember(steam, "appId", defaults.appId()),
                steam.get("required") instanceof Boolean required ? required : defaults.required(),
                steam.get("relayAllowed") instanceof Boolean relay ? relay : defaults.relayAllowed())
                .clamped();
    }

    public void writeSteam(Project project, SteamSettings steam) throws IOException {
        pendingSteam = steam.clamped();
        try {
            writeMarker(project, readSettings(project), readQuality(project));
        } finally {
            pendingSteam = null;
        }
    }

    public RenderSettings readRender(Project project) {
        if (pendingRender != null) {
            return pendingRender;
        }
        Map<String, Object> root = readMarkerRoot(project);
        if (!(root.get("render") instanceof Map<?, ?> render)) {
            return RenderSettings.defaults();
        }
        String api = render.get("api") instanceof String text ? text : "";
        return new RenderSettings(GraphicsApi.parse(api, RenderSettings.defaults().api()));
    }

    public void writeRender(Project project, RenderSettings render) throws IOException {
        pendingRender = render.clamped();
        try {
            writeMarker(project, readSettings(project), readQuality(project));
        } finally {
            pendingRender = null;
        }
    }

    public ReleaseSettings readRelease(Project project) {
        if (pendingRelease != null) {
            return pendingRelease;
        }
        Map<String, Object> root = readMarkerRoot(project);
        if (!(root.get("release") instanceof Map<?, ?> release)) {
            return ReleaseSettings.defaults();
        }
        return new ReleaseSettings(release.get("version") instanceof String version
                ? version
                : ReleaseSettings.DEFAULT_VERSION).sanitized();
    }

    public void writeRelease(Project project, ReleaseSettings release) throws IOException {
        pendingRelease = release.sanitized();
        try {
            writeMarker(project, readSettings(project), readQuality(project));
        } finally {
            pendingRelease = null;
        }
    }

    private void writeReleaseKeys(JsonWriter writer, ReleaseSettings release) {
        writer.key("release").beginObject()
                .key("version").valueString(release.version())
                .endObject();
    }

    private void writeRenderKeys(JsonWriter writer, RenderSettings render) {
        writer.key("render").beginObject()
                .key("api").valueString(render.api().id())
                .endObject();
    }

    private void writeSteamKeys(JsonWriter writer, SteamSettings steam) {
        writer.key("steam").beginObject()
                .key("appId").valueNumber(steam.appId())
                .key("required").valueBoolean(steam.required())
                .key("relayAllowed").valueBoolean(steam.relayAllowed())
                .endObject();
    }

    private void writeNetworkKeys(JsonWriter writer, NetworkSettings network) {
        writer.key("network").beginObject()
                .key("port").valueNumber(network.port())
                .key("maximumPeers").valueNumber(network.maximumPeers())
                .key("networkTickRate").valueNumber(network.networkTickRate())
                .key("snapshotRate").valueNumber(network.snapshotRate())
                .key("interpolationDelayTicks").valueNumber(network.interpolationDelayTicks())
                .key("timeoutSeconds").valueNumber(network.timeoutSeconds())
                .key("joinSecret").valueString(network.joinSecret())
                .endObject();
    }


    private NetworkSettings pendingNetwork;
    private SteamSettings pendingSteam;
    private RenderSettings pendingRender;
    private ReleaseSettings pendingRelease;

    private static float numberAt(List<?> values, int index, float fallback) {
        return values.get(index) instanceof Number number ? number.floatValue() : fallback;
    }

    private static int intMember(Map<?, ?> members, String key, int fallback) {
        return members.get(key) instanceof Number number ? number.intValue() : fallback;
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
