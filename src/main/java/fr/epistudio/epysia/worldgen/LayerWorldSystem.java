package fr.epistudio.epysia.worldgen;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;

public final class LayerWorldSystem implements GameSystem {

    private final LayerWorld world;
    private final Vector3f focus = new Vector3f();
    private EngineServices services;

    public LayerWorldSystem(LayerWorld world) {
        this.world = world;
    }

    public LayerWorld world() {
        return world;
    }

    @Override
    public void initialize(EngineServices engineServices) {
        this.services = engineServices;
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        if (services == null || !locateFocus(scene)) {
            return;
        }
        world.update(services, focus.x, focus.z);
    }

    private boolean locateFocus(Scene scene) {
        for (Camera3D camera : scene.componentsOf(Camera3D.class)) {
            GameObject owner = camera.ownerOrNull();
            Transform3D transform = owner == null ? null : owner.getComponentOrNull(Transform3D.class);
            if (transform != null) {
                focus.set(transform.position());
                return true;
            }
        }
        return false;
    }

    @Override
    public void shutdown() {
        if (services != null) {
            world.shutdown(services);
        }
    }
}
