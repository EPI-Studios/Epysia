package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.editor.command.builtin.FitColliderCommand;
import fr.epistudio.epysia.editor.scene.ColliderFit;
import fr.epistudio.epysia.editor.scene.MeshLocalBounds;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.Collider;
import fr.epistudio.epysia.render.mesh.Aabb;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;

import java.util.Optional;

public final class MeshColliderFitSection {

    public Optional<EditorCommand> render(GameObject gameObject, IComponent component) {
        if (!(component instanceof Collider collider) || !ColliderFit.supports(collider)) {
            return Optional.empty();
        }
        Optional<Aabb> bounds = MeshLocalBounds.of(gameObject);
        ImGui.beginDisabled(bounds.isEmpty());
        boolean clicked = ImGui.button("Fit to mesh");
        ImGui.endDisabled();
        renderTooltip(bounds);
        return clicked ? bounds.map(box -> fitCommand(collider, box)) : Optional.empty();
    }

    private static EditorCommand fitCommand(Collider collider, Aabb bounds) {
        return new FitColliderCommand(collider, ColliderFit.capture(collider),
                ColliderFit.wrapping(collider, bounds));
    }

    private static void renderTooltip(Optional<Aabb> bounds) {
        if (!ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            return;
        }
        ImGui.setTooltip(I18n.translate(bounds.isEmpty()
                ? TextKey.EDITOR_MESH_COLLIDER_FIT_SECTION_NO_MESH_TOOLTIP
                : TextKey.EDITOR_MESH_COLLIDER_FIT_SECTION_FIT_TOOLTIP));
    }
}
