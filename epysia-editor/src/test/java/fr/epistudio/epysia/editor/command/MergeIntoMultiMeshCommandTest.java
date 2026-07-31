package fr.epistudio.epysia.editor.command;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.EditorSelection;
import fr.epistudio.epysia.editor.command.builtin.MergeIntoMultiMeshCommand;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.mesh.Aabb;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MergeIntoMultiMeshCommandTest {

    private static final int SOURCE_COUNT = 3;

    @Test
    void mergesMatchingRenderersAndRestoresThemOnUndo() {
        Scene scene = new Scene("merge-test");
        CommandContext context = new CommandContext(scene, new EditorSelection(), null, null);
        UploadedMesh mesh = dummyMesh();
        Material material = new LitMaterial();
        List<GameObject> sources = spawnSources(scene, mesh, material);
        scene.advanceTick();

        MergeIntoMultiMeshCommand merge = new MergeIntoMultiMeshCommand(sources);
        EditorCommand undo = merge.invert(context);
        merge.apply(context);

        MultiMeshRenderer merged = singleMultiMesh(scene);
        assertNotNull(merged, "merge must produce one MultiMeshRenderer");
        assertEquals(SOURCE_COUNT, merged.instanceCount(), "every source must become an instance");
        for (GameObject source : sources) {
            assertNull(sceneMemberOf(scene, source), "sources must leave the scene");
        }

        undo.apply(context);

        assertNull(singleMultiMesh(scene), "undo must remove the generated MultiMesh");
        for (GameObject source : sources) {
            assertNotNull(sceneMemberOf(scene, source), "undo must restore every source");
        }
    }

    @Test
    void keepsRenderersThatDoNotShareMeshAndMaterial() {
        Scene scene = new Scene("merge-mixed");
        CommandContext context = new CommandContext(scene, new EditorSelection(), null, null);
        List<GameObject> sources = new ArrayList<>();
        for (int index = 0; index < SOURCE_COUNT; index++) {
            sources.add(spawn(scene, "distinct" + index, dummyMesh(), new LitMaterial(), index));
        }

        scene.advanceTick();

        new MergeIntoMultiMeshCommand(sources).apply(context);

        assertNull(singleMultiMesh(scene), "renderers with distinct meshes must not merge");
        for (GameObject source : sources) {
            assertNotNull(sceneMemberOf(scene, source), "unmerged sources must stay in the scene");
        }
    }

    private static List<GameObject> spawnSources(Scene scene, UploadedMesh mesh, Material material) {
        List<GameObject> sources = new ArrayList<>();
        for (int index = 0; index < SOURCE_COUNT; index++) {
            sources.add(spawn(scene, "pillar" + index, mesh, material, index));
        }
        return sources;
    }

    private static GameObject spawn(Scene scene, String name, UploadedMesh mesh, Material material, int index) {
        GameObject gameObject = new GameObject(name);
        gameObject.addComponent(new Transform3D()).setPosition(index * 2.0f, 0.0f, 0.0f);
        gameObject.addComponent(new MeshRenderer()).setMesh(mesh).setMaterial(material);
        scene.addGameObject(gameObject);
        return gameObject;
    }

    private static GameObject sceneMemberOf(Scene scene, GameObject candidate) {
        for (GameObject member : scene.gameObjects()) {
            if (member == candidate) {
                return member;
            }
        }
        return null;
    }

    private static MultiMeshRenderer singleMultiMesh(Scene scene) {
        for (GameObject member : scene.gameObjects()) {
            MultiMeshRenderer renderer = member.getComponentOrNull(MultiMeshRenderer.class);
            if (renderer != null) {
                return renderer;
            }
        }
        return null;
    }

    private static UploadedMesh dummyMesh() {
        return new UploadedMesh(new BufferHandle(1L), new BufferHandle(2L), List.of(),
                Aabb.fromPositions(new float[]{0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f}),
                false, false, Optional.empty());
    }
}
