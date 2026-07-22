package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.mesh.MeshUploader;
import fr.epistudio.epysia.render.mesh.PlaneMesh;
import fr.epistudio.epysia.scene.Scene;

public final class VfxShowcase {

    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    private static final float GROUND_SIZE = 40.0f;

    private VfxShowcase() {
    }

    public static void main(String[] arguments) {
        VfxExampleGraphs.validateAll();
        StandaloneRunner.runStandalone("Epysia VFX Showcase", WIDTH, HEIGHT, VfxShowcase::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        scene.addGameObject(VfxExampleScene.buildCamera());
        scene.addGameObject(buildSun());
        scene.addGameObject(buildGround(services));
        for (VfxExampleScene.Placement placement : VfxExampleScene.placements()) {
            scene.addGameObject(VfxExampleScene.buildEffect(placement));
        }
    }

    private static GameObject buildSun() {
        GameObject sun = new GameObject("sun");
        Transform3D transform = new Transform3D();
        transform.lookAt(-0.4f, -1.0f, -0.3f, 0.0f, 1.0f, 0.0f);
        sun.addComponent(transform);
        sun.addComponent(new DirectionalLight().setColor(0.55f, 0.6f, 0.75f).setIntensity(0.45f)
                .setAmbient(0.05f, 0.05f, 0.09f));
        return sun;
    }

    private static GameObject buildGround(EngineServices services) {
        GameObject ground = new GameObject("ground");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, VfxExampleScene.BASE_Y - 0.05f, VfxExampleScene.DEPTH_Z);
        ground.addComponent(transform);
        LitMaterial material = new LitMaterial();
        material.setBaseColor(0.13f, 0.12f, 0.12f);
        material.setRoughness(0.92f);
        ground.addComponent(new MeshRenderer()
                .setMesh(MeshUploader.upload(services.renderBackend(), PlaneMesh.data(GROUND_SIZE, 1.0f)))
                .setMaterial(material));
        return ground;
    }
}
