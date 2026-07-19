package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.assets.MeshThumbnailer;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.project.Project;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiTreeNodeFlags;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

public final class AssetBrowserView {

    public static final String WINDOW_TITLE = "Assets";

    private static final float FOLDER_TREE_WIDTH = 200.0f;
    private static final float THUMBNAIL_SIZE = 28.0f;
    private static final String PREFAB_EXTENSION = ".epyprefab";
    private static final String SCENE_EXTENSION = ".epyscene";
    private static final String OBJ_EXTENSION = ".obj";
    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of("build", ".gradle", ".git", ".idea", "target", ".worktrees", ".epysia");

    private enum Kind { PRESET, MESH, TEXTURE, AUDIO, SCRIPT, PREFAB, SCENE, OTHER }

    private final Project project;
    private final Notifier notifier;
    private final IconWidgets icons;
    private final ThumbnailCache thumbnails;
    private final MeshThumbnailer meshThumbnails;
    private final Consumer<Path> onOpenScript;
    private final Consumer<Path> onBakeMesh;
    private final Consumer<Path> onInstantiatePrefab;
    private final Consumer<Path> onOpenScene;
    private final Consumer<Path> onAttachScript;
    private final NameDialog nameDialog = new NameDialog("##asset-name-dialog");
    private final ConfirmDialog deleteConfirm = new ConfirmDialog("Delete this file?", "Delete");
    private final List<Entry> entries = new ArrayList<>();
    private Path currentDirectory;
    private String selectedPath = "";
    private boolean initialized;

    public AssetBrowserView(Project project, Notifier notifier, IconWidgets icons,
                            ThumbnailCache thumbnails, MeshThumbnailer meshThumbnails,
                            Consumer<Path> onOpenScript, Consumer<Path> onBakeMesh,
                            Consumer<Path> onInstantiatePrefab, Consumer<Path> onOpenScene,
                            Consumer<Path> onAttachScript) {
        this.project = project;
        this.notifier = notifier;
        this.icons = icons;
        this.thumbnails = thumbnails;
        this.meshThumbnails = meshThumbnails;
        this.onOpenScript = onOpenScript;
        this.onBakeMesh = onBakeMesh;
        this.onInstantiatePrefab = onInstantiatePrefab;
        this.onOpenScene = onOpenScene;
        this.onAttachScript = onAttachScript;
        this.currentDirectory = project.rootDirectory();
    }

    public void refreshAssets() {
        refresh();
    }

    public void render() {
        ensureInitialized();
        if (!ImGui.begin(WINDOW_TITLE)) {
            ImGui.end();
            return;
        }
        renderHeader();
        ImGui.separator();
        renderFolderTreeColumn();
        ImGui.sameLine();
        renderEntryList();
        nameDialog.render();
        deleteConfirm.render();
        ImGui.end();
    }

    private void ensureInitialized() {
        if (!initialized) {
            refresh();
            initialized = true;
        }
    }

    private void renderHeader() {
        if (icons.iconButton("assets-new-folder", EditorIcon.FOLDER, EditorStyle.ICON_SIZE_SMALL)) {
            nameDialog.open("New folder", "NewFolder", this::createFolder);
        }
        ImGui.sameLine();
        if (icons.iconButton("assets-refresh", EditorIcon.LOAD, EditorStyle.ICON_SIZE_SMALL)) {
            refresh();
        }
        ImGui.sameLine();
        ImGui.textDisabled(project.rootDirectory().relativize(currentDirectory).toString());
    }

    private void renderFolderTreeColumn() {
        ImGui.beginChild("##asset-folders", FOLDER_TREE_WIDTH, 0.0f, true);
        renderFolderNode(project.rootDirectory(), project.name());
        ImGui.endChild();
    }

    private void renderFolderNode(Path directory, String label) {
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (directory.equals(currentDirectory)) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
        if (directory.equals(project.rootDirectory())) {
            flags |= ImGuiTreeNodeFlags.DefaultOpen;
        }
        boolean opened = ImGui.treeNodeEx(directory.toString(), flags, label);
        if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) {
            navigateTo(directory);
        }
        if (opened) {
            renderChildFolders(directory);
            ImGui.treePop();
        }
    }

    private void renderChildFolders(Path directory) {
        for (Path child : listSubdirectories(directory)) {
            renderFolderNode(child, child.getFileName().toString());
        }
    }

    private List<Path> listSubdirectories(Path directory) {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, Files::isDirectory)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                if (!name.startsWith(".") && !EXCLUDED_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT))) {
                    result.add(child);
                }
            }
        } catch (IOException error) {
            notifier.show("Could not list directory: " + error.getMessage());
        }
        result.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return result;
    }

    private void renderEntryList() {
        ImGui.beginChild("##asset-entries", 0.0f, 0.0f, true);
        for (Entry entry : new ArrayList<>(entries)) {
            renderEntryRow(entry);
        }
        ImGui.endChild();
    }

    private void renderEntryRow(Entry entry) {
        ImGui.pushID(entry.assetPath());
        drawEntryThumbnail(entry);
        ImGui.sameLine();
        boolean selected = entry.assetPath().equals(selectedPath);
        if (ImGui.selectable(entry.displayName(), selected)) {
            selectedPath = entry.assetPath();
        }
        handleEntryInteractions(entry);
        ImGui.popID();
    }

    private void handleEntryInteractions(Entry entry) {
        renderEntryDragSource(entry);
        renderEntryContextMenu(entry);
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            activateEntry(entry);
        }
    }

    private void drawEntryThumbnail(Entry entry) {
        OptionalInt texture = thumbnailFor(entry);
        if (texture.isPresent()) {
            drawThumbnailImage(entry, texture.getAsInt());
        } else {
            icons.draw(iconFor(entry.kind()), EditorStyle.ICON_SIZE_MEDIUM);
        }
    }

    private void drawThumbnailImage(Entry entry, int textureId) {
        if (entry.kind() == Kind.TEXTURE) {
            ImGui.image(textureId, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        } else {
            ImGui.image(textureId, THUMBNAIL_SIZE, THUMBNAIL_SIZE, 0.0f, 1.0f, 1.0f, 0.0f);
        }
    }

    private OptionalInt thumbnailFor(Entry entry) {
        if (entry.kind() == Kind.TEXTURE) {
            return thumbnails.get(entry.assetPath());
        }
        if (entry.kind() == Kind.MESH || entry.kind() == Kind.PRESET) {
            return meshThumbnails.get(entry.assetPath());
        }
        return OptionalInt.empty();
    }

    private void renderEntryDragSource(Entry entry) {
        String mimeType = mimeFor(entry.kind());
        if (mimeType.isEmpty() || !ImGui.beginDragDropSource()) {
            return;
        }
        ImGui.setDragDropPayload(mimeType, entry.assetPath());
        icons.drawInline(iconFor(entry.kind()), EditorStyle.ICON_SIZE_SMALL);
        ImGui.textUnformatted(entry.displayName());
        ImGui.endDragDropSource();
    }

    private void renderEntryContextMenu(Entry entry) {
        if (!ImGui.beginPopupContextItem("asset-context")) {
            return;
        }
        selectedPath = entry.assetPath();
        renderContextItems(entry);
        ImGui.endPopup();
    }

    private void renderContextItems(Entry entry) {
        Path path = Path.of(entry.assetPath());
        if (entry.kind() == Kind.PREFAB && ImGui.menuItem("Instantiate")) {
            onInstantiatePrefab.accept(path);
        }
        if (entry.kind() == Kind.SCENE && ImGui.menuItem("Open Scene")) {
            onOpenScene.accept(path);
        }
        if (entry.kind() == Kind.SCRIPT && ImGui.menuItem("Attach to selected")) {
            onAttachScript.accept(path);
        }
        if (isBakeable(entry) && ImGui.menuItem("Bake Mesh")) {
            onBakeMesh.accept(path);
        }
        if (entry.kind() != Kind.PRESET) {
            renderFileManagementItems(entry, path);
        }
    }

    private void renderFileManagementItems(Entry entry, Path path) {
        if (ImGui.menuItem("Rename")) {
            nameDialog.open("Rename " + path.getFileName(), path.getFileName().toString(),
                    newName -> renameFile(path, newName));
        }
        if (ImGui.menuItem("Delete")) {
            deleteConfirm.open(path.getFileName() + " will be permanently removed from disk.",
                    () -> deleteFile(path));
        }
        ImGui.separator();
        if (ImGui.menuItem("Show Path")) {
            notifier.show(entry.assetPath());
        }
    }

    private static boolean isBakeable(Entry entry) {
        return entry.kind() == Kind.MESH
                && entry.assetPath().toLowerCase(Locale.ROOT).endsWith(OBJ_EXTENSION);
    }

    private void activateEntry(Entry entry) {
        Path path = Path.of(entry.assetPath());
        switch (entry.kind()) {
            case SCRIPT -> onOpenScript.accept(path);
            case PREFAB -> onInstantiatePrefab.accept(path);
            case SCENE -> onOpenScene.accept(path);
            default -> {
            }
        }
    }

    private void renameFile(Path path, String newName) {
        String sanitized = sanitizeFileName(path, newName);
        if (sanitized.isEmpty()) {
            notifier.show("Invalid file name: " + newName);
            return;
        }
        try {
            Path target = path.resolveSibling(sanitized);
            Files.move(path, target);
            renamePublicClassIfScript(target, sanitized);
            refresh();
        } catch (IOException error) {
            notifier.show("Rename failed: " + error.getMessage());
        }
    }

    private String sanitizeFileName(Path path, String requested) {
        String trimmed = requested.strip();
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        String originalName = path.getFileName().toString();
        int extensionIndex = originalName.lastIndexOf('.');
        if (extensionIndex > 0 && !trimmed.contains(".")) {
            return trimmed + originalName.substring(extensionIndex);
        }
        return trimmed;
    }

    private void renamePublicClassIfScript(Path target, String fileName) throws IOException {
        if (!fileName.endsWith(".java")) {
            return;
        }
        String className = fileName.substring(0, fileName.length() - ".java".length());
        if (!javax.lang.model.SourceVersion.isName(className)) {
            return;
        }
        String source = Files.readString(target);
        String renamed = source.replaceFirst(
                "(public\\s+(?:final\\s+)?class\\s+)\\w+", "$1" + className);
        if (!renamed.equals(source)) {
            Files.writeString(target, renamed);
            notifier.show("Class renamed to " + className);
        }
    }

    public void importExternalFiles(List<Path> files) {
        int imported = 0;
        for (Path file : files) {
            if (Files.isDirectory(file)) {
                notifier.show("Folders are not importable, drop files: " + file.getFileName());
                continue;
            }
            imported += importSingleFile(file);
        }
        if (imported > 0) {
            notifier.show("Imported " + imported + " file(s) into " + currentDirectory.getFileName());
            refresh();
        }
    }

    private int importSingleFile(Path file) {
        try {
            int copied = copyIntoCurrentDirectory(file);
            String name = file.getFileName().toString().toLowerCase();
            if (name.endsWith(".obj")) {
                copied += importCompanions(file, "mtllib");
            }
            if (name.endsWith(".mtl")) {
                copied += importCompanions(file, "map_");
            }
            return copied;
        } catch (IOException error) {
            notifier.show("Import failed: " + error.getMessage());
            return 0;
        }
    }

    private int importCompanions(Path source, String directivePrefix) throws IOException {
        int copied = 0;
        for (String line : Files.readAllLines(source)) {
            String trimmed = line.strip();
            if (!trimmed.startsWith(directivePrefix)) {
                continue;
            }
            String reference = trimmed.substring(trimmed.lastIndexOf(' ') + 1);
            Path companion = source.getParent().resolve(reference);
            if (Files.isRegularFile(companion)) {
                copied += copyIntoCurrentDirectory(companion);
                if (reference.toLowerCase().endsWith(".mtl")) {
                    copied += importCompanions(companion, "map_");
                }
            }
        }
        return copied;
    }

    private int copyIntoCurrentDirectory(Path source) throws IOException {
        Path target = currentDirectory.resolve(source.getFileName().toString());
        if (source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            return 0;
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return 1;
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
            notifier.show("Deleted: " + path.getFileName());
            refresh();
        } catch (IOException error) {
            notifier.show("Delete failed: " + error.getMessage());
        }
    }

    private void createFolder(String name) {
        try {
            Files.createDirectories(currentDirectory.resolve(name));
            refresh();
        } catch (IOException error) {
            notifier.show("Create folder failed: " + error.getMessage());
        }
    }

    private void navigateTo(Path directory) {
        currentDirectory = directory;
        refresh();
    }

    private void refresh() {
        entries.clear();
        if (!Files.isDirectory(currentDirectory)) {
            currentDirectory = project.rootDirectory();
        }
        if (currentDirectory.equals(project.rootDirectory())) {
            entries.add(new Entry("preset:cube", "preset:cube", Kind.PRESET));
            entries.add(new Entry("preset:plane", "preset:plane", Kind.PRESET));
            entries.add(new Entry("preset:capsule", "preset:capsule", Kind.PRESET));
        }
        listCurrentDirectoryFiles();
    }

    private void listCurrentDirectoryFiles() {
        List<Entry> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDirectory, Files::isRegularFile)) {
            for (Path path : stream) {
                classifyFile(path, files);
            }
        } catch (IOException error) {
            notifier.show("Could not list directory: " + error.getMessage());
        }
        files.sort(Comparator.comparing(Entry::displayName));
        entries.addAll(files);
    }

    private void classifyFile(Path path, List<Entry> files) {
        String name = path.getFileName().toString();
        if (name.startsWith(".") || name.endsWith(".project") || name.equals(Project.MARKER_FILENAME)) {
            return;
        }
        files.add(new Entry(name, path.toAbsolutePath().toString(), classify(name)));
    }

    private static Kind classify(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return Kind.SCRIPT;
        }
        if (lower.endsWith(PREFAB_EXTENSION)) {
            return Kind.PREFAB;
        }
        if (lower.endsWith(SCENE_EXTENSION)) {
            return Kind.SCENE;
        }
        return classifyBinary(lower);
    }

    private static Kind classifyBinary(String lower) {
        if (lower.endsWith(".obj") || lower.endsWith(".epymesh")) {
            return Kind.MESH;
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".tga") || lower.endsWith(".bmp")) {
            return Kind.TEXTURE;
        }
        if (lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".mp3") || lower.endsWith(".flac")) {
            return Kind.AUDIO;
        }
        return Kind.OTHER;
    }

    private static String mimeFor(Kind kind) {
        return switch (kind) {
            case MESH, PRESET -> AssetMimeTypes.MESH;
            case TEXTURE -> AssetMimeTypes.TEXTURE;
            case AUDIO -> AssetMimeTypes.AUDIO;
            case PREFAB -> AssetMimeTypes.PREFAB;
            case SCENE, SCRIPT, OTHER -> AssetMimeTypes.NONE;
        };
    }

    private static EditorIcon iconFor(Kind kind) {
        return switch (kind) {
            case MESH, PRESET -> EditorIcon.MESH;
            case SCRIPT -> EditorIcon.SCRIPT;
            case PREFAB -> EditorIcon.NODE_3D;
            case SCENE -> EditorIcon.LOAD;
            case AUDIO -> EditorIcon.ANIMATION_PLAYER;
            case TEXTURE, OTHER -> EditorIcon.FILE;
        };
    }

    private record Entry(String displayName, String assetPath, Kind kind) {
    }
}
