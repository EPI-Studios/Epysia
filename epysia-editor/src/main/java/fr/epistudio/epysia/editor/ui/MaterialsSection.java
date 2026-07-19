package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.command.EditorHistory;
import fr.epistudio.epysia.editor.command.builtin.AddMaterialCommand;
import fr.epistudio.epysia.editor.command.builtin.SetMaterialPropertyCommand;
import fr.epistudio.epysia.editor.inspector.AssetMimeTypes;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialFields;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.mesh.UploadedSubmesh;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;

public final class MaterialsSection {

    private static final float TEXTURE_WELL_SIZE = 32.0f;
    private static final float FLOAT_DRAG_STEP = 0.05f;
    private static final Set<String> UNIT_RANGE_FIELDS = Set.of("metallic", "roughness", "alphaCutoff");
    private static final int SLOT_HEADER_FLAGS = ImGuiTreeNodeFlags.DefaultOpen
            | ImGuiTreeNodeFlags.SpanAvailWidth;

    private final Supplier<SceneDocument> activeDocument;
    private final ThumbnailCache thumbnails;

    public MaterialsSection(Supplier<SceneDocument> activeDocument, ThumbnailCache thumbnails) {
        this.activeDocument = activeDocument;
        this.thumbnails = thumbnails;
    }

    private EditorHistory history() {
        return activeDocument.get().history();
    }

    public void render(MeshRenderer renderer) {
        ImGui.spacing();
        ImGui.textDisabled("Materials");
        ImGui.separator();
        int slotCount = slotCount(renderer);
        for (int slot = 0; slot < slotCount; slot++) {
            renderSlot(renderer, slot);
        }
    }

    private static int slotCount(MeshRenderer renderer) {
        int fromMesh = renderer.mesh().map(MaterialsSection::highestSlot).orElse(0);
        return Math.max(1, Math.max(fromMesh, renderer.materials().size()));
    }

    private static int highestSlot(UploadedMesh mesh) {
        int highest = 0;
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            highest = Math.max(highest, submesh.materialSlot() + 1);
        }
        return highest;
    }

    private void renderSlot(MeshRenderer renderer, int slot) {
        ImGui.pushID("material-slot-" + slot);
        Optional<Material> material = renderer.materialForSlot(slot);
        String label = "Slot " + slot + (material.isPresent()
                ? "  (" + material.get().getClass().getSimpleName() + ")" : "  (empty)");
        if (ImGui.treeNodeEx(label, SLOT_HEADER_FLAGS)) {
            renderSlotBody(renderer, slot, material);
            ImGui.treePop();
        }
        ImGui.popID();
    }

    private void renderSlotBody(MeshRenderer renderer, int slot, Optional<Material> material) {
        if (material.isEmpty()) {
            if (ImGui.button("+ Material")) {
                history().execute(new AddMaterialCommand(renderer, slot));
            }
            return;
        }
        renderMaterialEditor(material.get());
    }

    private void renderMaterialEditor(Material material) {
        for (Field field : MaterialFields.uniformFields(material.getClass())) {
            renderUniformRow(material, field);
        }
        for (Field field : MaterialFields.textureFields(material.getClass())) {
            renderTextureRow(material, field);
        }
        renderTransparentRow(material);
        renderDoubleSidedRow(material);
    }

    private void renderUniformRow(Material material, Field field) {
        Object value = MaterialFields.read(material, field);
        switch (value) {
            case Vector3f vector -> renderColorRow(material, field, vector);
            case Float number -> renderFloatRow(material, field, number);
            case null, default -> {
            }
        }
    }

    private void renderColorRow(Material material, Field field, Vector3f vector) {
        float[] components = {vector.x, vector.y, vector.z};
        if (ImGui.colorEdit3(labelFor(field), components)
                && (vector.x != components[0] || vector.y != components[1] || vector.z != components[2])) {
            executeUniformChange(material, field, new Vector3f(vector),
                    new Vector3f(components[0], components[1], components[2]));
        }
    }

    private void renderFloatRow(Material material, Field field, float current) {
        float[] value = {current};
        boolean changed = UNIT_RANGE_FIELDS.contains(field.getName())
                ? ImGui.sliderFloat(labelFor(field), value, 0.0f, 1.0f)
                : ImGui.dragFloat(labelFor(field), value, FLOAT_DRAG_STEP);
        if (changed && Float.compare(value[0], current) != 0) {
            executeUniformChange(material, field, current, value[0]);
        }
    }

    private void executeUniformChange(Material material, Field field, Object before, Object after) {
        history().execute(new SetMaterialPropertyCommand(material,
                SetMaterialPropertyCommand.Target.UNIFORM, field.getName(), before, after));
    }

    private void renderTextureRow(Material material, Field field) {
        ImGui.pushID("texture-" + field.getName());
        String currentPath = material.texturePath(field.getName()).orElse("");
        renderTextureWell(material, field, currentPath);
        acceptTextureDrop(material, field, currentPath);
        renderTextureLabel(material, field, currentPath);
        ImGui.popID();
    }

    private void renderTextureWell(Material material, Field field, String currentPath) {
        OptionalInt thumbnail = currentPath.isEmpty() ? OptionalInt.empty() : thumbnails.get(currentPath);
        if (thumbnail.isPresent()) {
            ImGui.imageButton(thumbnail.getAsInt(), TEXTURE_WELL_SIZE, TEXTURE_WELL_SIZE);
        } else {
            ImGui.button(currentPath.isEmpty() ? "None" : "…", TEXTURE_WELL_SIZE + 8.0f, TEXTURE_WELL_SIZE);
        }
        if (ImGui.isItemHovered() && !currentPath.isEmpty()) {
            ImGui.setTooltip(currentPath);
        }
    }

    private void acceptTextureDrop(Material material, Field field, String currentPath) {
        if (!ImGui.beginDragDropTarget()) {
            return;
        }
        String droppedPath = ImGui.acceptDragDropPayload(AssetMimeTypes.TEXTURE, String.class);
        if (droppedPath != null && !droppedPath.equals(currentPath)) {
            executeTextureChange(material, field, currentPath, droppedPath);
        }
        ImGui.endDragDropTarget();
    }

    private void renderTextureLabel(Material material, Field field, String currentPath) {
        ImGui.sameLine();
        ImGui.textUnformatted(labelFor(field));
        if (currentPath.isEmpty()) {
            return;
        }
        ImGui.sameLine();
        ImGui.textDisabled(Path.of(currentPath).getFileName().toString());
        ImGui.sameLine();
        if (ImGui.smallButton("X")) {
            executeTextureChange(material, field, currentPath, "");
        }
    }

    private void executeTextureChange(Material material, Field field, String before, String after) {
        history().execute(new SetMaterialPropertyCommand(material,
                SetMaterialPropertyCommand.Target.TEXTURE, field.getName(), before, after));
    }

    private void renderTransparentRow(Material material) {
        boolean current = material.transparent();
        if (ImGui.checkbox("Transparent", current)) {
            history().execute(new SetMaterialPropertyCommand(material,
                    SetMaterialPropertyCommand.Target.TRANSPARENT, "", current, !current));
        }
    }

    private void renderDoubleSidedRow(Material material) {
        boolean current = material.doubleSided();
        if (ImGui.checkbox("Double Sided", current)) {
            history().execute(new SetMaterialPropertyCommand(material,
                    SetMaterialPropertyCommand.Target.DOUBLE_SIDED, "", current, !current));
        }
    }

    private static String labelFor(Field field) {
        String name = field.getName();
        StringBuilder label = new StringBuilder();
        for (char character : name.toCharArray()) {
            if (Character.isUpperCase(character) && !label.isEmpty()) {
                label.append(' ');
            }
            label.append(character);
        }
        return capitalize(label.toString());
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text
                : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
