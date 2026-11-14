package fr.epistudio.epysia.components;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Camera2DSystem implements GameSystem {

    private static final float VIEW_DISTANCE = 10.0f;
    private static final float NEAR_PLANE = 0.05f;
    private static final float FAR_PLANE = 100.0f;

    private final Map<Camera2D, Camera3D> views = new IdentityHashMap<>();
    private final Vector3f scratchTranslation = new Vector3f();
    private final Vector2f scratchTarget = new Vector2f();

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
    }

    @Override
    public void lateUpdate(Scene scene, InputState input, float deltaTimeSeconds) {
        List<Camera2D> cameras = scene.componentsOf(Camera2D.class);
        releaseViewsOfRemovedCameras(scene, cameras);
        for (Camera2D camera : cameras) {
            camera.owner().ifPresent(owner -> drive(scene, camera, owner));
        }
    }

    @Override
    public void shutdown() {
        views.clear();
    }

    private void drive(Scene scene, Camera2D camera, GameObject owner) {
        Transform2D transform = owner.getComponentOrNull(Transform2D.class);
        if (transform == null) {
            return;
        }
        Camera3D view = viewOf(scene, camera);
        follow(camera, transform);
        keepInsideLimits(camera, transform, view);
        Vector2f position = transform.position();
        view.ownerOrNull().getComponentOrNull(Transform3D.class).setPosition(position.x, position.y, VIEW_DISTANCE);
        view.setOrthographicSize(camera.visibleHalfHeight());
        view.setActive(camera.active() && owner.activeInHierarchy());
    }

    private Camera3D viewOf(Scene scene, Camera2D camera) {
        Camera3D existing = views.get(camera);
        if (existing != null) {
            return existing;
        }
        Camera3D created = createView(scene);
        views.put(camera, created);
        return created;
    }

    private static Camera3D createView(Scene scene) {
        GameObject view = new GameObject("Camera 2D View");
        view.setPersistent(false);
        view.addComponent(new Transform3D());
        Camera3D camera = view.addComponent(new Camera3D()
                .setOrthographic(true)
                .setNearFar(NEAR_PLANE, FAR_PLANE));
        scene.addGameObject(view);
        return camera;
    }

    private void follow(Camera2D camera, Transform2D transform) {
        Optional<Vector2f> target = camera.followTarget().flatMap(this::planarPositionOf);
        if (target.isEmpty()) {
            return;
        }
        float blend = 1.0f - camera.followSmoothing();
        Vector2f position = transform.position();
        transform.setPosition(
                position.x + (target.get().x - position.x) * blend,
                position.y + (target.get().y - position.y) * blend);
    }

    private static void keepInsideLimits(Camera2D camera, Transform2D transform, Camera3D view) {
        if (!camera.limited()) {
            return;
        }
        float halfHeight = camera.visibleHalfHeight();
        float halfWidth = halfHeight * view.aspectRatio();
        Vector2f position = transform.position();
        transform.setPosition(
                clampAxis(position.x, camera.limitMin().x(), camera.limitMax().x(), halfWidth),
                clampAxis(position.y, camera.limitMin().y(), camera.limitMax().y(), halfHeight));
    }

    private static float clampAxis(float value, float minimum, float maximum, float halfExtent) {
        float lowest = minimum + halfExtent;
        float highest = maximum - halfExtent;
        if (lowest > highest) {
            return (minimum + maximum) * 0.5f;
        }
        return Math.clamp(value, lowest, highest);
    }

    private Optional<Vector2f> planarPositionOf(GameObject gameObject) {
        Transform2D planar = gameObject.getComponentOrNull(Transform2D.class);
        if (planar != null) {
            return Optional.of(scratchTarget.set(planar.position()));
        }
        Transform3D spatial = gameObject.getComponentOrNull(Transform3D.class);
        if (spatial == null) {
            return Optional.empty();
        }
        spatial.worldMatrix().getTranslation(scratchTranslation);
        return Optional.of(scratchTarget.set(scratchTranslation.x, scratchTranslation.y));
    }

    private void releaseViewsOfRemovedCameras(Scene scene, List<Camera2D> cameras) {
        views.entrySet().removeIf(entry -> {
            if (cameras.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().owner().ifPresent(scene::removeGameObject);
            return true;
        });
    }
}
