package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.graph.BuiltinNodes;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphComponent;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphInstance;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.GraphValues;
import fr.epistudio.epysia.graph.GraphVariable;
import fr.epistudio.epysia.graph.NodeDefinition;
import fr.epistudio.epysia.graph.NodeSetting;
import fr.epistudio.epysia.editor.preview.NodePreviewCache;
import fr.epistudio.epysia.editor.preview.ShaderGraphPreviewService;
import fr.epistudio.epysia.graph.PinDefinition;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.graph.PinType;
import fr.epistudio.epysia.graph.ReflectionNodes;
import fr.epistudio.epysia.graph.SettingKind;
import fr.epistudio.epysia.graph.StateNodes;
import fr.epistudio.epysia.graph.shader.ShaderGraphCompiler;
import fr.epistudio.epysia.graph.shader.ShaderNodes;
import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MouseButton;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import imgui.ImGui;
import imgui.extension.imnodes.ImNodes;
import imgui.extension.imnodes.flag.ImNodesCol;
import imgui.extension.imnodes.flag.ImNodesMiniMapLocation;
import imgui.extension.imnodes.flag.ImNodesPinShape;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class GraphEditorView {

    public static final String WINDOW_TITLE = "Graph Editor";

    private static final int PIN_STRIDE = 256;
    private static final int OUTPUT_SLOT_OFFSET = 100;
    private static final int SETTING_SLOT_OFFSET = 200;
    private static final float LITERAL_WIDTH = 90.0f;
    private static final float VECTOR_LITERAL_WIDTH = 168.0f;
    private static final float VARIABLES_PANEL_WIDTH = 240.0f;
    private static final float MINIMAP_SIZE_FRACTION = 0.18f;
    private static final float SPLITTER_THICKNESS = 5.0f;
    private static final float MIN_PANEL_WIDTH = 120.0f;
    private static final float MAX_PANEL_WIDTH = 720.0f;
    private static final int SPLITTER_COLOR = 0x22FFFFFF;
    private static final int SPLITTER_ACTIVE_COLOR = 0xFFC4823A;
    private static final long FLASH_DURATION_NANOS = 400_000_000L;
    private static final int COLOR_EXEC = 0xFFFFFFFF;
    private static final int COLOR_FLOAT = 0xFF6BD66B;
    private static final int COLOR_INT = 0xFF7BD6C4;
    private static final int COLOR_BOOLEAN = 0xFF5A5AE0;
    private static final int COLOR_STRING = 0xFFD65AD6;
    private static final int COLOR_VECTOR2 = 0xFF7AE0B4;
    private static final int COLOR_VECTOR3 = 0xFF5AD0E0;
    private static final int COLOR_VECTOR4 = 0xFF4A9AE8;
    private static final int COLOR_NUMERIC = 0xFFC8E0A0;
    private static final float TEXTURE_PREVIEW_SIZE = 48.0f;
    private static final int TEXTURE_PREVIEW_SLOT_OFFSET = 250;
    private static final int NODE_PREVIEW_SLOT_OFFSET = 251;
    private static final float NODE_PREVIEW_SIZE = 56.0f;
    private static final float PREVIEW_PANEL_WIDTH = 300.0f;
    private static final float PREVIEW_IMAGE_SIZE = 280.0f;
    private static final float ORBIT_SPEED = 0.01f;
    private static final float PREVIEW_CULL_MARGIN = 96.0f;
    private static final String PROJECT_MESH_LABEL = "Project Mesh...";
    private static final int COLOR_PREVIEW_ERROR = 0xFF5A5AE8;
    private static final int CUSTOM_CODE_WIDTH = 260;
    private static final int CUSTOM_CODE_HEIGHT = 110;
    private static final int COLOR_GAME_OBJECT = 0xFFE0A05A;
    private static final int COLOR_OBJECT = 0xFFB0B0B0;
    private static final int COLOR_FLASH = 0xFF3AC4FF;
    private static final int COLOR_STATE_TITLE = 0xFF6A4A26;
    private static final int COLOR_STATE_ACTIVE = 0xFF34B42A;
    private static final int COLOR_TRANSITION_TITLE = 0xFF2878C8;
    private static final float SELF_TRANSITION_OFFSET = 120.0f;
    private static final float SECONDS_PER_NANO = 1.0e-9f;
    private static final NodeSetting TRANSITION_VARIABLE_SETTING =
            new NodeSetting(BuiltinNodes.VARIABLE_NAME_SETTING, SettingKind.VARIABLE_NAME, "");
    private static final NodeSetting TRANSITION_OPERATOR_SETTING =
            new NodeSetting(BuiltinNodes.OPERATOR_SETTING, SettingKind.COMPARISON, StateNodes.DEFAULT_OPERATOR);
    private static final NodeSetting TRANSITION_THRESHOLD_SETTING =
            new NodeSetting(StateNodes.THRESHOLD_SETTING, SettingKind.NUMBER, 0.0f);
    private static final NodeSetting TRANSITION_PRIORITY_SETTING =
            new NodeSetting(StateNodes.PRIORITY_SETTING, SettingKind.WHOLE_NUMBER, StateNodes.DEFAULT_PRIORITY);
    private static final String NODE_SEARCH_POPUP = "##graph-node-search";
    private static final List<String> COMPARISON_OPERATORS = List.of(">", "<", ">=", "<=", "==", "!=");
    private static final List<String> LOG_LEVELS = List.of("Info", "Warning", "Error");
    private static final List<PinType> VARIABLE_TYPES = List.of(PinType.FLOAT, PinType.INT,
            PinType.BOOLEAN, PinType.STRING, PinType.VECTOR3);

    private final ComponentRegistry componentRegistry;
    private final Notifier notifier;
    private final Supplier<SceneDocument> activeDocument;
    private final ThumbnailCache thumbnails;
    private final Consumer<Path> onGeneratedShaderSaved;
    private final ShaderGraphPreviewService previews;
    private final AssetPicker assetPicker;
    private final BooleanSupplier nodePreviewsEnabled;
    private final Consumer<Boolean> onNodePreviewsToggled;
    private final GraphJsonCodec codec = new GraphJsonCodec();
    private final Map<Path, OpenGraph> openGraphs = new LinkedHashMap<>();
    private final ImString nodeSearch = new ImString(128);
    private GraphNodeRegistry registry;
    private Path focusRequest;
    private boolean windowFocusRequest;
    private boolean contextCreated;
    private boolean nodeSearchRequested;
    private boolean centerViewRequested;
    private boolean sidePanelVisible = true;
    private boolean previewPanelVisible = true;
    private float sidePanelWidth = VARIABLES_PANEL_WIDTH;
    private float previewPanelWidth = PREVIEW_PANEL_WIDTH;
    private float popupSpawnX;
    private float popupSpawnY;
    private float canvasMinX;
    private float canvasMinY;
    private float canvasMaxX;
    private float canvasMaxY;

    public GraphEditorView(ComponentRegistry componentRegistry, Notifier notifier,
                           Supplier<SceneDocument> activeDocument, ThumbnailCache thumbnails,
                           Consumer<Path> onGeneratedShaderSaved, ShaderGraphPreviewService previews,
                           AssetPicker assetPicker, BooleanSupplier nodePreviewsEnabled,
                           Consumer<Boolean> onNodePreviewsToggled) {
        this.componentRegistry = componentRegistry;
        this.notifier = notifier;
        this.activeDocument = activeDocument;
        this.thumbnails = thumbnails;
        this.onGeneratedShaderSaved = onGeneratedShaderSaved;
        this.previews = previews;
        this.assetPicker = assetPicker;
        this.nodePreviewsEnabled = nodePreviewsEnabled;
        this.onNodePreviewsToggled = onNodePreviewsToggled;
        rebuildRegistry();
    }

    public void refreshReflectionNodes() {
        rebuildRegistry();
    }

    private void rebuildRegistry() {
        registry = GraphNodeRegistry.withBuiltins();
        registry.setClassResolver(this::resolveClass);
        for (NodeDefinition definition : ReflectionNodes.catalog(componentRegistry)) {
            registry.register(definition);
        }
    }

    private Optional<Class<?>> resolveClass(String className) {
        for (ComponentRegistry.Entry entry : componentRegistry.entries()) {
            if (entry.componentClass().getName().equals(className)) {
                return Optional.of(entry.componentClass());
            }
        }
        return resolveFromClasspath(className);
    }

    private static Optional<Class<?>> resolveFromClasspath(String className) {
        try {
            return Optional.of(Class.forName(className, false, GraphEditorView.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError missing) {
            return Optional.empty();
        }
    }

    public void open(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!openGraphs.containsKey(normalized)) {
            loadGraph(normalized);
        }
        focusRequest = normalized;
        windowFocusRequest = true;
    }

    private void loadGraph(Path path) {
        try {
            openGraphs.put(path, new OpenGraph(codec.readFromFile(path)));
        } catch (IOException | RuntimeException error) {
            notifier.show("Could not open graph: " + error.getMessage());
        }
    }

    public void render() {
        ensureContext();
        if (openGraphs.isEmpty()) {
            renderEmptyWindow();
            return;
        }
        if (windowFocusRequest) {
            ImGui.setNextWindowFocus();
            windowFocusRequest = false;
        }
        if (!ImGui.begin(WINDOW_TITLE)) {
            ImGui.end();
            return;
        }
        previews.beginFrame();
        renderTabs();
        ImGui.end();
        assetPicker.render();
    }

    public void shutdown() {
        previews.shutdown();
    }

    private void ensureContext() {
        if (!contextCreated) {
            ImNodes.createContext();
            contextCreated = true;
        }
    }

    private void renderEmptyWindow() {
        if (ImGui.begin(WINDOW_TITLE)) {
            ImGui.textDisabled("No graph open.");
            ImGui.textDisabled("Double-click a .epygraph file in the asset browser.");
        }
        ImGui.end();
    }

    private void renderTabs() {
        if (!ImGui.beginTabBar("##graph-tabs", ImGuiTabBarFlags.Reorderable)) {
            return;
        }
        for (Map.Entry<Path, OpenGraph> entry : new ArrayList<>(openGraphs.entrySet())) {
            renderTab(entry.getKey(), entry.getValue());
        }
        ImGui.endTabBar();
    }

    private void renderTab(Path path, OpenGraph graph) {
        ImBoolean keepOpen = new ImBoolean(true);
        int flags = graph.dirty ? ImGuiTabItemFlags.UnsavedDocument : ImGuiTabItemFlags.None;
        if (path.equals(focusRequest)) {
            flags |= ImGuiTabItemFlags.SetSelected;
            focusRequest = null;
        }
        boolean selected = ImGui.beginTabItem(path.getFileName().toString() + "##" + path, keepOpen, flags);
        if (selected) {
            renderGraph(path, graph);
            ImGui.endTabItem();
        }
        if (!keepOpen.get()) {
            openGraphs.remove(path);
            previews.invalidateGraph(path);
        }
    }

    private void renderGraph(Path path, OpenGraph graph) {
        handleSaveShortcut(path, graph);
        renderPanelToggles(graph);
        if (sidePanelVisible) {
            renderVariablesPanel(path, graph);
            ImGui.sameLine();
            sidePanelWidth = renderVerticalSplitter("##graph-side-splitter", sidePanelWidth, true);
            ImGui.sameLine();
        }
        boolean previewVisible = graph.asset.kind().isShader() && previewPanelVisible;
        if (!previewVisible) {
            renderCanvas(path, graph);
            return;
        }
        ImGui.beginChild("##graph-canvas-host", -(previewPanelWidth + SPLITTER_THICKNESS), 0.0f, false);
        renderCanvas(path, graph);
        ImGui.endChild();
        ImGui.sameLine();
        previewPanelWidth = renderVerticalSplitter("##graph-preview-splitter", previewPanelWidth, false);
        ImGui.sameLine();
        renderPreviewPanel(path, graph);
    }

    private void renderPanelToggles(OpenGraph graph) {
        if (ImGui.smallButton(sidePanelVisible ? "< Panel" : "> Panel")) {
            sidePanelVisible = !sidePanelVisible;
        }
        if (graph.asset.kind().isShader()) {
            ImGui.sameLine();
            if (ImGui.smallButton(previewPanelVisible ? "Preview >" : "Preview <")) {
                previewPanelVisible = !previewPanelVisible;
            }
        }
    }

    private float renderVerticalSplitter(String id, float width, boolean growsWithDrag) {
        ImGui.pushStyleColor(ImGuiCol.Button, SPLITTER_COLOR);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, SPLITTER_ACTIVE_COLOR);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, SPLITTER_ACTIVE_COLOR);
        ImGui.button(id, SPLITTER_THICKNESS, Math.max(1.0f, ImGui.getContentRegionAvailY()));
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered() || ImGui.isItemActive()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        float adjusted = width;
        if (ImGui.isItemActive()) {
            float delta = ImGui.getIO().getMouseDeltaX();
            adjusted += growsWithDrag ? delta : -delta;
        }
        return Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, adjusted));
    }

    private void renderPreviewPanel(Path path, OpenGraph graph) {
        ImGui.beginChild("##graph-preview", 0.0f, 0.0f, true);
        ImGui.textDisabled("Preview");
        renderMeshSelector(path, graph);
        renderMainPreviewImage(path, graph);
        renderPreviewToggles();
        ImGui.endChild();
    }

    private void renderMainPreviewImage(Path path, OpenGraph graph) {
        previews.mainPreviewTexture(path, graph.asset, System.nanoTime())
                .ifPresentOrElse(this::drawMainPreview,
                        () -> ImGui.textDisabled("Building preview..."));
        previews.mainErrorMessage().ifPresent(message -> {
            ImGui.pushTextWrapPos(0.0f);
            ImGui.textColored(COLOR_PREVIEW_ERROR, "Compile error: " + message);
            ImGui.popTextWrapPos();
        });
    }

    private void drawMainPreview(int texture) {
        ImGui.image(texture, PREVIEW_IMAGE_SIZE, PREVIEW_IMAGE_SIZE, 0.0f, 1.0f, 1.0f, 0.0f);
        handlePreviewCameraInput();
    }

    private void handlePreviewCameraInput() {
        if (!ImGui.isItemHovered()) {
            return;
        }
        if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            previews.orbitMain(ImGui.getIO().getMouseDeltaX() * ORBIT_SPEED,
                    ImGui.getIO().getMouseDeltaY() * ORBIT_SPEED);
        }
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel != 0.0f) {
            previews.zoomMain(wheel);
        }
    }

    private void renderMeshSelector(Path path, OpenGraph graph) {
        if (graph.asset.kind() != GraphKind.SHADER_SURFACE) {
            return;
        }
        String current = previews.meshPathFor(path);
        ImGui.setNextItemWidth(-1.0f);
        if (!ImGui.beginCombo("##preview-mesh", meshLabel(current))) {
            return;
        }
        selectMeshItem(path, "Sphere", ShaderGraphPreviewService.SPHERE_MESH, current);
        selectMeshItem(path, "Cube", ShaderGraphPreviewService.CUBE_MESH, current);
        selectMeshItem(path, "Plane", ShaderGraphPreviewService.PLANE_MESH, current);
        if (ImGui.selectable(PROJECT_MESH_LABEL)) {
            assetPicker.open(UploadedMesh.class, picked -> previews.setMeshPath(path, picked));
        }
        ImGui.endCombo();
    }

    private void selectMeshItem(Path path, String label, String meshPath, String current) {
        if (ImGui.selectable(label, meshPath.equals(current))) {
            previews.setMeshPath(path, meshPath);
        }
    }

    private static String meshLabel(String meshPath) {
        if (meshPath.equals(ShaderGraphPreviewService.SPHERE_MESH)) {
            return "Sphere";
        }
        if (meshPath.equals(ShaderGraphPreviewService.CUBE_MESH)) {
            return "Cube";
        }
        if (meshPath.equals(ShaderGraphPreviewService.PLANE_MESH)) {
            return "Plane";
        }
        return Path.of(meshPath).getFileName().toString();
    }

    private void renderPreviewToggles() {
        ImBoolean enabled = new ImBoolean(nodePreviewsEnabled.getAsBoolean());
        if (ImGui.checkbox("Node previews", enabled)) {
            onNodePreviewsToggled.accept(enabled.get());
        }
        ImGui.textDisabled("Live targets: " + previews.liveNodeTargetCount()
                + " / " + NodePreviewCache.MAX_LIVE_TARGETS);
    }

    private void handleSaveShortcut(Path path, OpenGraph graph) {
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.S)
                && ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)) {
            save(path, graph);
        }
    }

    private void save(Path path, OpenGraph graph) {
        try {
            codec.writeToFile(graph.asset, path);
            graph.dirty = false;
            notifier.show("Saved " + path.getFileName());
            writeGeneratedShader(path, graph);
        } catch (IOException error) {
            notifier.show("Graph save failed: " + error.getMessage());
        }
    }

    private void writeGeneratedShader(Path path, OpenGraph graph) throws IOException {
        if (!graph.asset.kind().isShader()) {
            return;
        }
        String source;
        try {
            source = new ShaderGraphCompiler().compile(graph.asset, path.getFileName().toString());
        } catch (EpysiaException error) {
            notifier.show("Shader graph compile failed: " + error.getMessage());
            return;
        }
        Path generatedFile = generatedShaderPath(path, graph.asset);
        Files.writeString(generatedFile, source);
        onGeneratedShaderSaved.accept(generatedFile);
        notifier.show("Generated " + generatedFile.getFileName());
    }

    private static Path generatedShaderPath(Path graphPath, GraphAsset asset) {
        String fileName = graphPath.getFileName().toString();
        String baseName = fileName.endsWith(GraphJsonCodec.FILE_EXTENSION)
                ? fileName.substring(0, fileName.length() - GraphJsonCodec.FILE_EXTENSION.length())
                : fileName;
        return graphPath.resolveSibling(baseName + ShaderGraphCompiler.generatedSuffix(asset));
    }

    private void renderVariablesPanel(Path path, OpenGraph graph) {
        ImGui.beginChild("##graph-variables", sidePanelWidth, 0.0f, true);
        ImGui.textDisabled(kindLabel(graph.asset.kind()));
        if (ImGui.button("Save", -1.0f, 0.0f)) {
            save(path, graph);
        }
        if (ImGui.button("Center View", -1.0f, 0.0f)) {
            centerViewRequested = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Middle-click drag pans the canvas");
        }
        if (ImGui.button("Add Node", -1.0f, 0.0f)) {
            nodeSearchRequested = true;
        }
        ImGui.textDisabled("Right-click the canvas to search nodes");
        ImGui.separator();
        if (graph.asset.kind().isShader()) {
            renderShaderPanel(path, graph);
            ImGui.endChild();
            return;
        }
        ImGui.textDisabled("Variables");
        int removeIndex = -1;
        for (int index = 0; index < graph.asset.variables().size(); index++) {
            if (renderVariableRow(graph, index)) {
                removeIndex = index;
            }
        }
        removeVariableIfRequested(graph, removeIndex);
        if (ImGui.button("Add Variable", -1.0f, 0.0f)) {
            addVariable(graph);
        }
        ImGui.endChild();
    }

    private static String kindLabel(GraphKind kind) {
        return switch (kind) {
            case LOGIC -> "Logic Graph";
            case STATE_MACHINE -> "State Machine";
            case SHADER_SURFACE -> "Surface Shader Graph";
            case SHADER_POST -> "Post Effect Shader Graph";
        };
    }

    private void renderShaderPanel(Path path, OpenGraph graph) {
        ImGui.textDisabled("Parameters");
        boolean anyParameter = false;
        for (GraphNode node : graph.asset.nodes()) {
            if (node.typeKey().startsWith("shader.parameter.")) {
                ImGui.textUnformatted(parameterLabel(node));
                anyParameter = true;
            }
        }
        if (!anyParameter) {
            ImGui.textDisabled("Add Parameter nodes to expose values.");
        }
        ImGui.separator();
        ImGui.textDisabled("Generated file:");
        ImGui.textWrapped(generatedShaderPath(path, graph.asset).getFileName().toString());
    }

    private static String parameterLabel(GraphNode node) {
        String name = GraphValues.asString(node.values().getOrDefault(ShaderNodes.NAME_SETTING, ""));
        String kind = switch (node.typeKey()) {
            case ShaderNodes.PARAMETER_FLOAT -> "Float";
            case ShaderNodes.PARAMETER_COLOR -> "Color";
            default -> "Texture";
        };
        return (name.isEmpty() ? "(unnamed)" : name) + "  [" + kind + "]";
    }

    private void removeVariableIfRequested(OpenGraph graph, int removeIndex) {
        if (removeIndex >= 0) {
            graph.asset.variables().remove(removeIndex);
            graph.dirty = true;
        }
    }

    private void addVariable(OpenGraph graph) {
        int suffix = graph.asset.variables().size() + 1;
        String name = "Variable" + suffix;
        while (graph.asset.findVariable(name).isPresent()) {
            suffix++;
            name = "Variable" + suffix;
        }
        graph.asset.variables().add(new GraphVariable(name, PinType.FLOAT, 0.0f, false));
        graph.dirty = true;
    }

    private boolean renderVariableRow(OpenGraph graph, int index) {
        GraphVariable variable = graph.asset.variables().get(index);
        ImGui.pushID("variable-" + index);
        renderVariableName(graph, variable);
        renderVariableType(graph, variable);
        renderVariableDefault(graph, variable);
        boolean exposed = variable.exposed();
        if (ImGui.checkbox("Exposed", exposed)) {
            variable.setExposed(!exposed);
            graph.dirty = true;
        }
        ImGui.sameLine();
        boolean removeRequested = ImGui.smallButton("Remove");
        ImGui.separator();
        ImGui.popID();
        return removeRequested;
    }

    private void renderVariableName(OpenGraph graph, GraphVariable variable) {
        ImString buffer = graph.textBuffer("variable-name-" + System.identityHashCode(variable),
                variable.name());
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.inputText("##name", buffer)) {
            renameVariable(graph, variable, buffer.get());
        }
    }

    private void renameVariable(OpenGraph graph, GraphVariable variable, String newName) {
        String cleaned = newName.replace("\0", "").strip();
        if (cleaned.isEmpty() || cleaned.equals(variable.name())) {
            return;
        }
        String oldName = variable.name();
        variable.setName(cleaned);
        updateVariableNodeReferences(graph, oldName, cleaned);
        graph.dirty = true;
    }

    private void updateVariableNodeReferences(OpenGraph graph, String oldName, String newName) {
        for (GraphNode node : graph.asset.nodes()) {
            Object referenced = node.values().get(BuiltinNodes.VARIABLE_NAME_SETTING);
            if (oldName.equals(referenced)) {
                node.values().put(BuiltinNodes.VARIABLE_NAME_SETTING, newName);
            }
        }
    }

    private void renderVariableType(OpenGraph graph, GraphVariable variable) {
        ImGui.setNextItemWidth(-1.0f);
        if (!ImGui.beginCombo("##type", variable.type().name())) {
            return;
        }
        for (PinType type : VARIABLE_TYPES) {
            if (ImGui.selectable(type.name(), type == variable.type())) {
                variable.setType(type);
                variable.setDefaultValue(GraphValues.defaultFor(type));
                graph.dirty = true;
            }
        }
        ImGui.endCombo();
    }

    private void renderVariableDefault(OpenGraph graph, GraphVariable variable) {
        ImGui.setNextItemWidth(-1.0f);
        String key = "variable-default-" + System.identityHashCode(variable);
        Optional<Object> edited = renderValueWidget("##default", key, variable.type(),
                variable.defaultValue(), graph);
        edited.ifPresent(value -> {
            variable.setDefaultValue(value);
            graph.dirty = true;
        });
    }

    private Optional<Object> renderValueWidget(String label, String bufferKey, PinType type,
                                               Object current, OpenGraph graph) {
        return switch (type) {
            case FLOAT, NUMERIC -> renderFloatWidget(label, current);
            case INT -> renderIntWidget(label, current);
            case BOOLEAN -> renderBooleanWidget(label, current);
            case STRING -> renderStringWidget(label, bufferKey, current, graph);
            case VECTOR2 -> renderVector2Widget(label, current);
            case VECTOR3 -> renderVectorWidget(label, current);
            case VECTOR4 -> renderVector4Widget(label, current);
            case EXEC, GAME_OBJECT, OBJECT -> Optional.empty();
        };
    }

    private static Optional<Object> renderFloatWidget(String label, Object current) {
        float[] value = {GraphValues.asFloat(current)};
        if (ImGui.dragFloat(label, value, 0.05f)) {
            return Optional.of(value[0]);
        }
        return Optional.empty();
    }

    private static Optional<Object> renderIntWidget(String label, Object current) {
        int[] value = {GraphValues.asInt(current)};
        if (ImGui.dragInt(label, value)) {
            return Optional.of(value[0]);
        }
        return Optional.empty();
    }

    private static Optional<Object> renderBooleanWidget(String label, Object current) {
        boolean value = GraphValues.asBoolean(current);
        if (ImGui.checkbox(label, value)) {
            return Optional.of(!value);
        }
        return Optional.empty();
    }

    private Optional<Object> renderStringWidget(String label, String bufferKey,
                                                Object current, OpenGraph graph) {
        ImString buffer = graph.textBuffer(bufferKey, GraphValues.asString(current));
        if (ImGui.inputText(label, buffer)) {
            return Optional.of(buffer.get().replace("\0", ""));
        }
        return Optional.empty();
    }

    private static Optional<Object> renderVectorWidget(String label, Object current) {
        Vector3f vector = GraphValues.asVector(current);
        float[] values = {vector.x, vector.y, vector.z};
        if (ImGui.dragFloat3(label, values, 0.05f)) {
            return Optional.of(new Vector3f(values[0], values[1], values[2]));
        }
        return Optional.empty();
    }

    private static Optional<Object> renderVector2Widget(String label, Object current) {
        Vector2f vector = GraphValues.asVector2(current);
        float[] values = {vector.x, vector.y};
        if (ImGui.dragFloat2(label, values, 0.05f)) {
            return Optional.of(new Vector2f(values[0], values[1]));
        }
        return Optional.empty();
    }

    private static Optional<Object> renderVector4Widget(String label, Object current) {
        Vector4f vector = GraphValues.asVector4(current);
        float[] values = {vector.x, vector.y, vector.z, vector.w};
        if (ImGui.dragFloat4(label, values, 0.05f)) {
            return Optional.of(new Vector4f(values[0], values[1], values[2], values[3]));
        }
        return Optional.empty();
    }

    private static Optional<Object> renderColorWidget(String label, Object current) {
        Vector4f color = GraphValues.asVector4(current);
        float[] components = {color.x, color.y, color.z, color.w};
        if (ImGui.colorEdit4(label, components)) {
            return Optional.of(new Vector4f(components[0], components[1], components[2], components[3]));
        }
        return Optional.empty();
    }

    private void renderCanvas(Path path, OpenGraph graph) {
        ImGui.beginChild("##graph-canvas", 0.0f, 0.0f, false);
        Optional<GraphInstance> debugInstance = debugInstanceFor(path);
        graph.pinsById.clear();
        captureCanvasRect();
        graph.graphPath = path;
        if (centerViewRequested) {
            ImNodes.editorContextResetPanning(0.0f, 0.0f);
            centerViewRequested = false;
        }
        ImNodes.beginNodeEditor();
        for (GraphNode node : new ArrayList<>(graph.asset.nodes())) {
            renderNode(graph, node, debugInstance);
        }
        renderLinks(graph, debugInstance);
        ImNodes.miniMap(MINIMAP_SIZE_FRACTION, ImNodesMiniMapLocation.BottomRight);
        ImNodes.endNodeEditor();
        syncNodePositions(graph);
        handleLinkCreated(graph);
        handleLinkDestroyed(graph);
        handleDeletions(graph);
        handleContextMenu();
        renderNodeSearchPopup(graph);
        ImGui.endChild();
    }

    private Optional<GraphInstance> debugInstanceFor(Path path) {
        String pathText = path.toString();
        for (GameObject gameObject : activeDocument.get().scene().gameObjects()) {
            Optional<GraphComponent> component = gameObject.getComponent(GraphComponent.class);
            if (component.isPresent() && component.get().graphPath().equals(pathText)) {
                return component.get().runtimeInstance();
            }
        }
        return Optional.empty();
    }

    private static boolean recentlyFired(long nanos) {
        return nanos > 0 && System.nanoTime() - nanos < FLASH_DURATION_NANOS;
    }

    private void renderNode(OpenGraph graph, GraphNode node, Optional<GraphInstance> debugInstance) {
        if (graph.placedNodes.add(node.id())) {
            ImNodes.setNodeGridSpacePos(node.id(), node.positionX(), node.positionY());
        }
        boolean flash = debugInstance.map(instance -> recentlyFired(instance.nodeFireNanos(node.id())))
                .orElse(false);
        int pushedColors = pushTitleStyle(node, flash, isActiveState(node, debugInstance));
        renderNodeBody(graph, node, debugInstance);
        for (int index = 0; index < pushedColors; index++) {
            ImNodes.popColorStyle();
        }
    }

    private static boolean isActiveState(GraphNode node, Optional<GraphInstance> debugInstance) {
        return StateNodes.isState(node)
                && debugInstance.map(instance -> instance.activeStateId() == node.id()).orElse(false);
    }

    private static int pushTitleStyle(GraphNode node, boolean flash, boolean activeState) {
        if (activeState) {
            ImNodes.pushColorStyle(ImNodesCol.TitleBar, COLOR_STATE_ACTIVE);
            ImNodes.pushColorStyle(ImNodesCol.TitleBarHovered, COLOR_STATE_ACTIVE);
            ImNodes.pushColorStyle(ImNodesCol.TitleBarSelected, COLOR_STATE_ACTIVE);
            return 3;
        }
        if (flash) {
            ImNodes.pushColorStyle(ImNodesCol.TitleBar, COLOR_FLASH);
            return 1;
        }
        if (StateNodes.isState(node)) {
            ImNodes.pushColorStyle(ImNodesCol.TitleBar, COLOR_STATE_TITLE);
            return 1;
        }
        if (StateNodes.isTransition(node)) {
            ImNodes.pushColorStyle(ImNodesCol.TitleBar, COLOR_TRANSITION_TITLE);
            return 1;
        }
        return 0;
    }

    private void renderNodeBody(OpenGraph graph, GraphNode node, Optional<GraphInstance> debugInstance) {
        ImNodes.beginNode(node.id());
        ImNodes.beginNodeTitleBar();
        ImGui.textUnformatted(titleOf(graph, node));
        if (StateNodes.isState(node) && StateNodes.markedInitial(node)) {
            ImGui.sameLine();
            ImGui.textDisabled("[Initial]");
        }
        debugInstance.ifPresent(instance -> renderTitleDebug(node, instance));
        ImNodes.endNodeTitleBar();
        renderSettings(graph, node);
        renderInputPins(graph, node);
        renderOutputPins(graph, node);
        renderNodePreview(graph, node);
        ImNodes.endNode();
    }

    private void renderNodePreview(OpenGraph graph, GraphNode node) {
        if (!previewableNode(graph, node)) {
            return;
        }
        ImNodes.beginStaticAttribute(node.id() * PIN_STRIDE + NODE_PREVIEW_SLOT_OFFSET);
        if (graph.collapsedPreviews.contains(node.id())) {
            renderPreviewExpandButton(graph, node);
        } else {
            renderNodePreviewImage(graph, node);
        }
        ImNodes.endStaticAttribute();
    }

    private void renderPreviewExpandButton(OpenGraph graph, GraphNode node) {
        if (ImGui.smallButton("Show preview##" + node.id())) {
            graph.collapsedPreviews.remove(node.id());
        }
    }

    private void renderNodePreviewImage(OpenGraph graph, GraphNode node) {
        String pinName = previewPinOf(graph, node).orElse(ShaderNodes.RESULT_PIN);
        if (!nodeVisibleInViewport(node.id())) {
            ImGui.dummy(NODE_PREVIEW_SIZE, NODE_PREVIEW_SIZE);
            return;
        }
        previews.nodePreviewTexture(graph.graphPath, graph.asset, node.id(), pinName)
                .ifPresentOrElse(texture -> ImGui.image(texture, NODE_PREVIEW_SIZE, NODE_PREVIEW_SIZE,
                                0.0f, 1.0f, 1.0f, 0.0f),
                        () -> renderNodePreviewFallback(graph, node, pinName));
        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            graph.collapsedPreviews.add(node.id());
        }
    }

    private void renderNodePreviewFallback(OpenGraph graph, GraphNode node, String pinName) {
        previews.nodeErrorMessage(graph.graphPath, node.id(), pinName)
                .ifPresentOrElse(message -> ImGui.textColored(COLOR_PREVIEW_ERROR, "No preview"),
                        () -> ImGui.textDisabled("..."));
    }

    private boolean previewableNode(OpenGraph graph, GraphNode node) {
        return graph.asset.kind().isShader() && nodePreviewsEnabled.getAsBoolean()
                && ShaderNodes.isShaderNode(node.typeKey()) && !ShaderNodes.isOutput(node.typeKey())
                && previewPinOf(graph, node).isPresent();
    }

    private Optional<String> previewPinOf(OpenGraph graph, GraphNode node) {
        List<PinDefinition> outputs = registry.effectiveOutputPins(graph.asset, node);
        return outputs.isEmpty() ? Optional.empty() : Optional.of(outputs.get(0).name());
    }

    private void captureCanvasRect() {
        canvasMinX = ImGui.getWindowPosX();
        canvasMinY = ImGui.getWindowPosY();
        canvasMaxX = canvasMinX + ImGui.getWindowSizeX();
        canvasMaxY = canvasMinY + ImGui.getWindowSizeY();
    }

    private boolean nodeVisibleInViewport(int nodeId) {
        float nodeMinX = ImNodes.getNodeScreenSpacePosX(nodeId);
        float nodeMinY = ImNodes.getNodeScreenSpacePosY(nodeId);
        float nodeMaxX = nodeMinX + ImNodes.getNodeDimensionsX(nodeId);
        float nodeMaxY = nodeMinY + ImNodes.getNodeDimensionsY(nodeId);
        return nodeMaxX >= canvasMinX - PREVIEW_CULL_MARGIN
                && nodeMinX <= canvasMaxX + PREVIEW_CULL_MARGIN
                && nodeMaxY >= canvasMinY - PREVIEW_CULL_MARGIN
                && nodeMinY <= canvasMaxY + PREVIEW_CULL_MARGIN;
    }

    private void renderTitleDebug(GraphNode node, GraphInstance instance) {
        if (instance.activeStateId() == node.id()) {
            float seconds = (System.nanoTime() - instance.stateEnteredNanos()) * SECONDS_PER_NANO;
            ImGui.sameLine();
            ImGui.textUnformatted(String.format(Locale.ROOT, "%.1fs", seconds));
            return;
        }
        int fireCount = instance.nodeFireCount(node.id());
        if (fireCount > 0) {
            ImGui.sameLine();
            ImGui.textDisabled("x" + fireCount);
        }
    }

    private String titleOf(OpenGraph graph, GraphNode node) {
        if (StateNodes.isState(node)) {
            return "State: " + StateNodes.stateName(node);
        }
        if (StateNodes.isTransition(node)) {
            return "When " + StateNodes.conditionSummary(node);
        }
        String variableName = GraphValues.asString(
                node.values().getOrDefault(BuiltinNodes.VARIABLE_NAME_SETTING, ""));
        if (node.typeKey().equals(BuiltinNodes.VARIABLE_GET)) {
            return "Get " + variableName;
        }
        if (node.typeKey().equals(BuiltinNodes.VARIABLE_SET)) {
            return "Set " + variableName;
        }
        return registry.find(node.typeKey()).map(NodeDefinition::displayName).orElse(node.typeKey());
    }

    private void renderSettings(OpenGraph graph, GraphNode node) {
        if (StateNodes.isState(node)) {
            renderStateSettings(graph, node);
            return;
        }
        if (StateNodes.isTransition(node)) {
            renderTransitionSettings(graph, node);
            return;
        }
        renderGenericSettings(graph, node);
    }

    private void renderStateSettings(OpenGraph graph, GraphNode node) {
        ImNodes.beginStaticAttribute(node.id() * PIN_STRIDE + SETTING_SLOT_OFFSET);
        renderStateNameSetting(graph, node);
        if (ImGui.checkbox("Initial", StateNodes.markedInitial(node))) {
            markInitial(graph, node);
        }
        ImNodes.endStaticAttribute();
    }

    private void renderStateNameSetting(OpenGraph graph, GraphNode node) {
        String bufferKey = "setting-" + node.id() + "-" + StateNodes.STATE_NAME_SETTING;
        ImString buffer = graph.textBuffer(bufferKey, StateNodes.stateName(node));
        ImGui.setNextItemWidth(VECTOR_LITERAL_WIDTH);
        if (ImGui.inputText("##state-name", buffer)) {
            node.values().put(StateNodes.STATE_NAME_SETTING, buffer.get().replace("\0", ""));
            graph.dirty = true;
        }
    }

    private void markInitial(OpenGraph graph, GraphNode node) {
        for (GraphNode state : graph.asset.nodesOfType(StateNodes.STATE)) {
            state.values().put(StateNodes.INITIAL_SETTING, state.id() == node.id());
        }
        graph.dirty = true;
    }

    private void renderTransitionSettings(OpenGraph graph, GraphNode node) {
        ImNodes.beginStaticAttribute(node.id() * PIN_STRIDE + SETTING_SLOT_OFFSET);
        boolean always = StateNodes.alwaysTaken(node);
        if (ImGui.checkbox("Always", always)) {
            node.values().put(StateNodes.ALWAYS_SETTING, !always);
            graph.dirty = true;
        }
        if (!always) {
            renderConditionRow(graph, node);
        }
        renderPriorityRow(graph, node);
        ImNodes.endStaticAttribute();
    }

    private void renderConditionRow(OpenGraph graph, GraphNode node) {
        renderEnumSetting(graph, node, TRANSITION_VARIABLE_SETTING, variableNames(graph));
        ImGui.sameLine();
        renderEnumSetting(graph, node, TRANSITION_OPERATOR_SETTING, COMPARISON_OPERATORS);
        ImGui.sameLine();
        renderNumberSetting(graph, node, TRANSITION_THRESHOLD_SETTING);
    }

    private void renderPriorityRow(OpenGraph graph, GraphNode node) {
        ImGui.textDisabled("Priority");
        ImGui.sameLine();
        renderWholeNumberSetting(graph, node, TRANSITION_PRIORITY_SETTING);
    }

    private void renderGenericSettings(OpenGraph graph, GraphNode node) {
        Optional<NodeDefinition> definition = registry.find(node.typeKey());
        if (definition.isEmpty()) {
            ImGui.textDisabled("Unknown node type");
            return;
        }
        List<NodeSetting> settings = definition.get().settings();
        for (int index = 0; index < settings.size(); index++) {
            if (hiddenShaderSetting(graph, node, settings.get(index))) {
                continue;
            }
            ImNodes.beginStaticAttribute(node.id() * PIN_STRIDE + SETTING_SLOT_OFFSET + index);
            renderSettingWidget(graph, node, settings.get(index));
            ImNodes.endStaticAttribute();
        }
        renderTexturePreview(graph, node);
    }

    private static boolean hiddenShaderSetting(OpenGraph graph, GraphNode node, NodeSetting setting) {
        if (node.typeKey().equals(ShaderNodes.TEXTURE_SAMPLE)) {
            boolean surface = graph.asset.kind() == GraphKind.SHADER_SURFACE;
            return surface ? setting.key().equals(ShaderNodes.PATH_SETTING)
                    : setting.key().equals(ShaderNodes.MATERIAL_SAMPLER_SETTING);
        }
        if (node.typeKey().equals(ShaderNodes.CUSTOM_CODE)) {
            return hiddenCustomCodeSetting(node, setting);
        }
        return false;
    }

    private static boolean hiddenCustomCodeSetting(GraphNode node, NodeSetting setting) {
        int count = Math.clamp(GraphValues.asInt(node.values()
                .getOrDefault(ShaderNodes.INPUT_COUNT_SETTING, 1)), 0, ShaderNodes.CUSTOM_CODE_MAX_INPUTS);
        for (int index = count; index < ShaderNodes.CUSTOM_CODE_MAX_INPUTS; index++) {
            if (setting.key().equals(ShaderNodes.customInputNameSetting(index))
                    || setting.key().equals(ShaderNodes.customInputTypeSetting(index))) {
                return true;
            }
        }
        return false;
    }

    private void renderTexturePreview(OpenGraph graph, GraphNode node) {
        boolean textureNode = node.typeKey().equals(ShaderNodes.TEXTURE_SAMPLE)
                || node.typeKey().equals(ShaderNodes.PARAMETER_TEXTURE);
        if (!textureNode || graph.asset.kind() == GraphKind.SHADER_SURFACE) {
            return;
        }
        String path = GraphValues.asString(node.values().getOrDefault(ShaderNodes.PATH_SETTING, "")).strip();
        if (path.isEmpty()) {
            return;
        }
        ImNodes.beginStaticAttribute(node.id() * PIN_STRIDE + TEXTURE_PREVIEW_SLOT_OFFSET);
        thumbnails.get(path).ifPresentOrElse(
                texture -> ImGui.image(texture, TEXTURE_PREVIEW_SIZE, TEXTURE_PREVIEW_SIZE),
                () -> ImGui.textDisabled("No preview"));
        ImNodes.endStaticAttribute();
    }

    private void renderSettingWidget(OpenGraph graph, GraphNode node, NodeSetting setting) {
        if (ShaderNodes.isShaderNode(node.typeKey()) && renderShaderSetting(graph, node, setting)) {
            return;
        }
        switch (setting.kind()) {
            case KEY -> renderEnumSetting(graph, node, setting, keyNames());
            case MOUSE_BUTTON -> renderEnumSetting(graph, node, setting, mouseButtonNames());
            case COMPARISON -> renderEnumSetting(graph, node, setting, COMPARISON_OPERATORS);
            case LOG_LEVEL -> renderEnumSetting(graph, node, setting, LOG_LEVELS);
            case VARIABLE_NAME -> renderEnumSetting(graph, node, setting, variableNames(graph));
            case WHOLE_NUMBER -> renderWholeNumberSetting(graph, node, setting);
            case NUMBER -> renderNumberSetting(graph, node, setting);
            case TOGGLE -> renderToggleSetting(graph, node, setting);
            case ASSET_PATH, TEXT -> renderTextSetting(graph, node, setting);
        }
    }

    private boolean renderShaderSetting(OpenGraph graph, GraphNode node, NodeSetting setting) {
        String key = setting.key();
        if (key.equals(ShaderNodes.MASTER_SETTING)) {
            renderEnumSetting(graph, node, setting, ShaderNodes.MASTER_MODES);
            return true;
        }
        if (key.equals(ShaderNodes.MATERIAL_SAMPLER_SETTING)) {
            renderEnumSetting(graph, node, setting, ShaderNodes.MATERIAL_SAMPLERS);
            return true;
        }
        if (isCustomTypeSetting(key)) {
            renderEnumSetting(graph, node, setting, ShaderNodes.CUSTOM_VALUE_TYPES);
            return true;
        }
        if (key.equals(ShaderNodes.INPUT_COUNT_SETTING)) {
            renderCustomInputCountSetting(graph, node, setting);
            return true;
        }
        if (key.equals(ShaderNodes.CODE_SETTING)) {
            renderCodeSetting(graph, node, setting);
            return true;
        }
        return false;
    }

    private static boolean isCustomTypeSetting(String key) {
        if (key.equals(ShaderNodes.OUTPUT_TYPE_SETTING)) {
            return true;
        }
        for (int index = 0; index < ShaderNodes.CUSTOM_CODE_MAX_INPUTS; index++) {
            if (key.equals(ShaderNodes.customInputTypeSetting(index))) {
                return true;
            }
        }
        return false;
    }

    private void renderCustomInputCountSetting(OpenGraph graph, GraphNode node, NodeSetting setting) {
        int[] value = {GraphValues.asInt(node.values().getOrDefault(setting.key(), setting.defaultValue()))};
        ImGui.setNextItemWidth(LITERAL_WIDTH);
        if (ImGui.dragInt("##setting-" + setting.key(), value, 0.1f, 0, ShaderNodes.CUSTOM_CODE_MAX_INPUTS)) {
            node.values().put(setting.key(),
                    Math.clamp(value[0], 0, ShaderNodes.CUSTOM_CODE_MAX_INPUTS));
            graph.dirty = true;
        }
    }

    private void renderCodeSetting(OpenGraph graph, GraphNode node, NodeSetting setting) {
        String bufferKey = "setting-" + node.id() + "-" + setting.key();
        String current = GraphValues.asString(node.values().getOrDefault(setting.key(), setting.defaultValue()));
        ImString buffer = graph.largeTextBuffer(bufferKey, current);
        if (ImGui.inputTextMultiline("##setting-" + setting.key(), buffer,
                CUSTOM_CODE_WIDTH, CUSTOM_CODE_HEIGHT)) {
            node.values().put(setting.key(), buffer.get().replace("\0", ""));
            graph.dirty = true;
        }
    }

    private static List<String> keyNames() {
        List<String> names = new ArrayList<>();
        for (KeyCode key : KeyCode.values()) {
            names.add(key.name());
        }
        return names;
    }

    private static List<String> mouseButtonNames() {
        List<String> names = new ArrayList<>();
        for (MouseButton button : MouseButton.values()) {
            names.add(button.name());
        }
        return names;
    }

    private static List<String> variableNames(OpenGraph graph) {
        List<String> names = new ArrayList<>();
        for (GraphVariable variable : graph.asset.variables()) {
            names.add(variable.name());
        }
        return names;
    }

    private void renderEnumSetting(OpenGraph graph, GraphNode node, NodeSetting setting,
                                   List<String> options) {
        String current = GraphValues.asString(node.values()
                .getOrDefault(setting.key(), setting.defaultValue()));
        ImGui.setNextItemWidth(LITERAL_WIDTH);
        if (!ImGui.beginCombo("##setting-" + setting.key(), current)) {
            return;
        }
        for (String option : options) {
            if (ImGui.selectable(option, option.equals(current))) {
                node.values().put(setting.key(), option);
                graph.dirty = true;
            }
        }
        ImGui.endCombo();
    }

    private void renderWholeNumberSetting(OpenGraph graph, GraphNode node, NodeSetting setting) {
        int[] value = {GraphValues.asInt(node.values().getOrDefault(setting.key(), setting.defaultValue()))};
        ImGui.setNextItemWidth(LITERAL_WIDTH);
        if (ImGui.dragInt("##setting-" + setting.key(), value, 0.1f, 1, 16)) {
            node.values().put(setting.key(), Math.max(1, value[0]));
            graph.dirty = true;
        }
    }

    private void renderNumberSetting(OpenGraph graph, GraphNode node, NodeSetting setting) {
        float[] value = {GraphValues.asFloat(node.values().getOrDefault(setting.key(), setting.defaultValue()))};
        ImGui.setNextItemWidth(LITERAL_WIDTH);
        if (ImGui.dragFloat("##setting-" + setting.key(), value, 0.05f)) {
            node.values().put(setting.key(), value[0]);
            graph.dirty = true;
        }
    }

    private void renderToggleSetting(OpenGraph graph, GraphNode node, NodeSetting setting) {
        boolean value = GraphValues.asBoolean(node.values().getOrDefault(setting.key(), setting.defaultValue()));
        if (ImGui.checkbox("##setting-" + setting.key(), value)) {
            node.values().put(setting.key(), !value);
            graph.dirty = true;
        }
    }

    private void renderTextSetting(OpenGraph graph, GraphNode node, NodeSetting setting) {
        String bufferKey = "setting-" + node.id() + "-" + setting.key();
        String current = GraphValues.asString(node.values().getOrDefault(setting.key(), setting.defaultValue()));
        ImString buffer = graph.textBuffer(bufferKey, current);
        ImGui.setNextItemWidth(VECTOR_LITERAL_WIDTH);
        if (ImGui.inputText("##setting-" + setting.key(), buffer)) {
            node.values().put(setting.key(), buffer.get().replace("\0", ""));
            graph.dirty = true;
        }
        acceptPathDrop(graph, node, setting);
    }

    private void acceptPathDrop(OpenGraph graph, GraphNode node, NodeSetting setting) {
        if (setting.kind() != SettingKind.ASSET_PATH || !ImGui.beginDragDropTarget()) {
            return;
        }
        String dropped = ImGui.acceptDragDropPayload(AssetMimeTypes.PREFAB, String.class);
        if (dropped == null) {
            dropped = ImGui.acceptDragDropPayload(AssetMimeTypes.GRAPH, String.class);
        }
        if (dropped == null) {
            dropped = ImGui.acceptDragDropPayload(AssetMimeTypes.TEXTURE, String.class);
        }
        if (dropped != null) {
            node.values().put(setting.key(), dropped);
            graph.clearTextBuffer("setting-" + node.id() + "-" + setting.key());
            graph.dirty = true;
        }
        ImGui.endDragDropTarget();
    }

    private void renderInputPins(OpenGraph graph, GraphNode node) {
        List<PinDefinition> pins = registry.effectiveInputPins(graph.asset, node);
        for (int index = 0; index < pins.size(); index++) {
            PinDefinition pin = pins.get(index);
            int attributeId = node.id() * PIN_STRIDE + index;
            graph.pinsById.put(attributeId, new PinReference(node, pin, false));
            ImNodes.pushColorStyle(ImNodesCol.Pin, colorFor(pin.type()));
            ImNodes.beginInputAttribute(attributeId, shapeFor(pin.type()));
            renderInputPinContent(graph, node, pin);
            ImNodes.endInputAttribute();
            ImNodes.popColorStyle();
        }
    }

    private void renderInputPinContent(OpenGraph graph, GraphNode node, PinDefinition pin) {
        ImGui.textUnformatted(pin.name());
        if (pin.type() == PinType.EXEC || graph.asset.edgeInto(node.id(), pin.name()).isPresent()) {
            return;
        }
        renderPinLiteral(graph, node, pin);
    }

    private void renderPinLiteral(OpenGraph graph, GraphNode node, PinDefinition pin) {
        if (pin.type() == PinType.GAME_OBJECT || pin.type() == PinType.OBJECT
                || ShaderNodes.defaultsToUv(node.typeKey(), pin.name())
                || ShaderNodes.isOutput(node.typeKey())) {
            return;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(literalWidthFor(pin.type()));
        String bufferKey = "literal-" + node.id() + "-" + pin.name();
        Object current = node.values().containsKey(pin.name())
                ? node.values().get(pin.name())
                : ShaderNodes.defaultPinValue(node.typeKey(), pin);
        renderLiteralWidget(graph, node, pin, bufferKey, current)
                .ifPresent(value -> {
                    node.values().put(pin.name(), value);
                    graph.dirty = true;
                });
    }

    private Optional<Object> renderLiteralWidget(OpenGraph graph, GraphNode node, PinDefinition pin,
                                                 String bufferKey, Object current) {
        String label = "##literal-" + pin.name();
        if (isColorValuePin(node, pin)) {
            return renderColorWidget(label, current);
        }
        return renderValueWidget(label, bufferKey, pin.type(), current, graph);
    }

    private static boolean isColorValuePin(GraphNode node, PinDefinition pin) {
        return pin.name().equals(ShaderNodes.VALUE_PIN)
                && (node.typeKey().equals(ShaderNodes.CONSTANT_COLOR)
                || node.typeKey().equals(ShaderNodes.PARAMETER_COLOR));
    }

    private static float literalWidthFor(PinType type) {
        return switch (type) {
            case VECTOR2, VECTOR3, VECTOR4 -> VECTOR_LITERAL_WIDTH;
            default -> LITERAL_WIDTH;
        };
    }

    private void renderOutputPins(OpenGraph graph, GraphNode node) {
        List<PinDefinition> pins = registry.effectiveOutputPins(graph.asset, node);
        for (int index = 0; index < pins.size(); index++) {
            PinDefinition pin = pins.get(index);
            int attributeId = node.id() * PIN_STRIDE + OUTPUT_SLOT_OFFSET + index;
            graph.pinsById.put(attributeId, new PinReference(node, pin, true));
            ImNodes.pushColorStyle(ImNodesCol.Pin, colorFor(pin.type()));
            ImNodes.beginOutputAttribute(attributeId, shapeFor(pin.type()));
            ImGui.indent(LITERAL_WIDTH);
            ImGui.textUnformatted(pin.name());
            ImGui.unindent(LITERAL_WIDTH);
            ImNodes.endOutputAttribute();
            ImNodes.popColorStyle();
        }
    }

    private void renderLinks(OpenGraph graph, Optional<GraphInstance> debugInstance) {
        List<GraphEdge> edges = graph.asset.edges();
        for (int index = 0; index < edges.size(); index++) {
            GraphEdge edge = edges.get(index);
            int fromId = attributeIdFor(graph, edge.fromNode(), edge.fromPin(), true);
            int toId = attributeIdFor(graph, edge.toNode(), edge.toPin(), false);
            if (fromId < 0 || toId < 0) {
                continue;
            }
            renderLink(graph, debugInstance, edge, index, fromId, toId);
        }
    }

    private void renderLink(OpenGraph graph, Optional<GraphInstance> debugInstance,
                            GraphEdge edge, int linkId, int fromId, int toId) {
        boolean flash = debugInstance.map(instance -> recentlyFired(instance.edgeFireNanos(edge)))
                .orElse(false);
        PinReference source = graph.pinsById.get(fromId);
        int color = flash ? COLOR_FLASH : colorFor(source.pin().type());
        ImNodes.pushColorStyle(ImNodesCol.Link, color);
        ImNodes.link(linkId, fromId, toId);
        ImNodes.popColorStyle();
    }

    private int attributeIdFor(OpenGraph graph, int nodeId, String pinName, boolean output) {
        Optional<GraphNode> node = graph.asset.findNode(nodeId);
        if (node.isEmpty()) {
            return -1;
        }
        List<PinDefinition> pins = output
                ? registry.effectiveOutputPins(graph.asset, node.get())
                : registry.effectiveInputPins(graph.asset, node.get());
        for (int index = 0; index < pins.size(); index++) {
            if (pins.get(index).name().equals(pinName)) {
                return nodeId * PIN_STRIDE + (output ? OUTPUT_SLOT_OFFSET : 0) + index;
            }
        }
        return -1;
    }

    private void syncNodePositions(OpenGraph graph) {
        for (GraphNode node : graph.asset.nodes()) {
            if (!graph.placedNodes.contains(node.id())) {
                continue;
            }
            float x = ImNodes.getNodeGridSpacePosX(node.id());
            float y = ImNodes.getNodeGridSpacePosY(node.id());
            if (Math.abs(x - node.positionX()) > 0.01f || Math.abs(y - node.positionY()) > 0.01f) {
                node.setPosition(x, y);
                graph.dirty = true;
            }
        }
    }

    private void handleLinkCreated(OpenGraph graph) {
        ImInt startAttribute = new ImInt();
        ImInt endAttribute = new ImInt();
        if (!ImNodes.isLinkCreated(startAttribute, endAttribute)) {
            return;
        }
        PinReference first = graph.pinsById.get(startAttribute.get());
        PinReference second = graph.pinsById.get(endAttribute.get());
        if (first == null || second == null || first.output() == second.output()) {
            return;
        }
        PinReference output = first.output() ? first : second;
        PinReference input = first.output() ? second : first;
        connect(graph, output, input);
    }

    private void connect(OpenGraph graph, PinReference output, PinReference input) {
        if (isStateToStateDrag(output, input)) {
            insertTransitionBetween(graph, output.node(), input.node());
            return;
        }
        if (!input.pin().type().acceptsFrom(output.pin().type())) {
            notifier.show("Incompatible pin types: " + output.pin().type() + " -> " + input.pin().type());
            return;
        }
        if (output.pin().type() == PinType.EXEC && !isTransitionsPin(output)) {
            graph.asset.edges().removeIf(edge -> edge.fromNode() == output.node().id()
                    && edge.fromPin().equals(output.pin().name()));
        } else {
            graph.asset.edges().removeIf(edge -> edge.toNode() == input.node().id()
                    && edge.toPin().equals(input.pin().name()));
        }
        graph.asset.edges().add(new GraphEdge(output.node().id(), output.pin().name(),
                input.node().id(), input.pin().name()));
        graph.dirty = true;
    }

    private static boolean isStateToStateDrag(PinReference output, PinReference input) {
        return isTransitionsPin(output)
                && StateNodes.isState(input.node())
                && input.pin().name().equals(StateNodes.STATE_IN_PIN);
    }

    private static boolean isTransitionsPin(PinReference output) {
        return StateNodes.isState(output.node())
                && output.pin().name().equals(StateNodes.TRANSITIONS_PIN);
    }

    private void insertTransitionBetween(OpenGraph graph, GraphNode from, GraphNode to) {
        float x = (from.positionX() + to.positionX()) / 2.0f + (from == to ? SELF_TRANSITION_OFFSET : 0.0f);
        float y = (from.positionY() + to.positionY()) / 2.0f + (from == to ? SELF_TRANSITION_OFFSET : 0.0f);
        GraphNode transition = graph.asset.addNode(StateNodes.TRANSITION, x, y);
        applyDefaultSettings(transition);
        ImNodes.setNodeGridSpacePos(transition.id(), x, y);
        graph.placedNodes.add(transition.id());
        graph.asset.edges().add(new GraphEdge(from.id(), StateNodes.TRANSITIONS_PIN,
                transition.id(), StateNodes.FROM_PIN));
        graph.asset.edges().add(new GraphEdge(transition.id(), StateNodes.TO_PIN,
                to.id(), StateNodes.STATE_IN_PIN));
        graph.dirty = true;
    }

    private void handleLinkDestroyed(OpenGraph graph) {
        ImInt linkId = new ImInt();
        if (ImNodes.isLinkDestroyed(linkId)
                && linkId.get() >= 0 && linkId.get() < graph.asset.edges().size()) {
            graph.asset.edges().remove(linkId.get());
            graph.dirty = true;
        }
    }

    private void handleDeletions(OpenGraph graph) {
        if (!ImGui.isKeyPressed(ImGuiKey.Delete)
                || !ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)) {
            return;
        }
        deleteSelectedLinks(graph);
        deleteSelectedNodes(graph);
    }

    private void deleteSelectedLinks(OpenGraph graph) {
        int count = ImNodes.numSelectedLinks();
        if (count <= 0) {
            return;
        }
        int[] selected = new int[count];
        ImNodes.getSelectedLinks(selected);
        List<GraphEdge> removed = new ArrayList<>();
        for (int linkId : selected) {
            if (linkId >= 0 && linkId < graph.asset.edges().size()) {
                removed.add(graph.asset.edges().get(linkId));
            }
        }
        graph.asset.edges().removeAll(removed);
        markDirtyAndClearLinkSelection(graph, !removed.isEmpty());
    }

    private static void markDirtyAndClearLinkSelection(OpenGraph graph, boolean changed) {
        if (changed) {
            graph.dirty = true;
        }
        ImNodes.clearLinkSelection();
    }

    private void deleteSelectedNodes(OpenGraph graph) {
        int count = ImNodes.numSelectedNodes();
        if (count <= 0) {
            return;
        }
        int[] selected = new int[count];
        ImNodes.getSelectedNodes(selected);
        for (int nodeId : selected) {
            if (isProtectedOutputNode(graph, nodeId)) {
                notifier.show("The output node cannot be deleted");
                continue;
            }
            graph.asset.removeNode(nodeId);
            graph.placedNodes.remove(nodeId);
        }
        graph.dirty = true;
        ImNodes.clearNodeSelection();
    }

    private static boolean isProtectedOutputNode(OpenGraph graph, int nodeId) {
        return graph.asset.kind().isShader() && graph.asset.findNode(nodeId)
                .map(node -> ShaderNodes.isOutput(node.typeKey()))
                .orElse(false);
    }

    private void handleContextMenu() {
        boolean rightClicked = ImGui.isWindowHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Right);
        if (rightClicked || nodeSearchRequested) {
            popupSpawnX = rightClicked ? ImGui.getMousePosX()
                    : ImGui.getWindowPosX() + ImGui.getWindowWidth() * 0.4f;
            popupSpawnY = rightClicked ? ImGui.getMousePosY()
                    : ImGui.getWindowPosY() + ImGui.getWindowHeight() * 0.3f;
            nodeSearch.set("");
            ImGui.openPopup(NODE_SEARCH_POPUP);
            nodeSearchRequested = false;
        }
    }

    private void renderNodeSearchPopup(OpenGraph graph) {
        if (!ImGui.beginPopup(NODE_SEARCH_POPUP)) {
            return;
        }
        ImGui.inputTextWithHint("##graph-node-filter", "Search nodes", nodeSearch);
        ImGui.separator();
        ImGui.beginChild("##graph-node-list", 320.0f, 360.0f, false);
        String query = nodeSearch.get().toLowerCase(Locale.ROOT).replace("\0", "");
        if (!graph.asset.kind().isShader()) {
            renderVariableNodeOptions(graph, query);
        }
        renderCategorizedNodeOptions(graph, query);
        ImGui.endChild();
        ImGui.endPopup();
    }

    private void renderVariableNodeOptions(OpenGraph graph, String query) {
        for (GraphVariable variable : new ArrayList<>(graph.asset.variables())) {
            renderVariableNodeOption(graph, query, "Get " + variable.name(),
                    BuiltinNodes.VARIABLE_GET, variable.name());
            renderVariableNodeOption(graph, query, "Set " + variable.name(),
                    BuiltinNodes.VARIABLE_SET, variable.name());
        }
    }

    private void renderVariableNodeOption(OpenGraph graph, String query, String label,
                                          String typeKey, String variableName) {
        String fullLabel = "Variables / " + label;
        if (!query.isEmpty() && !fullLabel.toLowerCase(Locale.ROOT).contains(query)) {
            return;
        }
        if (ImGui.selectable(fullLabel)) {
            GraphNode created = spawnNode(graph, typeKey);
            created.values().put(BuiltinNodes.VARIABLE_NAME_SETTING, variableName);
            ImGui.closeCurrentPopup();
        }
    }

    private void renderCategorizedNodeOptions(OpenGraph graph, String query) {
        for (NodeDefinition definition : registry.all()) {
            if (definition.typeKey().equals(BuiltinNodes.VARIABLE_GET)
                    || definition.typeKey().equals(BuiltinNodes.VARIABLE_SET)) {
                continue;
            }
            if (!allowedInKind(graph.asset.kind(), definition)) {
                continue;
            }
            renderNodeOption(graph, query, definition);
        }
    }

    private static boolean allowedInKind(GraphKind kind, NodeDefinition definition) {
        String typeKey = definition.typeKey();
        if (kind == GraphKind.SHADER_SURFACE) {
            return ShaderNodes.isShaderNode(typeKey) && !ShaderNodes.isPostOnly(typeKey);
        }
        if (kind == GraphKind.SHADER_POST) {
            return ShaderNodes.isShaderNode(typeKey) && !ShaderNodes.isSurfaceOnly(typeKey);
        }
        if (ShaderNodes.isShaderNode(typeKey)) {
            return false;
        }
        return kind != GraphKind.LOGIC || !definition.category().equals(StateNodes.CATEGORY);
    }

    private void renderNodeOption(OpenGraph graph, String query, NodeDefinition definition) {
        String label = definition.category() + " / " + definition.displayName();
        if (!query.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(query)) {
            return;
        }
        if (ImGui.selectable(label)) {
            spawnNode(graph, definition.typeKey());
            ImGui.closeCurrentPopup();
        }
    }

    private GraphNode spawnNode(OpenGraph graph, String typeKey) {
        GraphNode node = graph.asset.addNode(typeKey, 0.0f, 0.0f);
        applyDefaultSettings(node);
        applyDefaultPinValues(graph, node);
        ImNodes.setNodeScreenSpacePos(node.id(), popupSpawnX, popupSpawnY);
        graph.placedNodes.add(node.id());
        graph.dirty = true;
        return node;
    }

    private void applyDefaultSettings(GraphNode node) {
        registry.find(node.typeKey()).ifPresent(definition -> {
            for (NodeSetting setting : definition.settings()) {
                node.values().put(setting.key(), setting.defaultValue());
            }
        });
    }

    private void applyDefaultPinValues(OpenGraph graph, GraphNode node) {
        if (!ShaderNodes.isShaderNode(node.typeKey())) {
            return;
        }
        for (PinDefinition pin : registry.effectiveInputPins(graph.asset, node)) {
            if (pin.type().isShaderValue() && !ShaderNodes.defaultsToUv(node.typeKey(), pin.name())
                    && !ShaderNodes.isOutput(node.typeKey())) {
                node.values().put(pin.name(), ShaderNodes.defaultPinValue(node.typeKey(), pin));
            }
        }
    }

    private static int colorFor(PinType type) {
        return switch (type) {
            case EXEC -> COLOR_EXEC;
            case FLOAT -> COLOR_FLOAT;
            case INT -> COLOR_INT;
            case BOOLEAN -> COLOR_BOOLEAN;
            case STRING -> COLOR_STRING;
            case VECTOR2 -> COLOR_VECTOR2;
            case VECTOR3 -> COLOR_VECTOR3;
            case VECTOR4 -> COLOR_VECTOR4;
            case NUMERIC -> COLOR_NUMERIC;
            case GAME_OBJECT -> COLOR_GAME_OBJECT;
            case OBJECT -> COLOR_OBJECT;
        };
    }

    private static int shapeFor(PinType type) {
        return type == PinType.EXEC ? ImNodesPinShape.TriangleFilled : ImNodesPinShape.CircleFilled;
    }

    private record PinReference(GraphNode node, PinDefinition pin, boolean output) {
    }

    private static final class OpenGraph {

        final GraphAsset asset;
        final Map<Integer, PinReference> pinsById = new HashMap<>();
        final Set<Integer> placedNodes = new HashSet<>();
        final Set<Integer> collapsedPreviews = new HashSet<>();
        final Map<String, ImString> textBuffers = new HashMap<>();
        boolean dirty;
        Path graphPath = Path.of("");

        OpenGraph(GraphAsset asset) {
            this.asset = asset;
        }

        ImString textBuffer(String key, String initial) {
            return textBuffers.computeIfAbsent(key, ignored -> new ImString(initial, 256));
        }

        ImString largeTextBuffer(String key, String initial) {
            return textBuffers.computeIfAbsent(key, ignored -> new ImString(initial, 8192));
        }

        void clearTextBuffer(String key) {
            textBuffers.remove(key);
        }
    }
}
