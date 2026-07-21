package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.AssetMetaFile;
import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.editor.assets.AssetEntry;
import fr.epistudio.epysia.editor.assets.AssetQuery;
import fr.epistudio.epysia.editor.assets.AssetScanner;
import fr.epistudio.epysia.editor.assets.AssetType;
import fr.epistudio.epysia.editor.assets.BuiltinAssets;
import fr.epistudio.epysia.editor.assets.MeshThumbnailer;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.importer.AssetImportPipeline;
import fr.epistudio.epysia.editor.importer.AssetImporter;
import fr.epistudio.epysia.editor.importer.ImportOutcome;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.project.Project;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

public final class AssetBrowserView {

    public static final String WINDOW_TITLE = "Assets";

    private static final float FOLDER_TREE_WIDTH = 200.0f;
    private static final float THUMBNAIL_SIZE = 28.0f;
    private static final float ENTRY_LIST_BOTTOM_PADDING = 6.0f;
    private static final float SEARCH_FIELD_WIDTH = 180.0f;
    private static final float SMALL_COMBO_WIDTH = 110.0f;
    private static final float BREADCRUMB_SPACING = 4.0f;
    private static final String BREADCRUMB_SEPARATOR = "\u203a";
    private static final int SEARCH_CAPACITY = 128;
    private static final String PREFAB_EXTENSION = ".epyprefab";
    private static final String SCENE_EXTENSION = ".epyscene";
    private static final String OBJ_EXTENSION = ".obj";
    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of("build", ".gradle", ".git", ".idea", "target", ".worktrees", ".epysia");
    private static final long NEEDS_IMPORT_RECHECK_MILLIS = 2000L;

    
    private static final String GRAPH_EXTENSION = ".epygraph";
    private static final String GRAPH_TEMPLATE_RESOURCE = "/templates/NewGraph.epygraph";
    private static final String STATE_MACHINE_TEMPLATE_RESOURCE = "/templates/NewStateMachine.epygraph";
    private static final String SURFACE_SHADER_GRAPH_TEMPLATE_RESOURCE = "/templates/NewSurfaceShaderGraph.epygraph";
    private static final String POST_SHADER_GRAPH_TEMPLATE_RESOURCE = "/templates/NewPostShaderGraph.epygraph";

    private static final String MATERIAL_TEMPLATE_RESOURCE = "/templates/NewMaterial.epymaterial";
    private static final String MATERIAL_EXTENSION = ".epymaterial";
    private static final String MATERIALS_CATEGORY = "Materials";
    private static final String SHADERS_CATEGORY = "Shaders";
    private static final String POST_CATEGORY = "Post Processing";
    private static final String SCRIPTING_CATEGORY = "Scripting";

    private static final String VERTEX_SHADER_SUFFIX = ".vert.glsl";
    private static final String FRAGMENT_SHADER_SUFFIX = ".frag.glsl";
    private static final String SURFACE_SHADER_SUFFIX = ".surf.glsl";
    private static final String POST_EFFECT_SUFFIX = ".post.glsl";
    private static final String VERTEX_SHADER_TEMPLATE_RESOURCE = "/templates/NewShader.vert.glsl";
    private static final String FRAGMENT_SHADER_TEMPLATE_RESOURCE = "/templates/NewShader.frag.glsl";
    private static final String SURFACE_SHADER_TEMPLATE_RESOURCE = "/templates/NewSurfaceShader.surf.glsl";
    private static final String POST_EFFECT_TEMPLATE_RESOURCE = "/templates/NewPostEffect.post.glsl";


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
    private final Consumer<Path> onOpenGraph;
    private final AssetImportPipeline importPipeline;
    private final NameDialog nameDialog = new NameDialog("##asset-name-dialog");
    private final NewAssetDialog newAssetDialog;
    private final ConfirmDialog deleteConfirm = new ConfirmDialog("Delete this file?", "Delete");
    private final List<AssetEntry> entries = new ArrayList<>();
    private final Deque<Path> importQueue = new ArrayDeque<>();
    private final Set<Path> queuedSources = new HashSet<>();
    private final Map<Path, Long> lastImportCheckAt = new HashMap<>();
    private final AssetQuery query = new AssetQuery();
    private boolean showingBuiltins;
    private final ImString searchInput = new ImString(SEARCH_CAPACITY);
    private final AssetEntryGrid grid;
    private Path currentDirectory;
    private String selectedPath = "";
    private boolean initialized;

    public AssetBrowserView(Project project, Notifier notifier, IconWidgets icons,
                            ThumbnailCache thumbnails, MeshThumbnailer meshThumbnails,
                            Consumer<Path> onOpenScript, Consumer<Path> onBakeMesh,
                            Consumer<Path> onInstantiatePrefab, Consumer<Path> onOpenScene,
                            Consumer<Path> onAttachScript, Consumer<Path> onOpenGraph,
                            AssetImportPipeline importPipeline) {
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
        this.onOpenGraph = onOpenGraph;
        this.importPipeline = importPipeline;
        this.currentDirectory = project.rootDirectory();
        this.newAssetDialog = new NewAssetDialog("##new-asset-dialog", icons);
        this.grid = new AssetEntryGrid(icons, thumbnails, meshThumbnails);
    }

    public void refreshAssets() {
        refresh();
    }

    public void render() {
        ensureInitialized();
        processImportQueue();
        thumbnails.beginFrame();
        meshThumbnails.beginFrame();
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
        newAssetDialog.render();
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
        if (icons.iconButton("assets-new", EditorIcon.ADD, EditorStyle.ICON_SIZE_SMALL)) {
            newAssetDialog.setKinds(assetKinds());
            newAssetDialog.open();
        }
        ImGui.sameLine();
        if (icons.iconButton("assets-new-folder", EditorIcon.FOLDER, EditorStyle.ICON_SIZE_SMALL)) {
            nameDialog.open("New folder", "NewFolder", this::createFolder);
        }
        ImGui.sameLine();
        if (icons.iconButton("assets-refresh", EditorIcon.LOAD, EditorStyle.ICON_SIZE_SMALL)) {
            refresh();
        }
        ImGui.sameLine();
        renderBreadcrumb();
        renderSearchField();
    }

    private void renderBreadcrumb() {
        if (showingBuiltins) {
            ImGui.textDisabled(BuiltinAssets.FOLDER_LABEL);
            return;
        }
        Path root = project.rootDirectory();
        List<Path> segments = breadcrumbSegments(root);
        for (int index = 0; index < segments.size(); index++) {
            if (index > 0) {
                ImGui.sameLine(0.0f, BREADCRUMB_SPACING);
                ImGui.textDisabled(BREADCRUMB_SEPARATOR);
                ImGui.sameLine(0.0f, BREADCRUMB_SPACING);
            }
            renderBreadcrumbSegment(segments.get(index), index == segments.size() - 1);
        }
    }

    private List<Path> breadcrumbSegments(Path root) {
        List<Path> segments = new ArrayList<>();
        Path cursor = currentDirectory;
        while (cursor != null && cursor.startsWith(root)) {
            segments.add(0, cursor);
            if (cursor.equals(root)) {
                break;
            }
            cursor = cursor.getParent();
        }
        return segments;
    }

    private void renderBreadcrumbSegment(Path segment, boolean last) {
        String label = segment.equals(project.rootDirectory())
                ? project.name() : segment.getFileName().toString();
        if (last) {
            ImGui.textUnformatted(label);
            return;
        }
        if (ImGui.smallButton(label + "##crumb-" + segment)) {
            navigateTo(segment);
        }
    }

    private void renderSearchField() {
        ImGui.sameLine();
        float offset = ImGui.getContentRegionMaxX() - SEARCH_FIELD_WIDTH;
        if (offset > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(offset);
        }
        ImGui.setNextItemWidth(SEARCH_FIELD_WIDTH);
        if (ImGui.inputTextWithHint("##assets-search", "Search", searchInput)) {
            query.setSearchText(searchInput.get());
            refresh();
        }
    }

    private void renderFolderTreeColumn() {
        ImGui.beginChild("##asset-folders", FOLDER_TREE_WIDTH, 0.0f, true);
        renderFolderNode(project.rootDirectory(), project.name());
        ImGui.separator();
        renderBuiltinFolderNode();
        ImGui.endChild();
    }

    private void renderBuiltinFolderNode() {
        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.SpanAvailWidth
                | ImGuiTreeNodeFlags.NoTreePushOnOpen;
        if (showingBuiltins) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
        ImGui.treeNodeEx(BuiltinAssets.FOLDER_LABEL, flags, BuiltinAssets.FOLDER_LABEL);
        if (ImGui.isItemClicked()) {
            showingBuiltins = true;
            grid.clearSelection();
            refresh();
        }
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
        acceptAssetDrop(directory);
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
        renderViewControls();
        ImGui.separator();
        grid.render(query.apply(new ArrayList<>(entries)), this::activateEntry, this::decorateEntry);
        ImGui.dummy(0.0f, ENTRY_LIST_BOTTOM_PADDING);
        renderBrowserContextMenu();
        ImGui.endChild();
    }

    private void decorateEntry(AssetEntry entry) {
        renderEntryDragSource(entry);
        renderEntryContextMenu(entry);
    }

    private void renderViewControls() {
        boolean gridMode = grid.mode() == AssetEntryGrid.Mode.GRID;
        if (icons.toggleButton("assets-grid", EditorIcon.GRID, EditorStyle.ICON_SIZE_SMALL, gridMode)) {
            grid.setMode(gridMode ? AssetEntryGrid.Mode.LIST : AssetEntryGrid.Mode.GRID);
        }
        ImGui.sameLine();
        renderSortCombo();
        ImGui.sameLine();
        renderTypeCombo();
        ImGui.sameLine();
        ImGui.textDisabled(entries.size() + " items");
    }

    private void renderSortCombo() {
        ImGui.setNextItemWidth(SMALL_COMBO_WIDTH);
        if (ImGui.beginCombo("##assets-sort", "Sort: " + query.sortField().label())) {
            for (AssetQuery.SortField field : AssetQuery.SortField.values()) {
                if (ImGui.selectable(field.label(), field == query.sortField())) {
                    query.setSortField(field);
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(query.ascending() ? "Asc" : "Desc")) {
            query.toggleDirection();
        }
    }

    private void renderTypeCombo() {
        String label = query.typeFilter().map(AssetType::pluralLabel).orElse("All");
        ImGui.setNextItemWidth(SMALL_COMBO_WIDTH);
        if (!ImGui.beginCombo("##assets-type", "Type: " + label)) {
            return;
        }
        if (ImGui.selectable("All", query.typeFilter().isEmpty())) {
            query.setTypeFilter(null);
        }
        for (AssetType type : AssetType.values()) {
            if (ImGui.selectable(type.pluralLabel(), query.typeFilter().orElse(null) == type)) {
                query.setTypeFilter(type);
            }
        }
        ImGui.endCombo();
    }

    private void renderBrowserContextMenu() {
        int flags = ImGuiPopupFlags.MouseButtonRight | ImGuiPopupFlags.NoOpenOverItems;
        if (!ImGui.beginPopupContextWindow("##asset-browser-context", flags)) {
            return;
        }
        if (ImGui.menuItem("New Asset...")) {
            newAssetDialog.setKinds(assetKinds());
            newAssetDialog.open();
        }
        ImGui.endPopup();
    }

    private List<NewAssetDialog.AssetKind> assetKinds() {
        return List.of(
                kind("Material", MATERIALS_CATEGORY, "shareable material, assign to mesh slots",
                        EditorIcon.MESH, "MyMaterial", this::createMaterial),
                kind("Surface Shader", SHADERS_CATEGORY, "GLSL injected into the lit pipeline",
                        EditorIcon.MESH, "MySurfaceShader", this::createSurfaceShader),
                kind("Shader Graph", SHADERS_CATEGORY, "nodes, compiles to a surface shader",
                        EditorIcon.GRID, "MySurfaceGraph", this::createSurfaceShaderGraph),
                kind("Shader Pair", SHADERS_CATEGORY, "vert + frag, replaces the pipeline",
                        EditorIcon.FILE, "MyShader", this::createShaderPair),
                kind("Post Effect", POST_CATEGORY, "fullscreen pass",
                        EditorIcon.VISIBILITY_VISIBLE, "MyPostEffect", this::createPostEffect),
                kind("Post Graph", POST_CATEGORY, "nodes, compiles to a post effect",
                        EditorIcon.GRID, "MyPostGraph", this::createPostShaderGraph),
                kind("Logic Graph", SCRIPTING_CATEGORY, "attaches like a script",
                        EditorIcon.SCRIPT, "MyGraph", this::createGraph),
                kind("State Machine", SCRIPTING_CATEGORY, "states and transitions",
                        EditorIcon.ANIMATION_PLAYER, "MyStateMachine", this::createStateMachine));
    }

    private static NewAssetDialog.AssetKind kind(String label, String category, String description,
                                                 EditorIcon icon, String defaultName, Consumer<String> create) {
        return new NewAssetDialog.AssetKind(label, category, description, icon, defaultName, create);
    }

    private void createPostEffect(String requestedName) {
        createShaderAsset(requestedName, this::writePostEffect);
    }

    private void createGraph(String requestedName) {
        createGraphFromTemplate(requestedName, GRAPH_TEMPLATE_RESOURCE);
    }

    private void createSurfaceShaderGraph(String requestedName) {
        createShaderGraphFromTemplate(requestedName, SURFACE_SHADER_GRAPH_TEMPLATE_RESOURCE);
    }

    private void createPostShaderGraph(String requestedName) {
        createShaderGraphFromTemplate(requestedName, POST_SHADER_GRAPH_TEMPLATE_RESOURCE);
    }

    private void createShaderGraphFromTemplate(String requestedName, String templateResource) {
        String name = requestedName.replace("\0", "").strip();
        if (name.isEmpty() || !name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
            notifier.show("Invalid graph name: " + requestedName);
            return;
        }
        try {
            Path createdFile = writeShaderGraphTemplate(name, templateResource);
            refresh();
            onOpenGraph.accept(createdFile);
        } catch (IOException | InvalidPathException error) {
            notifier.show("Shader graph creation failed: " + error.getMessage());
        }
    }

    private Path writeShaderGraphTemplate(String name, String templateResource) throws IOException {
        Path directory = targetDirectory();
        Files.createDirectories(directory);
        Path graphFile = directory.resolve(name + GRAPH_EXTENSION);
        if (Files.exists(graphFile)) {
            throw new IOException("Graph already exists: " + name);
        }
        Files.writeString(graphFile, loadTemplate(templateResource));
        notifier.show("Shader graph created: " + name);
        return graphFile;
    }

    private void createStateMachine(String requestedName) {
        createGraphFromTemplate(requestedName, STATE_MACHINE_TEMPLATE_RESOURCE);
    }

    private void createMaterial(String requestedName) {
        String name = requestedName.replace("\0", "").strip();
        if (name.isEmpty() || !name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
            notifier.show("Invalid material name: " + requestedName);
            return;
        }
        try {
            writeMaterialTemplate(name);
            refresh();
        } catch (IOException | InvalidPathException error) {
            notifier.show("Material creation failed: " + error.getMessage());
        }
    }

    private void writeMaterialTemplate(String name) throws IOException {
        Path directory = targetDirectory();
        Files.createDirectories(directory);
        Path materialFile = directory.resolve(name + MATERIAL_EXTENSION);
        if (Files.exists(materialFile)) {
            throw new IOException("Material already exists: " + name);
        }
        Files.writeString(materialFile, loadTemplate(MATERIAL_TEMPLATE_RESOURCE));
        notifier.show("Material created: " + name);
    }

    private void createGraphFromTemplate(String requestedName, String templateResource) {
        String name = requestedName.replace("\0", "").strip();
        if (name.isEmpty() || !name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
            notifier.show("Invalid graph name: " + requestedName);
            return;
        }
        try {
            Path createdFile = writeGraphTemplate(name, templateResource);
            refresh();
            onOpenGraph.accept(createdFile);
        } catch (IOException | InvalidPathException error) {
            notifier.show("Graph creation failed: " + error.getMessage());
        }
    }

    private Path writeGraphTemplate(String name, String templateResource) throws IOException {
        Path directory = targetDirectory();
        Files.createDirectories(directory);
        Path graphFile = directory.resolve(name + GRAPH_EXTENSION);
        if (Files.exists(graphFile)) {
            throw new IOException("Graph already exists: " + name);
        }
        Files.writeString(graphFile, loadTemplate(templateResource));
        notifier.show("Graph created: " + name);
        return graphFile;
    }

    private static String loadTemplate(String templateResource) throws IOException {
        try (InputStream stream = AssetBrowserView.class.getResourceAsStream(templateResource)) {
            if (stream == null) {
                throw new IOException("Missing template resource: " + templateResource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path writePostEffect(String name) throws IOException {
        Path directory = targetDirectory();
        Files.createDirectories(directory);
        Path effectFile = directory.resolve(name + POST_EFFECT_SUFFIX);
        if (Files.exists(effectFile)) {
            throw new IOException("Shader already exists: " + name);
        }
        Files.writeString(effectFile, loadTemplate(POST_EFFECT_TEMPLATE_RESOURCE));
        notifier.show("Post effect created: " + name);
        return effectFile;
    }

    private void createSurfaceShader(String requestedName) {
        createShaderAsset(requestedName, this::writeSurfaceShader);
    }

    private void createShaderPair(String requestedName) {
        createShaderAsset(requestedName, this::writeShaderPair);
    }

    private void createShaderAsset(String requestedName, ShaderTemplateWriter templateWriter) {
        String name = requestedName.replace("\0", "").strip();
        if (name.isEmpty() || !name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
            notifier.show("Invalid shader name: " + requestedName);
            return;
        }
        try {
            Path createdFile = templateWriter.write(name);
            refresh();
            onOpenScript.accept(createdFile);
        } catch (IOException | InvalidPathException error) {
            notifier.show("Shader creation failed: " + error.getMessage());
        }
    }

    private interface ShaderTemplateWriter {
        Path write(String name) throws IOException;
    }

    private Path writeSurfaceShader(String name) throws IOException {
        Path directory = targetDirectory();
        Files.createDirectories(directory);
        Path surfaceFile = directory.resolve(name + SURFACE_SHADER_SUFFIX);
        if (Files.exists(surfaceFile)) {
            throw new IOException("Shader already exists: " + name);
        }
        Files.writeString(surfaceFile, loadTemplate(SURFACE_SHADER_TEMPLATE_RESOURCE));
        notifier.show("Surface shader created: " + name);
        return surfaceFile;
    }

    private Path writeShaderPair(String name) throws IOException {
        Path directory = targetDirectory();
        Files.createDirectories(directory);
        Path vertexFile = directory.resolve(name + VERTEX_SHADER_SUFFIX);
        Path fragmentFile = directory.resolve(name + FRAGMENT_SHADER_SUFFIX);
        if (Files.exists(vertexFile) || Files.exists(fragmentFile)) {
            throw new IOException("Shader already exists: " + name);
        }
        Files.writeString(vertexFile, loadTemplate(VERTEX_SHADER_TEMPLATE_RESOURCE));
        Files.writeString(fragmentFile, loadTemplate(FRAGMENT_SHADER_TEMPLATE_RESOURCE));
        notifier.show("Shader created: " + name);
        return fragmentFile;
    }

    private void renderEntryDragSource(AssetEntry entry) {
        String mimeType = mimeFor(entry.type());
        if (mimeType.isEmpty() || !ImGui.beginDragDropSource()) {
            return;
        }
        ImGui.setDragDropPayload(mimeType, entry.assetPath());
        icons.drawInline(iconFor(entry.type()), EditorStyle.ICON_SIZE_SMALL);
        ImGui.textUnformatted(entry.displayName());
        ImGui.endDragDropSource();
    }

    private void renderEntryContextMenu(AssetEntry entry) {
        if (!ImGui.beginPopupContextItem("asset-context")) {
            return;
        }
        selectedPath = entry.assetPath();
        renderContextItems(entry);
        ImGui.endPopup();
    }

    private void renderContextItems(AssetEntry entry) {
        Path path = Path.of(entry.assetPath());
        if (entry.type() == AssetType.PREFAB && ImGui.menuItem("Instantiate")) {
            onInstantiatePrefab.accept(path);
        }
        if (entry.type() == AssetType.SCENE && ImGui.menuItem("Open Scene")) {
            onOpenScene.accept(path);
        }
        if (entry.type() == AssetType.SCRIPT && ImGui.menuItem("Attach to selected")) {
            onAttachScript.accept(path);
        }
        if (entry.type() == AssetType.GRAPH && ImGui.menuItem("Open in Graph Editor")) {
            onOpenGraph.accept(path);
        }
        if (isBakeable(entry) && ImGui.menuItem("Bake Mesh")) {
            onBakeMesh.accept(path);
        }
        if (isImportSource(path) && ImGui.menuItem("Reimport")) {
            reimport(path);
        }
        if (entry.type() != AssetType.PRESET) {
            renderFileManagementItems(entry, path);
        }
    }

    private void renderFileManagementItems(AssetEntry entry, Path path) {
        if (ImGui.menuItem("Rename")) {
            nameDialog.open("Rename " + path.getFileName(), path.getFileName().toString(),
                    newName -> renameFile(path, newName));
        }
        if (ImGui.menuItem("Duplicate")) {
            duplicateFile(path);
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

    private static boolean isBakeable(AssetEntry entry) {
        return entry.type() == AssetType.MESH
                && entry.assetPath().toLowerCase(Locale.ROOT).endsWith(OBJ_EXTENSION);
    }

    private boolean isImportSource(Path path) {
        return importPipeline.importerFor(path).isPresent();
    }

    private void reimport(Path source) {
        Optional<String> displayName = importPipeline.importerFor(source).map(AssetImporter::displayName);
        Optional<ImportOutcome> outcome = importPipeline.reimport(source);
        if (outcome.isEmpty()) {
            notifier.show("Reimport failed: " + source.getFileName());
            return;
        }
        reportImport("Reimported", source, displayName, outcome.get());
        refresh();
    }

    private void importAndReport(Path source) {
        Optional<String> displayName = importPipeline.importerFor(source).map(AssetImporter::displayName);
        Optional<ImportOutcome> outcome = importPipeline.ensureImported(source);
        if (outcome.isEmpty()) {
            notifier.show("Import failed: " + source.getFileName());
            return;
        }
        reportImport("Imported", source, displayName, outcome.get());
    }

    private void reportImport(String verb, Path source, Optional<String> displayName, ImportOutcome outcome) {
        notifier.show(verb + " " + source.getFileName() + displayName.map(name -> " (" + name + ")").orElse(""));
        outcome.warnings().forEach(notifier::show);
    }

    private void activateEntry(AssetEntry entry) {
        Path path = Path.of(entry.assetPath());
        switch (entry.type()) {
            case SCRIPT, SHADER -> onOpenScript.accept(path);
            case PREFAB -> onInstantiatePrefab.accept(path);
            case SCENE -> onOpenScene.accept(path);
            case GRAPH -> onOpenGraph.accept(path);
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
            AssetMetaFile.moveAlongside(path, target);
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
            Path copiedFile = currentDirectory.resolve(file.getFileName().toString());
            if (importPipeline.needsImport(copiedFile)) {
                importAndReport(copiedFile);
            }
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

    private void duplicateFile(Path source) {
        try {
            Path target = uniqueSibling(source);
            Files.copy(source, target);
            refresh();
            notifier.show("Duplicated: " + target.getFileName());
        } catch (IOException error) {
            notifier.show("Duplicate failed: " + error.getMessage());
        }
    }

    private static Path uniqueSibling(Path source) {
        String name = source.getFileName().toString();
        int dot = name.indexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        int index = 2;
        Path candidate = source.resolveSibling(base + " " + index + extension);
        while (Files.exists(candidate)) {
            index++;
            candidate = source.resolveSibling(base + " " + index + extension);
        }
        return candidate;
    }

    private void moveFile(Path source, Path targetDirectory) {
        if (source.getParent().equals(targetDirectory)) {
            return;
        }
        try {
            Path target = targetDirectory.resolve(source.getFileName());
            if (Files.exists(target)) {
                notifier.show("Already exists there: " + source.getFileName());
                return;
            }
            Files.move(source, target);
            AssetMetaFile.moveAlongside(source, target);
            refresh();
            notifier.show("Moved: " + source.getFileName());
        } catch (IOException error) {
            notifier.show("Move failed: " + error.getMessage());
        }
    }

    private void acceptAssetDrop(Path targetDirectory) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        for (String mimeType : AssetMimeTypes.ALL) {
            String payload = ImGui.acceptDragDropPayload(mimeType);
            if (payload != null && !payload.startsWith(MeshAssetLoader.PRESET_PREFIX)) {
                moveFile(Path.of(payload), targetDirectory);
                break;
            }
        }
        ImGui.endDragDropTarget();
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
            AssetMetaFile.deleteAlongside(path);
            notifier.show("Deleted: " + path.getFileName());
            refresh();
        } catch (IOException error) {
            notifier.show("Delete failed: " + error.getMessage());
        }
    }

    private Path targetDirectory() {
        return showingBuiltins ? project.rootDirectory() : currentDirectory;
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
        showingBuiltins = false;
        currentDirectory = directory;
        refresh();
    }

    private void refresh() {
        entries.clear();
        if (!showingBuiltins && !Files.isDirectory(currentDirectory)) {
            currentDirectory = project.rootDirectory();
        }
        List<AssetEntry> scanned = showingBuiltins ? BuiltinAssets.entries() : scanCurrentDirectory();
        entries.addAll(scanned);
        if (!showingBuiltins) {
            autoImportScanned(scanned);
        }
    }

    private void autoImportScanned(List<AssetEntry> scanned) {
        if (query.isSearching()) {
            return;
        }
        for (AssetEntry entry : scanned) {
            enqueueIfStale(Path.of(entry.assetPath()));
        }
    }

    private void enqueueIfStale(Path source) {
        if (queuedSources.contains(source) || wasRecentlyChecked(source)) {
            return;
        }
        lastImportCheckAt.put(source, System.currentTimeMillis());
        if (importPipeline.needsImport(source)) {
            queuedSources.add(source);
            importQueue.addLast(source);
        }
    }

    private boolean wasRecentlyChecked(Path source) {
        Long checkedAt = lastImportCheckAt.get(source);
        return checkedAt != null && System.currentTimeMillis() - checkedAt < NEEDS_IMPORT_RECHECK_MILLIS;
    }

    private void processImportQueue() {
        Path source = importQueue.pollFirst();
        if (source == null) {
            return;
        }
        queuedSources.remove(source);
        importAndReport(source);
    }

    private List<AssetEntry> scanCurrentDirectory() {
        try {
            return query.isSearching()
                    ? AssetScanner.searchRecursively(currentDirectory)
                    : AssetScanner.listDirectory(currentDirectory);
        } catch (IOException error) {
            notifier.show("Could not list directory: " + error.getMessage());
            return List.of();
        }
    }

    private static String mimeFor(AssetType type) {
        return switch (type) {
            case MESH, PRESET -> AssetMimeTypes.MESH;
            case TEXTURE -> AssetMimeTypes.TEXTURE;
            case AUDIO -> AssetMimeTypes.AUDIO;
            case SHADER -> AssetMimeTypes.SHADER;
            case PREFAB -> AssetMimeTypes.PREFAB;
            case GRAPH -> AssetMimeTypes.GRAPH;
            case MATERIAL -> AssetMimeTypes.MATERIAL;
            case CLIP -> AssetMimeTypes.CLIP;
            case SCENE, SCRIPT, OTHER -> AssetMimeTypes.NONE;
        };
    }

    private static EditorIcon iconFor(AssetType type) {
        return switch (type) {
            case MESH, PRESET -> EditorIcon.MESH;
            case SCRIPT, SHADER -> EditorIcon.SCRIPT;
            case PREFAB -> EditorIcon.NODE_3D;
            case GRAPH -> EditorIcon.GRID;
            case MATERIAL -> EditorIcon.MESH;
            case SCENE -> EditorIcon.LOAD;
            case AUDIO, CLIP -> EditorIcon.ANIMATION_PLAYER;
            case TEXTURE, OTHER -> EditorIcon.FILE;
        };
    }

}
