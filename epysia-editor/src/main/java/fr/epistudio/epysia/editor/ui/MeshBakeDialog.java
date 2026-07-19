package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epymesh.BakedCollider;
import fr.epistudio.epysia.assets.epymesh.EpyMeshFormat;
import fr.epistudio.epysia.assets.epymesh.EpyMeshWriter;
import fr.epistudio.epysia.editor.notify.Notifier;
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
            ImGui.openPopup(POPUP_TITLE);
            openRequested = false;
        }
        if (!ImGui.beginPopupModal(POPUP_TITLE, ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        renderBody();
        ImGui.endPopup();
    }

    private void renderBody() {
        ImGui.labelText("Source", sourcePath == null ? "-" : sourcePath.getFileName().toString());
        renderColliderChoices();
        ImGui.separator();
        if (ImGui.button("Bake")) {
            bake();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }
    }

    private void renderColliderChoices() {
        ImGui.textDisabled("Collider");
        for (ColliderChoice candidate : ColliderChoice.values()) {
            if (ImGui.radioButton(candidate.name().charAt(0)
                    + candidate.name().substring(1).toLowerCase(java.util.Locale.ROOT), choice == candidate)) {
                choice = candidate;
            }
            ImGui.sameLine();
        }
        ImGui.newLine();
    }

    private void bake() {
        try {
            MeshData meshData = ObjMesh.parseFromFile(sourcePath);
            Path output = outputPath();
            EpyMeshWriter.writeToFile(output, meshData, buildCollider(meshData));
            ImGui.closeCurrentPopup();
            onBaked.accept(output);
        } catch (RuntimeException error) {
            notifier.show("Bake failed: " + error.getMessage());
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
