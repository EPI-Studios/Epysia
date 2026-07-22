package com.meekdev.psyhou.game;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.vfx.ParticleBurst;
import fr.epistudio.epysia.vfx.ParticleEffect;

import java.util.List;

public final class VfxExampleScene {

    public record Placement(String name, float x, float sampleHeight, int regionHalfExtent,
                            int poolSize, float duration) {
    }

    public static final float BASE_Y = -1.8f;
    public static final float DEPTH_Z = -9.0f;
    public static final float CAMERA_HEIGHT = 0.4f;
    public static final float FIELD_OF_VIEW_DEGREES = 60.0f;

    private static final float SPARKS_BURST_INTERVAL = 0.3f;
    private static final int SPARKS_BURST_COUNT = 220;
    private static final int SPARKS_BURST_CYCLES = 64;

    private VfxExampleScene() {
    }

    public static List<Placement> placements() {
        return List.of(
                new Placement("Smoke", -5.6f, 1.6f, 50, 512, 8.0f),
                new Placement("Fire", -1.9f, 1.0f, 45, 1024, 3.0f),
                new Placement("MagicSwirl", 1.9f, 0.6f, 50, 1024, 4.0f),
                new Placement("Sparks", 5.6f, 0.5f, 70, 1536, 2.0f));
    }

    public static GameObject buildEffect(Placement placement) {
        GameObject effectObject = new GameObject(placement.name());
        Transform3D transform = new Transform3D();
        transform.setPosition(placement.x(), BASE_Y, DEPTH_Z);
        effectObject.addComponent(transform);
        effectObject.addComponent(configure(placement));
        return effectObject;
    }

    private static ParticleEffect configure(Placement placement) {
        ParticleEffect effect = new ParticleEffect()
                .setPoolSize(placement.poolSize())
                .setDuration(placement.duration())
                .setLooping(true)
                .setPrewarm(true)
                .setGraphPath(VfxExampleGraphs.fileOf(placement.name()).toString());
        if ("Sparks".equals(placement.name())) {
            effect.addBurst(new ParticleBurst(SPARKS_BURST_INTERVAL, SPARKS_BURST_COUNT,
                    SPARKS_BURST_CYCLES, SPARKS_BURST_INTERVAL));
        }
        return effect;
    }

    public static GameObject buildCamera() {
        GameObject camera = new GameObject("camera");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, CAMERA_HEIGHT, 0.0f);
        transform.lookAt(0.0f, CAMERA_HEIGHT, DEPTH_Z, 0.0f, 1.0f, 0.0f);
        camera.addComponent(transform);
        camera.addComponent(new Camera3D().setActive(true)
                .setNearFar(0.1f, 100.0f).setFieldOfViewDegrees(FIELD_OF_VIEW_DEGREES));
        return camera;
    }
}
