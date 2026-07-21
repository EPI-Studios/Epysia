package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SceneSubtreeRemovalTest {

    @Test
    void removingParentCascadesToChildAndUndoRestoresBoth() {
        Scene scene = new Scene("SubtreeRemoval");
        GameObject parent = new GameObject("Parent");
        Transform3D parentTransform = parent.addComponent(new Transform3D());
        GameObject child = new GameObject("Child");
        Transform3D childTransform = child.addComponent(new Transform3D());
        childTransform.setParent(parentTransform);
        scene.addGameObject(parent);
        scene.addGameObject(child);
        scene.advanceTick();

        scene.removeGameObject(parent);
        scene.advanceTick();

        assertFalse(scene.gameObjects().contains(parent));
        assertFalse(scene.gameObjects().contains(child));

        scene.addGameObject(parent);
        scene.addGameObject(child);
        scene.advanceTick();

        assertTrue(scene.gameObjects().contains(parent));
        assertTrue(scene.gameObjects().contains(child));
        assertEquals(parentTransform, childTransform.parent().orElseThrow());
    }
}
