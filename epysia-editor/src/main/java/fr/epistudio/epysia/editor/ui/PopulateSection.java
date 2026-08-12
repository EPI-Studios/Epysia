package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.assets.epyinstances.EpyInstancesFormat;
import fr.epistudio.epysia.assets.epyinstances.EpyInstancesWriter;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.MeshDataSource;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.render.mesh.SurfacePopulator;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;
import imgui.type.ImInt;
import org.joml.Matrix4f;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class PopulateSection {

    private static final String[] UP_AXIS_LABELS = {"X", "Y", "Z"};
    private static final int MINIMUM_AMOUNT = 1;
    private static final int MAXIMUM_AMOUNT = 500_000;

    private static final String INSTANCES_DIRECTORY_NAME = "instances";

    private final Supplier<SceneDocument> activeDocument;
    private final Notifier notifier;
    private final Path outputDirectory;
    private final ImInt surfaceIndex = new ImInt();
    private final ImInt upAxisIndex = new ImInt(1);
    private final int[] amount = {1000};
    private final int[] seed = {1};
    private final float[] rotateRandom = {1.0f};
    private final float[] tiltRandom = {0.0f};
    private final float[] scale = {1.0f};
    private final float[] scaleRandom = {0.0f};

    public PopulateSection(Supplier<SceneDocument> activeDocument, Notifier notifier, Project project) {
        this.activeDocument = activeDocument;
        this.notifier = notifier;
        this.outputDirectory = project.rootDirectory().resolve(INSTANCES_DIRECTORY_NAME);
    }

    public void render(MultiMeshRenderer renderer) {
        ImGui.separator();
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_POPULATE_SECTION_TITLE));
        List<GameObject> surfaces = surfaceCandidates();
        if (surfaces.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_POPULATE_SECTION_NO_TARGET));
            return;
        }
        renderSurfacePicker(surfaces);
        renderParameters();
        renderPopulateButton(renderer, surfaces);
        Texts.muted(renderer.instanceCount() + " instances stored");
    }

    private void renderSurfacePicker(List<GameObject> surfaces) {
        surfaceIndex.set(Math.min(surfaceIndex.get(), surfaces.size() - 1));
        String[] names = new String[surfaces.size()];
        for (int index = 0; index < surfaces.size(); index++) {
            names[index] = surfaces.get(index).name();
        }
        ImGui.combo("Surface", surfaceIndex, names);
    }

    private void renderParameters() {
        ImGui.dragInt("Amount", amount, 10.0f, MINIMUM_AMOUNT, MAXIMUM_AMOUNT);
        ImGui.combo("Up axis", upAxisIndex, UP_AXIS_LABELS);
        ImGui.sliderFloat("Random rotation", rotateRandom, 0.0f, 1.0f);
        ImGui.sliderFloat("Random tilt", tiltRandom, 0.0f, 1.0f);
        ImGui.dragFloat("Scale", scale, 0.01f, 0.01f, 100.0f);
        ImGui.sliderFloat("Random scale", scaleRandom, 0.0f, 1.0f);
        ImGui.dragInt("Seed", seed, 1.0f, 0, Integer.MAX_VALUE);
    }

    private void renderPopulateButton(MultiMeshRenderer renderer, List<GameObject> surfaces) {
        if (!ImGui.button(I18n.translate(TextKey.EDITOR_POPULATE_SECTION_POPULATE))) {
            return;
        }
        GameObject surface = surfaces.get(surfaceIndex.get());
        Optional<MeshData> surfaceData = surfaceMeshOf(surface);
        if (surfaceData.isEmpty()) {
            notifier.show("Cannot read the geometry of '" + surface.name() + "'.");
            return;
        }
        List<Matrix4f> instances = SurfacePopulator.populate(surfaceData.get(),
                worldMatrixOf(surface), settings());
        renderer.setInstances(instances);
        storeInstances(renderer);
        activeDocument.get().markDirty();
        notifier.show("Populated " + instances.size() + " instances on '" + surface.name() + "'.");
    }

    private void storeInstances(MultiMeshRenderer renderer) {
        Path file = outputDirectory.resolve(instancesFileName(renderer));
        try {
            EpyInstancesWriter.writeToFile(file, renderer.instanceModels());
            renderer.instancesRef().setPath(file.toString());
        } catch (RuntimeException error) {
            notifier.show("Could not write " + file + ": " + error.getMessage());
        }
    }

    private String instancesFileName(MultiMeshRenderer renderer) {
        GameObject owner = renderer.ownerOrNull();
        String ownerName = owner == null ? "instances" : owner.name();
        return activeDocument.get().name() + "." + sanitize(ownerName) + EpyInstancesFormat.EXTENSION;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private SurfacePopulator.Settings settings() {
        return new SurfacePopulator.Settings(amount[0],
                SurfacePopulator.UpAxis.values()[upAxisIndex.get()],
                rotateRandom[0], tiltRandom[0], scale[0], scaleRandom[0], seed[0]);
    }

    private static Optional<MeshData> surfaceMeshOf(GameObject surface) {
        MeshRenderer meshRenderer = surface.getComponentOrNull(MeshRenderer.class);
        return meshRenderer == null
                ? Optional.empty()
                : MeshDataSource.load(meshRenderer.meshRef().path());
    }

    private static Matrix4f worldMatrixOf(GameObject surface) {
        Transform3D transform = surface.getComponentOrNull(Transform3D.class);
        return transform == null ? new Matrix4f() : new Matrix4f(transform.worldMatrix());
    }

    private List<GameObject> surfaceCandidates() {
        return activeDocument.get().scene().gameObjects().stream()
                .filter(candidate -> candidate.getComponentOrNull(MeshRenderer.class) != null)
                .toList();
    }
}
