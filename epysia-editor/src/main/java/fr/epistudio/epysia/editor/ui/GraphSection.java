package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphComponent;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.GraphValues;
import fr.epistudio.epysia.graph.GraphVariable;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;
import imgui.type.ImString;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class GraphSection {

    private final Supplier<SceneDocument> activeDocument;
    private final Consumer<Path> onOpenGraph;
    private final GraphJsonCodec codec = new GraphJsonCodec();
    private final Map<String, ImString> textBuffers = new HashMap<>();
    private String cachedPath = "";
    private long cachedModifiedMillis;
    private GraphAsset cachedAsset = new GraphAsset();

    public GraphSection(Supplier<SceneDocument> activeDocument, Consumer<Path> onOpenGraph) {
        this.activeDocument = activeDocument;
        this.onOpenGraph = onOpenGraph;
    }

    public void render(GraphComponent component) {
        ImGui.spacing();
        renderPathRow(component);
        renderOpenButton(component);
        renderExposedVariables(component);
    }

    private void renderPathRow(GraphComponent component) {
        String fileName = component.graphPath().isEmpty()
                ? I18n.translate(TextKey.EDITOR_GRAPH_SECTION_DROP_HINT)
                : Path.of(component.graphPath()).getFileName().toString();
        Texts.muted(I18n.translate(TextKey.EDITOR_GRAPH_SECTION_GRAPH));
        ImGui.sameLine();
        ImGui.button(fileName, ImGui.getContentRegionAvailX(), 0.0f);
        acceptGraphDrop(component);
    }

    private void acceptGraphDrop(GraphComponent component) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        String dropped = ImGui.acceptDragDropPayload(AssetMimeTypes.GRAPH, String.class);
        if (dropped != null) {
            component.setGraphPath(dropped);
            activeDocument.get().markDirty();
        }
        ImGui.endDragDropTarget();
    }

    private void renderOpenButton(GraphComponent component) {
        ImGui.beginDisabled(component.graphPath().isEmpty());
        if (ImGui.button(I18n.label(TextKey.EDITOR_GRAPH_SECTION_OPEN_IN_GRAPH_EDITOR,
                "graph-section-open"), ImGui.getContentRegionAvailX(), 0.0f)) {
            onOpenGraph.accept(Path.of(component.graphPath()));
        }
        ImGui.endDisabled();
    }

    private void renderExposedVariables(GraphComponent component) {
        Optional<GraphAsset> asset = assetFor(component.graphPath());
        if (asset.isEmpty()) {
            return;
        }
        boolean headerShown = false;
        for (GraphVariable variable : asset.get().variables()) {
            if (!variable.exposed()) {
                continue;
            }
            headerShown = showHeaderOnce(headerShown);
            renderOverrideRow(component, variable);
        }
    }

    private static boolean showHeaderOnce(boolean headerShown) {
        if (!headerShown) {
            ImGui.spacing();
            Texts.muted(I18n.translate(TextKey.EDITOR_GRAPH_SECTION_EXPOSED_VARIABLES));
            ImGui.separator();
        }
        return true;
    }

    private void renderOverrideRow(GraphComponent component, GraphVariable variable) {
        ImGui.pushID("graph-override-" + variable.name());
        boolean overridden = component.variableOverrides().containsKey(variable.name());
        Object current = overridden
                ? component.variableOverrides().get(variable.name())
                : variable.defaultValue();
        ImGui.textUnformatted(variable.name());
        ImGui.sameLine(120.0f);
        renderOverrideWidget(component, variable, current);
        renderResetButton(component, variable, overridden);
        ImGui.popID();
    }

    private void renderOverrideWidget(GraphComponent component, GraphVariable variable, Object current) {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 30.0f);
        Optional<Object> edited = renderValueWidget(variable, current);
        edited.ifPresent(value -> {
            component.variableOverrides().put(variable.name(), value);
            activeDocument.get().markDirty();
        });
    }

    private void renderResetButton(GraphComponent component, GraphVariable variable, boolean overridden) {
        if (!overridden) {
            return;
        }
        ImGui.sameLine();
        if (ImGui.smallButton(I18n.label(TextKey.EDITOR_GRAPH_SECTION_RESET,
                "graph-section-reset-" + variable.name()))) {
            component.variableOverrides().remove(variable.name());
            textBuffers.remove(variable.name());
            activeDocument.get().markDirty();
        }
    }

    private Optional<Object> renderValueWidget(GraphVariable variable, Object current) {
        return switch (variable.type()) {
            case FLOAT -> floatWidget(current);
            case INT -> intWidget(current);
            case BOOLEAN -> booleanWidget(current);
            case STRING -> stringWidget(variable.name(), current);
            case VECTOR3 -> vectorWidget(current);
            case EXEC, VECTOR2, VECTOR4, NUMERIC, GAME_OBJECT, OBJECT -> Optional.empty();
        };
    }

    private static Optional<Object> floatWidget(Object current) {
        float[] value = {GraphValues.asFloat(current)};
        return ImGui.dragFloat("##value", value, 0.05f) ? Optional.of(value[0]) : Optional.empty();
    }

    private static Optional<Object> intWidget(Object current) {
        int[] value = {GraphValues.asInt(current)};
        return ImGui.dragInt("##value", value) ? Optional.of(value[0]) : Optional.empty();
    }

    private static Optional<Object> booleanWidget(Object current) {
        boolean value = GraphValues.asBoolean(current);
        return ImGui.checkbox("##value", value) ? Optional.of(!value) : Optional.empty();
    }

    private Optional<Object> stringWidget(String name, Object current) {
        ImString buffer = textBuffers.computeIfAbsent(name,
                ignored -> new ImString(GraphValues.asString(current), 256));
        if (ImGui.inputText("##value", buffer)) {
            return Optional.of(buffer.get().replace("\0", ""));
        }
        return Optional.empty();
    }

    private static Optional<Object> vectorWidget(Object current) {
        Vector3f vector = GraphValues.asVector(current);
        float[] values = {vector.x, vector.y, vector.z};
        if (ImGui.dragFloat3("##value", values, 0.05f)) {
            return Optional.of(new Vector3f(values[0], values[1], values[2]));
        }
        return Optional.empty();
    }

    private Optional<GraphAsset> assetFor(String pathText) {
        if (pathText.isEmpty()) {
            return Optional.empty();
        }
        Path path = Path.of(pathText);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        reloadIfChanged(pathText, path);
        return Optional.of(cachedAsset);
    }

    private void reloadIfChanged(String pathText, Path path) {
        long modifiedMillis = lastModifiedMillis(path);
        if (pathText.equals(cachedPath) && modifiedMillis == cachedModifiedMillis) {
            return;
        }
        try {
            cachedAsset = codec.readFromFile(path);
            cachedPath = pathText;
            cachedModifiedMillis = modifiedMillis;
        } catch (IOException | RuntimeException ignored) {
            cachedAsset = new GraphAsset();
        }
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException error) {
            return 0L;
        }
    }
}
