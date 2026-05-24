package fr.epistudio.epysia.editor;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EditorPlayRuntime {

    private final Scene scene;
    private final List<GameSystem> playModeSystems;
    private final EngineServices engineServices;
    private final Map<GameObject, TransformSnapshot> transformSnapshots = new HashMap<>();
    private boolean playing;
    private boolean initialized;

    public EditorPlayRuntime(Scene scene, List<GameSystem> playModeSystems, EngineServices engineServices) {
        this.scene = scene;
        this.playModeSystems = playModeSystems;
        this.engineServices = engineServices;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void play() {
        if (playing) {
            return;
        }
        captureSnapshots();
        if (!initialized) {
            for (GameSystem system : playModeSystems) {
                system.initialize(engineServices);
            }
            initialized = true;
        }
        playing = true;
    }

    public void stop() {
        if (!playing) {
            return;
        }
        playing = false;
        restoreSnapshots();
    }

    public void tick(InputState input, float deltaTimeSeconds) {
        if (!playing) {
            return;
        }
        scene.advanceTick();
        for (GameSystem system : playModeSystems) {
            system.update(scene, input, deltaTimeSeconds);
        }
    }

    public void shutdown() {
        if (initialized) {
            for (GameSystem system : playModeSystems) {
                system.shutdown();
            }
            initialized = false;
        }
        transformSnapshots.clear();
    }

    private void captureSnapshots() {
        transformSnapshots.clear();
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(Transform3D.class).ifPresent(transform ->
                    transformSnapshots.put(gameObject, snapshotOf(transform)));
        }
    }

    private void restoreSnapshots() {
        for (Map.Entry<GameObject, TransformSnapshot> entry : transformSnapshots.entrySet()) {
            entry.getKey().getComponent(Transform3D.class).ifPresent(transform -> {
                TransformSnapshot snapshot = entry.getValue();
                transform.setPosition(snapshot.position.x, snapshot.position.y, snapshot.position.z);
                transform.setRotation(snapshot.rotation);
                transform.setUniformScale(snapshot.scale.x);
            });
        }
    }

    private static TransformSnapshot snapshotOf(Transform3D transform) {
        return new TransformSnapshot(
                new Vector3f(transform.position()),
                new Quaternionf(transform.rotation()),
                new Vector3f(transform.scale()));
    }

    private record TransformSnapshot(Vector3f position, Quaternionf rotation, Vector3f scale) {
    }
}
