package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.mesh.MeshUploader;
import fr.epistudio.epysia.render.mesh.PlaneMesh;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.vfx.ParticleEffect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class EmbersDemo {

    private EmbersDemo() {
    }

    public static void main(String[] arguments) {
        StandaloneRunner.runStandalone("Embers Demo", 1280, 720, EmbersDemo::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        scene.addGameObject(buildCamera());
        scene.addGameObject(buildSun());
        scene.addGameObject(buildGround(services));
        scene.addGameObject(buildEmbers());
    }

    private static GameObject buildCamera() {
        GameObject camera = new GameObject("camera");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, 0.6f, 0.0f);
        transform.lookAt(0.0f, 0.2f, -5.0f, 0.0f, 1.0f, 0.0f);
        camera.addComponent(transform);
        camera.addComponent(new Camera3D().setActive(true)
                .setNearFar(0.1f, 100.0f).setFieldOfViewDegrees(60.0f));
        return camera;
    }

    private static GameObject buildSun() {
        GameObject sun = new GameObject("sun");
        Transform3D transform = new Transform3D();
        transform.lookAt(-0.4f, -1.0f, -0.3f, 0.0f, 1.0f, 0.0f);
        sun.addComponent(transform);
        sun.addComponent(new DirectionalLight().setColor(0.6f, 0.65f, 0.8f).setIntensity(0.5f)
                .setAmbient(0.06f, 0.06f, 0.1f));
        return sun;
    }

    private static GameObject buildGround(EngineServices services) {
        GameObject ground = new GameObject("ground");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, -1.2f, -5.0f);
        ground.addComponent(transform);
        LitMaterial material = new LitMaterial();
        material.setBaseColor(0.16f, 0.14f, 0.13f);
        material.setRoughness(0.9f);
        ground.addComponent(new MeshRenderer()
                .setMesh(MeshUploader.upload(services.renderBackend(), PlaneMesh.data(12.0f, 1.0f)))
                .setMaterial(material));
        return ground;
    }

    private static GameObject buildEmbers() {
        GameObject embers = new GameObject("embers");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, -1.1f, -5.0f);
        embers.addComponent(transform);
        ParticleEffect effect = new ParticleEffect().setPoolSize(2048);
        graphPath().ifPresentOrElse(
                path -> effect.setGraphPath(path.toString()),
                () -> effect.setEmissionRate(220.0f));
        embers.addComponent(effect);
        return embers;
    }

    private static Optional<Path> graphPath() {
        Path repoEmbers = Path.of("examples/resources/vfx/Embers.epygraph").toAbsolutePath();
        return Files.isRegularFile(repoEmbers) ? Optional.of(repoEmbers) : Optional.empty();
    }
}
