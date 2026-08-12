package fr.epistudio.epysia.gameobjects;

import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentLifecycleTest {

    @Test
    void deactivatingAnAncestorDeactivatesTheWholeSubtree() {
        GameObject root = objectNamed("root");
        GameObject child = objectNamed("child");
        GameObject grandChild = objectNamed("grandChild");
        child.setParent(root);
        grandChild.setParent(child);

        root.setActive(false);

        assertFalse(grandChild.activeInHierarchy(),
                "a deactivated ancestor must deactivate every descendant");
        assertTrue(grandChild.active(),
                "the descendant keeps its own authored flag untouched");
    }

    @Test
    void removalFromTheSceneMarksTheObjectAndItsComponentsDead() {
        Scene scene = new Scene("scene");
        GameObject object = objectNamed("doomed");
        PointLight light = object.addComponent(new PointLight());
        scene.addGameObject(object);
        scene.advanceTick();

        scene.removeGameObject(object);
        scene.advanceTick();

        assertFalse(object.isAlive(), "a removed object must report itself dead");
        assertFalse(light.isAlive(), "components of a removed object must report themselves dead");
        assertFalse(light.activeInHierarchy(), "a dead component is never active");
    }

    @Test
    void disablingAComponentLeavesTheOwnerActive() {
        GameObject object = objectNamed("holder");
        PointLight light = object.addComponent(new PointLight());

        light.setEnabled(false);

        assertFalse(light.activeInHierarchy(), "a disabled component is not active");
        assertTrue(object.activeInHierarchy(), "disabling a component must not deactivate its owner");
    }

    @Test
    void renamingRefreshesTheNameLookup() {
        Scene scene = new Scene("scene");
        GameObject object = objectNamed("before");
        scene.addGameObject(object);
        scene.advanceTick();
        assertTrue(scene.findByName("before").isPresent(), "the original name must resolve");

        object.setName("after");

        assertTrue(scene.findByName("after").isPresent(),
                "renaming must invalidate the cached name lookup");
        assertFalse(scene.findByName("before").isPresent(),
                "the stale name must stop resolving after a rename");
    }

    @Test
    void tagLookupTracksEveryTagAndRefreshesOnChange() {
        Scene scene = new Scene("scene");
        GameObject object = objectNamed("tagged");
        object.addTag("enemy").addTag("flying");
        scene.addGameObject(object);
        scene.advanceTick();

        assertEquals(1, scene.findByTag("enemy").size(), "the first tag must resolve");
        assertEquals(1, scene.findByTag("flying").size(),
                "a second tag on the same object must resolve too");

        object.removeTag("flying");

        assertTrue(scene.findByTag("flying").isEmpty(),
                "removing a tag must invalidate the cached tag lookup");
    }

    private static GameObject objectNamed(String name) {
        GameObject object = new GameObject(name);
        object.addComponent(new Transform3D());
        return object;
    }
}
