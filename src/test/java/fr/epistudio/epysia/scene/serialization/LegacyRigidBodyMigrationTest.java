package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.RigidBodyKind;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.Collider;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRigidBodyMigrationTest {

    private static final String LEGACY_SCENE = """
            {
              "name": "Legacy",
              "gameObjects": [
                {
                  "name": "BoxDynamic",
                  "parentIndex": -1,
                  "components": [
                    {
                      "type": "fr.epistudio.epysia.physics.components.RigidBodyComponent",
                      "fields": { "kind": "DYNAMIC", "shapeKind": "BOX", "halfExtents": [1.0, 2.0, 3.0] }
                    }
                  ]
                },
                {
                  "name": "Zone",
                  "parentIndex": -1,
                  "components": [
                    {
                      "type": "fr.epistudio.epysia.physics.components.RigidBodyComponent",
                      "fields": { "kind": "AREA", "shapeKind": "BOX" }
                    }
                  ]
                }
              ]
            }
            """;

    @Test
    void migratesLegacyBoxDynamicAndArea() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        SceneSerializer serializer = new SceneSerializer(registry);
        Scene scene = new Scene("Legacy");

        serializer.deserialize(scene, LEGACY_SCENE, null);

        GameObject boxDynamic = scene.gameObjects().get(0);
        RigidBodyComponent boxBody = boxDynamic.getComponent(RigidBodyComponent.class).orElseThrow();
        assertEquals(RigidBodyKind.DYNAMIC, boxBody.kind());
        Collider boxCollider = collider(boxDynamic);
        assertInstanceOf(BoxCollider.class, boxCollider);
        assertFalse(boxBody.isRegistered());

        GameObject zone = scene.gameObjects().get(1);
        RigidBodyComponent zoneBody = zone.getComponent(RigidBodyComponent.class).orElseThrow();
        assertEquals(RigidBodyKind.STATIC, zoneBody.kind());
        Collider zoneCollider = collider(zone);
        assertInstanceOf(BoxCollider.class, zoneCollider);
        assertTrue(zoneCollider.isTrigger());
    }

    private static Collider collider(GameObject gameObject) {
        for (IComponent component : gameObject.components()) {
            if (component instanceof Collider found) {
                return found;
            }
        }
        throw new AssertionError("No Collider produced for " + gameObject.name());
    }
}
