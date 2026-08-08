package fr.epistudio.epysia.render.volumetric;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.physics.components.CapsuleCollider;
import fr.epistudio.epysia.physics.components.SphereCollider;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ColliderOccupancyRasterizerTest {
    private static final int ALL_LAYERS = -1;
    private static final float TOLERANCE = 0.0001f;

    private final ColliderOccupancyRasterizer rasterizer = new ColliderOccupancyRasterizer();
    private final float[] packed = new float[ColliderOccupancyRasterizer.MAXIMUM_SHAPES
            * ColliderOccupancyRasterizer.FLOATS_PER_SHAPE];

    @Test
    void thePackedMatrixMapsWorldSpaceBackIntoColliderLocalSpace() {
        Scene scene = new Scene("rasterizer");
        GameObject wall = new GameObject("Wall");
        Transform3D transform = wall.addComponent(new Transform3D());
        transform.position().set(4.0f, 2.0f, -3.0f);
        wall.addComponent(new BoxCollider()).setHalfExtents(1.0f, 2.0f, 0.5f);
        scene.addGameObject(wall);
        scene.advanceTick();

        assertEquals(1, rasterizer.pack(scene, ALL_LAYERS, packed));

        Matrix4f worldToLocal = new Matrix4f().set(packed, 0);
        Vector4f centerInLocal = worldToLocal.transform(new Vector4f(4.0f, 2.0f, -3.0f, 1.0f));
        assertEquals(0.0f, centerInLocal.x, TOLERANCE);
        assertEquals(0.0f, centerInLocal.y, TOLERANCE);
        assertEquals(0.0f, centerInLocal.z, TOLERANCE);

        Vector4f cornerInLocal = worldToLocal.transform(new Vector4f(5.0f, 4.0f, -2.5f, 1.0f));
        assertEquals(1.0f, cornerInLocal.x, TOLERANCE);
        assertEquals(2.0f, cornerInLocal.y, TOLERANCE);
        assertEquals(0.5f, cornerInLocal.z, TOLERANCE);
    }

    @Test
    void theColliderOffsetShiftsTheLocalOrigin() {
        Scene scene = new Scene("rasterizer");
        GameObject block = new GameObject("Block");
        block.addComponent(new Transform3D());
        block.addComponent(new BoxCollider()).setOffset(0.0f, 5.0f, 0.0f);
        scene.addGameObject(block);
        scene.advanceTick();

        rasterizer.pack(scene, ALL_LAYERS, packed);

        Matrix4f worldToLocal = new Matrix4f().set(packed, 0);
        Vector4f offsetCenter = worldToLocal.transform(new Vector4f(0.0f, 5.0f, 0.0f, 1.0f));
        assertEquals(0.0f, offsetCenter.y, TOLERANCE);
    }

    @Test
    void eachShapeKindLandsInTheSlotsTheOccupancyKernelReads() {
        Scene scene = new Scene("rasterizer");
        scene.addGameObject(colliderObject("Sphere", new SphereCollider().setRadius(1.5f)));
        scene.advanceTick();
        rasterizer.pack(scene, ALL_LAYERS, packed);
        assertEquals(1.5f, packed[16 + 3], TOLERANCE);
        assertEquals(ColliderOccupancyRasterizer.KIND_SPHERE, packed[16 + 5], TOLERANCE);

        Scene capsuleScene = new Scene("rasterizer");
        capsuleScene.addGameObject(colliderObject("Capsule",
                new CapsuleCollider().setCapsule(0.4f, 0.9f)));
        capsuleScene.advanceTick();
        rasterizer.pack(capsuleScene, ALL_LAYERS, packed);
        assertEquals(0.4f, packed[16 + 3], TOLERANCE);
        assertEquals(0.9f, packed[16 + 4], TOLERANCE);
        assertEquals(ColliderOccupancyRasterizer.KIND_CAPSULE, packed[16 + 5], TOLERANCE);
    }

    @Test
    void collidersOutsideTheLayerMaskAreSkipped() {
        Scene scene = new Scene("rasterizer");
        GameObject block = new GameObject("Block");
        block.addComponent(new Transform3D());
        BoxCollider collider = block.addComponent(new BoxCollider());
        collider.setCollisionLayer(3);
        scene.addGameObject(block);
        scene.advanceTick();

        assertEquals(0, rasterizer.pack(scene, 1 << 5, packed));
        assertEquals(1, rasterizer.pack(scene, 1 << 3, packed));
    }

    @Test
    void triggersNeverOccludeSmoke() {
        Scene scene = new Scene("rasterizer");
        GameObject zone = new GameObject("Zone");
        zone.addComponent(new Transform3D());
        zone.addComponent(new BoxCollider()).setTrigger(true);
        scene.addGameObject(zone);
        scene.advanceTick();

        assertTrue(rasterizer.pack(scene, ALL_LAYERS, packed) == 0);
    }

    private static GameObject colliderObject(String name, fr.epistudio.epysia.physics.components.Collider collider) {
        GameObject gameObject = new GameObject(name);
        gameObject.addComponent(new Transform3D());
        gameObject.addComponent(collider);
        return gameObject;
    }
}
