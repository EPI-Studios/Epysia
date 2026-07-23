package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.SetPropertyCommand;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.inspector.EulerCache;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ExportedProperty;
import imgui.ImGui;
import imgui.type.ImString;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class PropertyRows {

    private static final float DRAG_STEP_FALLBACK = 0.05f;
    private static final float NUMERIC_RANGE_FALLBACK = 1_000_000.0f;
    private static final int STRING_CAPACITY = 512;
    private static final String NONE_LABEL = "None";

    private final Supplier<SceneDocument> activeDocument;
    private final AssetPicker assetPicker;
    private final Map<String, EulerCache> eulerCaches = new HashMap<>();
    private final Map<String, ImString> stringBuffers = new HashMap<>();
    private final Set<String> seenKeysThisFrame = new HashSet<>();

    public PropertyRows(Supplier<SceneDocument> activeDocument, AssetPicker assetPicker) {
        this.activeDocument = activeDocument;
        this.assetPicker = assetPicker;
    }

    private EditorHistory history() {
        return activeDocument.get().history();
    }

    public void beginFrame() {
        seenKeysThisFrame.clear();
    }

    public void pruneStaleKeys() {
        eulerCaches.keySet().removeIf(key -> !seenKeysThisFrame.contains(key));
        stringBuffers.keySet().removeIf(key -> !seenKeysThisFrame.contains(key));
    }

    public void renderProperty(IComponent owner, ExportedProperty property, String key) {
        ImGui.pushID(key);
        switch (property.kind()) {
            case FLOAT -> renderFloat(owner, property);
            case INT -> renderInt(owner, property);
            case BOOLEAN -> renderBoolean(owner, property);
            case STRING -> renderString(owner, property, key);
            case ENUM -> renderEnum(owner, property);
            case VECTOR2 -> renderVector2(owner, property);
            case VECTOR3 -> renderVector3(owner, property);
            case QUATERNION -> renderQuaternion(owner, property, key);
            case ASSET_REF -> renderAssetRef(owner, property, key);
            case GAMEOBJECT_REF -> renderGameObjectRef(owner, property);
            default -> ImGui.labelText(property.label(), "(unsupported)");
        }
        ImGui.popID();
    }

    private void renderFloat(IComponent owner, ExportedProperty property) {
        float current = ((Number) property.read()).floatValue();
        float[] value = {current};
        float step = property.step() > 0.0f ? property.step() : DRAG_STEP_FALLBACK;
        if (ImGui.dragFloat(property.label(), value, step, boundedMin(property), boundedMax(property))
                && Float.compare(value[0], current) != 0) {
            history().execute(new SetPropertyCommand(owner, property, current, value[0]));
        }
    }

    private void renderInt(IComponent owner, ExportedProperty property) {
        int current = ((Number) property.read()).intValue();
        int[] value = {current};
        if (ImGui.dragInt(property.label(), value, 1.0f, (int) boundedMin(property), (int) boundedMax(property))
                && value[0] != current) {
            history().execute(new SetPropertyCommand(owner, property, current, value[0]));
        }
    }

    private void renderBoolean(IComponent owner, ExportedProperty property) {
        boolean current = (boolean) property.read();
        if (ImGui.checkbox(property.label(), current)) {
            history().execute(new SetPropertyCommand(owner, property, current, !current));
        }
    }

    private void renderString(IComponent owner, ExportedProperty property, String key) {
        String current = String.valueOf(property.read());
        ImString buffer = stringBuffer(key, current);
        if (ImGui.inputText(property.label(), buffer) && !buffer.get().equals(current)) {
            history().execute(new SetPropertyCommand(owner, property, current, buffer.get()));
        }
    }

    private void renderEnum(IComponent owner, ExportedProperty property) {
        Object current = property.read();
        String label = current == null ? "(none)" : current.toString();
        if (!ImGui.beginCombo(property.label(), label)) {
            return;
        }
        for (Object candidate : property.enumConstants()) {
            if (ImGui.selectable(candidate.toString(), candidate == current) && candidate != current) {
                history().execute(new SetPropertyCommand(owner, property, current, candidate));
            }
        }
        ImGui.endCombo();
    }

    private void renderVector2(IComponent owner, ExportedProperty property) {
        Vector2f vector = (Vector2f) property.read();
        float[] values = {vector.x, vector.y};
        if (ImGui.dragFloat2(property.label(), values, DRAG_STEP_FALLBACK)
                && (vector.x != values[0] || vector.y != values[1])) {
            Vector2f before = new Vector2f(vector);
            Vector2f after = new Vector2f(values[0], values[1]);
            history().execute(new SetPropertyCommand(owner, property, before, after));
        }
    }

    private void renderVector3(IComponent owner, ExportedProperty property) {
        Vector3f vector = (Vector3f) property.read();
        float[] values = {vector.x, vector.y, vector.z};
        if (ImGui.dragFloat3(property.label(), values, DRAG_STEP_FALLBACK)
                && (vector.x != values[0] || vector.y != values[1] || vector.z != values[2])) {
            Vector3f before = new Vector3f(vector);
            Vector3f after = new Vector3f(values[0], values[1], values[2]);
            history().execute(new SetPropertyCommand(owner, property, before, after));
        }
    }

    private void renderQuaternion(IComponent owner, ExportedProperty property, String key) {
        Quaternionf quaternion = (Quaternionf) property.read();
        EulerCache cache = eulerCache(key);
        cache.refreshFromIfChanged(quaternion, ImGui.isAnyItemActive() && ImGui.isWindowFocused());
        Vector3f degrees = cache.degrees();
        float[] values = {degrees.x, degrees.y, degrees.z};
        if (ImGui.dragFloat3(property.label(), values, 0.5f)) {
            commitEuler(owner, property, quaternion, cache, values);
        }
    }

    private void commitEuler(IComponent owner, ExportedProperty property, Quaternionf quaternion,
                             EulerCache cache, float[] values) {
        Quaternionf before = new Quaternionf(quaternion);
        Quaternionf after = new Quaternionf();
        cache.applyEulerToQuaternion(values[0], values[1], values[2], after);
        if (!before.equals(after)) {
            history().execute(new SetPropertyCommand(owner, property, before, new Quaternionf(after)));
        }
    }

    private void renderAssetRef(IComponent owner, ExportedProperty property, String key) {
        AssetRef<?> reference = (AssetRef<?>) property.read();
        if (reference == null) {
            ImGui.labelText(property.label(), "(missing reference)");
            return;
        }
        ImString buffer = stringBuffer(key, reference.path());
        renderAssetRefField(owner, property, reference, buffer);
    }

    private void renderAssetRefField(IComponent owner, ExportedProperty property,
                                     AssetRef<?> reference, ImString buffer) {
        float pickerWidth = ImGui.getFrameHeight();
        ImGui.setNextItemWidth(ImGui.calcItemWidth() - pickerWidth - ImGui.getStyle().getItemInnerSpacingX());
        if (ImGui.inputText("##asset-path", buffer) && !buffer.get().equals(reference.path())) {
            history().execute(new SetPropertyCommand(owner, property, reference.path(), buffer.get()));
        }
        acceptAssetDrop(owner, property, reference, buffer);
        ImGui.sameLine();
        if (ImGui.button("…", pickerWidth, 0.0f)) {
            assetPicker.open(reference.type(), path ->
                    applyAssetPath(owner, property, reference, buffer, path));
        }
        ImGui.sameLine();
        ImGui.textUnformatted(property.label());
    }

    private void acceptAssetDrop(IComponent owner, ExportedProperty property,
                                 AssetRef<?> reference, ImString buffer) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        String acceptedType = AssetMimeTypes.forAssetType(reference.type());
        if (!acceptedType.isEmpty()) {
            String droppedPath = ImGui.acceptDragDropPayload(acceptedType, String.class);
            if (droppedPath != null) {
                applyAssetPath(owner, property, reference, buffer, droppedPath);
            }
        }
        ImGui.endDragDropTarget();
    }

    private void applyAssetPath(IComponent owner, ExportedProperty property,
                                AssetRef<?> reference, ImString buffer, String path) {
        history().execute(new SetPropertyCommand(owner, property, reference.path(), path));
        buffer.set(path);
    }

    private void renderGameObjectRef(IComponent owner, ExportedProperty property) {
        Object current = property.read();
        String label = current instanceof GameObject target ? target.name() : NONE_LABEL;
        renderGameObjectCombo(owner, property, current, label);
        acceptGameObjectDrop(owner, property);
        ImGui.sameLine();
        ImGui.textUnformatted(property.label());
    }

    private void renderGameObjectCombo(IComponent owner, ExportedProperty property,
                                       Object current, String label) {
        if (!ImGui.beginCombo("##gameobject-ref", label)) {
            return;
        }
        if (ImGui.selectable(NONE_LABEL, current == null) && current != null) {
            history().execute(new SetPropertyCommand(owner, property, current, null));
        }
        for (GameObject candidate : activeDocument.get().scene().gameObjects()) {
            renderGameObjectOption(owner, property, current, candidate);
        }
        ImGui.endCombo();
    }

    private void renderGameObjectOption(IComponent owner, ExportedProperty property,
                                        Object current, GameObject candidate) {
        ImGui.pushID(candidate.id().toString());
        if (ImGui.selectable(candidate.name(), candidate == current) && candidate != current) {
            history().execute(new SetPropertyCommand(owner, property, current, candidate));
        }
        ImGui.popID();
    }

    private void acceptGameObjectDrop(IComponent owner, ExportedProperty property) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        GameObject dropped = ImGui.acceptDragDropPayload(HierarchyView.PAYLOAD_GAMEOBJECT, GameObject.class);
        if (dropped != null) {
            history().execute(new SetPropertyCommand(owner, property, property.read(), dropped));
        }
        ImGui.endDragDropTarget();
    }

    private static float boundedMin(ExportedProperty property) {
        return Float.isFinite(property.min()) ? property.min() : -NUMERIC_RANGE_FALLBACK;
    }

    private static float boundedMax(ExportedProperty property) {
        return Float.isFinite(property.max()) ? property.max() : NUMERIC_RANGE_FALLBACK;
    }

    private EulerCache eulerCache(String key) {
        seenKeysThisFrame.add(key);
        return eulerCaches.computeIfAbsent(key, ignored -> new EulerCache());
    }

    private ImString stringBuffer(String key, String currentValue) {
        seenKeysThisFrame.add(key);
        String safeValue = currentValue == null ? "" : currentValue;
        ImString buffer = stringBuffers.computeIfAbsent(key, ignored -> new ImString(safeValue, STRING_CAPACITY));
        if (!ImGui.isAnyItemActive() && !buffer.get().equals(safeValue)) {
            buffer.set(safeValue);
        }
        return buffer;
    }
}
