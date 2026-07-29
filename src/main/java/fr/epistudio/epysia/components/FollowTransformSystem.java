package fr.epistudio.epysia.components;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Optional;

public final class FollowTransformSystem implements GameSystem {

    private final Vector3f scratchTranslation = new Vector3f();
    private final Vector2f scratchTarget = new Vector2f();

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        for (FollowTransform2D follower : scene.componentsOf(FollowTransform2D.class)) {
            follower.owner()
                    .map(owner -> owner.getComponentOrNull(Transform2D.class))
                    .ifPresent(transform -> follow(scene, follower, transform));
        }
    }

    private void follow(Scene scene, FollowTransform2D follower, Transform2D transform) {
        Optional<Vector2f> target = planarPositionOf(follower.target().orElseGet(() -> activeCameraOf(scene)));
        if (target.isEmpty()) {
            return;
        }
        follower.captureRest(transform.position());
        Vector2f rest = follower.restPosition();
        float wantedX = rest.x + target.get().x * follower.weightX();
        float wantedY = rest.y + target.get().y * follower.weightY();
        float blend = 1.0f - follower.smoothing();
        transform.setPosition(
                transform.position().x + (wantedX - transform.position().x) * blend,
                transform.position().y + (wantedY - transform.position().y) * blend);
    }

    private static GameObject activeCameraOf(Scene scene) {
        for (Camera3D camera : scene.componentsOf(Camera3D.class)) {
            GameObject owner = camera.ownerOrNull();
            if (owner != null) {
                return owner;
            }
        }
        return null;
    }

    private Optional<Vector2f> planarPositionOf(GameObject gameObject) {
        if (gameObject == null) {
            return Optional.empty();
        }
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
}
