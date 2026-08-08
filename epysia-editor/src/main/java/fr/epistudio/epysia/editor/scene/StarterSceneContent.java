package fr.epistudio.epysia.editor.scene;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.BoxCollider;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;
import fr.epistudio.epysia.scene.Scene;

public final class StarterSceneContent {

    private static final float GROUND_SIZE = 20.0f;
    private static final float GROUND_COLLIDER_HALF_HEIGHT = 0.02f;

    private StarterSceneContent() {
    }

    public static void populate(Scene scene, EngineServices services) {
        scene.addGameObject(buildGround(services));
        scene.addGameObject(buildCamera());
        scene.advanceTick();
    }

    private static GameObject buildGround(EngineServices services) {
        GameObject ground = new GameObject("Ground");
        ground.addComponent(new Transform3D().setScale(GROUND_SIZE, 1.0f, GROUND_SIZE));
        MeshRenderer renderer = new MeshRenderer().setMeshPath("preset:plane");
        ground.addComponent(renderer);
        renderer.onLoad(services);
        BoxCollider collider = new BoxCollider();
        collider.halfExtents().set(BuiltinMeshes.PLANE_HALF_SIZE, GROUND_COLLIDER_HALF_HEIGHT,
                BuiltinMeshes.PLANE_HALF_SIZE);
        ground.addComponent(collider);
        return ground;
    }

    private static GameObject buildCamera() {
        GameObject camera = new GameObject("Main Camera");
        Transform3D transform = new Transform3D().setPosition(0.0f, 2.0f, 6.0f);
        transform.lookAt(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        camera.addComponent(transform);
        camera.addComponent(new Camera3D());
        return camera;
    }
}
