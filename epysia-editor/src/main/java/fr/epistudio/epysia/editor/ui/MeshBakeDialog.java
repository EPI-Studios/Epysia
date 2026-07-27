package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epymesh.BakedCollider;
import fr.epistudio.epysia.assets.epymesh.EpyMeshFormat;
import fr.epistudio.epysia.assets.epymesh.EpyMeshWriter;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.ObjMesh;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class MeshBakeDialog {

    private static final String POPUP_TITLE = "Import Mesh";

    private enum ColliderChoice { NONE, CONVEX, TRIANGLE }

    private final Notifier notifier;
    private final Consumer<Path> onBaked;
    private ColliderChoice choice = ColliderChoice.NONE;
    private Path sourcePath;
    private boolean openRequested;

    public MeshBakeDialog(Notifier notifier, Consumer<Path> onBaked) {
        this.notifier = notifier;
        this.onBaked = onBaked;
    }

    public void openFor(Path objPath) {
        sourcePath = objPath;
        choice = ColliderChoice.NONE;
        openRequested = true;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(I18n.label(TextKey.EDITOR_MESH_BAKE_DIALOG_TITLE, "mesh-bake-dialog"));
            openRequested = false;
        }
        if (!ImGui.beginPopupModal(I18n.label(TextKey.EDITOR_MESH_BAKE_DIALOG_TITLE, "mesh-bake-dialog"),
                ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        renderBody();
        ImGui.endPopup();
    }

    private void renderBody() {
        ImGui.labelText(I18n.label(TextKey.EDITOR_MESH_BAKE_DIALOG_SOURCE, "mesh-bake-source"),
                sourcePath == null ? "-" : sourcePath.getFileName().toString());
        renderColliderChoices();
        ImGui.separator();
        if (ImGui.button(I18n.label(TextKey.EDITOR_MESH_BAKE_DIALOG_BAKE, "mesh-bake-bake"))) {
            bake();
        }
        ImGui.sameLine();
        if (ImGui.button(I18n.label(TextKey.EDITOR_MESH_BAKE_DIALOG_CANCEL, "mesh-bake-cancel"))) {
            ImGui.closeCurrentPopup();
        }
    }

    private void renderColliderChoices() {
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_MESH_BAKE_DIALOG_COLLIDER));
        for (ColliderChoice candidate : ColliderChoice.values()) {
            if (ImGui.radioButton(I18n.label(colliderKey(candidate), "mesh-bake-collider-" + candidate.name()),
                    choice == candidate)) {
                choice = candidate;
            }
            ImGui.sameLine();
        }
        ImGui.newLine();
    }

    private static TextKey colliderKey(ColliderChoice candidate) {
        return switch (candidate) {
            case NONE -> TextKey.EDITOR_MESH_BAKE_DIALOG_COLLIDER_NONE;
            case CONVEX -> TextKey.EDITOR_MESH_BAKE_DIALOG_COLLIDER_CONVEX;
            case TRIANGLE -> TextKey.EDITOR_MESH_BAKE_DIALOG_COLLIDER_TRIANGLE;
        };
    }

    private void bake() {
        try {
            MeshData meshData = ObjMesh.parseFromFile(sourcePath);
            Path output = outputPath();
            EpyMeshWriter.writeToFile(output, meshData, buildCollider(meshData));
            ImGui.closeCurrentPopup();
            onBaked.accept(output);
        } catch (RuntimeException error) {
            notifier.show(I18n.translate(TextKey.EDITOR_MESH_BAKE_DIALOG_TOAST_BAKE_FAILED,
                    error.getMessage()));
        }
    }

    private Optional<BakedCollider> buildCollider(MeshData meshData) {
        return switch (choice) {
            case NONE -> Optional.empty();
            case CONVEX -> Optional.of(BakedCollider.convexHull(meshData.positions()));
            case TRIANGLE -> Optional.of(BakedCollider.triangleMesh(meshData.positions(), meshData.indices()));
        };
    }

    private Path outputPath() {
        String fileName = sourcePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return sourcePath.resolveSibling(baseName + EpyMeshFormat.EXTENSION);
    }
}
