package fr.epistudio.epysia.navigation;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class NavigationSystem implements GameSystem {

    private final NavigationService service = new NavigationService();
    private final Vector3f position = new Vector3f();
    private final Vector3f focus = new Vector3f();
    private final Vector3f toCorner = new Vector3f();
    private final Quaternionf facing = new Quaternionf();

    public NavigationService service() {
        return service;
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        refreshStreamedTiles(scene);
        if (!service.baked()) {
            return;
        }
        for (NavMeshAgent agent : scene.componentsOf(NavMeshAgent.class)) {
            advance(agent, deltaTimeSeconds);
        }
    }

    private void refreshStreamedTiles(Scene scene) {
        for (NavMeshSurface surface : scene.componentsOf(NavMeshSurface.class)) {
            if (!surface.followStreaming() || !locateFocus(scene)) {
                continue;
            }
            service.refreshAround(scene, focus, surface.streamingRadius(), surface.tilesPerFrame());
        }
    }

    private boolean locateFocus(Scene scene) {
        for (Camera3D camera : scene.componentsOf(Camera3D.class)) {
            if (!camera.active() || camera.ownerOrNull() == null) {
                continue;
            }
            Transform3D transform = camera.ownerOrNull().getComponentOrNull(Transform3D.class);
            if (transform != null) {
                transform.worldPosition(focus);
                return true;
            }
        }
        return false;
    }

    private void advance(NavMeshAgent agent, float deltaTimeSeconds) {
        Transform3D transform = agent.ownerOrNull() == null
                ? null
                : agent.ownerOrNull().getComponentOrNull(Transform3D.class);
        if (transform == null || !agent.hasDestination()) {
            return;
        }
        transform.worldPosition(position);
        refreshPath(agent, deltaTimeSeconds);
        steer(agent, transform, deltaTimeSeconds);
    }

    private void refreshPath(NavMeshAgent agent, float deltaTimeSeconds) {
        agent.setRepathTimer(agent.repathTimer() - deltaTimeSeconds);
        if (agent.repathTimer() > 0.0f && !agent.corners().isEmpty()) {
            return;
        }
        agent.setRepathTimer(Math.max(agent.repathInterval(), deltaTimeSeconds));
        List<Vector3f> path = service.findPath(position, agent.destination());
        agent.corners().clear();
        agent.corners().addAll(path);
        agent.setCorner(0);
    }

    private void steer(NavMeshAgent agent, Transform3D transform, float deltaTimeSeconds) {
        Vector3f corner = nextCorner(agent);
        if (corner == null) {
            agent.desiredVelocity().zero();
            return;
        }
        toCorner.set(corner).sub(position);
        toCorner.y = 0.0f;
        if (toCorner.lengthSquared() <= 1.0e-6f) {
            return;
        }
        agent.desiredVelocity().set(toCorner).normalize().mul(agent.speed());
        if (agent.steerTransform()) {
            applyMotion(agent, transform, deltaTimeSeconds);
        }
    }

    private Vector3f nextCorner(NavMeshAgent agent) {
        while (agent.corner() < agent.corners().size()) {
            Vector3f candidate = agent.corners().get(agent.corner());
            toCorner.set(candidate).sub(position);
            toCorner.y = 0.0f;
            if (toCorner.length() > agent.arrivalDistance()) {
                return candidate;
            }
            agent.setCorner(agent.corner() + 1);
        }
        agent.corners().clear();
        return null;
    }

    private void applyMotion(NavMeshAgent agent, Transform3D transform, float deltaTimeSeconds) {
        transform.setPosition(position.x + agent.desiredVelocity().x * deltaTimeSeconds,
                position.y,
                position.z + agent.desiredVelocity().z * deltaTimeSeconds);
        if (agent.turnSpeed() <= 0.0f) {
            return;
        }
        facing.identity().rotateY((float) Math.atan2(agent.desiredVelocity().x, agent.desiredVelocity().z));
        transform.setRotation(transform.rotation().slerp(facing,
                Math.clamp(agent.turnSpeed() * deltaTimeSeconds, 0.0f, 1.0f), facing));
    }

    @Override
    public void initialize(EngineServices services) {
    }
}
