package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.vfx.VfxNodes;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.vfx.ParticleEffect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EmbersScreenshot {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int CHECK_FRAME = 90;
    private static final int SCAN_HALF_EXTENT = 60;
    private static final int RED_MARGIN = 40;

    private EmbersScreenshot() {
    }

    public static void main(String[] arguments) {
        StandaloneRunner.runStandalone("EmbersScreenshot", WIDTH, HEIGHT,
                EmbersScreenshot::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        scene.addGameObject(buildCamera());
        scene.addGameObject(buildGraphEffect());
        engine.addRenderSystem(new PixelCheckSystem(engine));
    }

    private static GameObject buildGraphEffect() {
        GameObject effectObject = new GameObject("graph-effect");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, -1.0f, -6.0f);
        effectObject.addComponent(transform);
        effectObject.addComponent(new ParticleEffect().setPoolSize(512)
                .setGraphPath(writeGraphFile().toString()));
        return effectObject;
    }

    private static Path writeGraphFile() {
        Path repoEmbers = Path.of("examples/resources/vfx/Embers.epygraph");
        if (Files.isRegularFile(repoEmbers)) {
            return repoEmbers.toAbsolutePath();
        }
        GraphAsset asset = new GraphAsset();
        asset.setKind(GraphKind.VFX);
        GraphNode spawnRate = asset.addNode(VfxNodes.OUTPUT_SPAWN_RATE, 0.0f, 0.0f);
        spawnRate.values().put(VfxNodes.RATE_PIN, 200.0f);
        GraphNode particleOutput = asset.addNode(VfxNodes.OUTPUT_PARTICLE, 0.0f, 0.0f);
        GraphNode cone = asset.addNode(VfxNodes.CONE_DIRECTION, 0.0f, 0.0f);
        cone.values().put(VfxNodes.ANGLE_SETTING, 30.0f);
        asset.edges().add(new GraphEdge(cone.id(), VfxNodes.RESULT_PIN,
                particleOutput.id(), VfxNodes.VELOCITY_PIN));
        try {
            Path file = Files.createTempFile("vfx-graph-check", ".epygraph");
            Files.writeString(file, new GraphJsonCodec().write(asset));
            return file;
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write the check graph: " + exception.getMessage(), exception);
        }
    }

    private static GameObject buildCamera() {
        GameObject camera = new GameObject("camera");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, 0.0f, 0.0f);
        transform.lookAt(0.0f, 0.0f, -6.0f, 0.0f, 1.0f, 0.0f);
        camera.addComponent(transform);
        camera.addComponent(new Camera3D().setActive(true)
                .setNearFar(0.1f, 100.0f).setFieldOfViewDegrees(65.0f));
        return camera;
    }

    private static final class PixelCheckSystem implements RenderSystem {

        private final EpysiaEngine engine;
        private RenderBackend backend;
        private int frameCount;

        private PixelCheckSystem(EpysiaEngine engine) {
            this.engine = engine;
        }

        @Override
        public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
            this.backend = renderBackend;
        }

        @Override
        public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
            frameCount++;
            if (frameCount != CHECK_FRAME) {
                return;
            }
            saveScreenshot();
            reportAliveCount(scene);
            report(warmPixelCount());
        }

        private void reportAliveCount(Scene scene) {
            fr.epistudio.epysia.vfx.VfxRenderSystem vfx =
                    engine.renderSystem(fr.epistudio.epysia.vfx.VfxRenderSystem.class);
            for (GameObject gameObject : scene.gameObjects()) {
                ParticleEffect effect = gameObject.getComponentOrNull(ParticleEffect.class);
                if (effect != null) {
                    System.out.println("[diag] " + vfx.debugSnapshot(effect));
                }
            }
        }

        private void saveScreenshot() {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(WIDTH, HEIGHT,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
            org.lwjgl.opengl.GL11.glReadPixels(0, 0, WIDTH, HEIGHT,
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixels);
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int base = (y * WIDTH + x) * 4;
                    int red = pixels.get(base) & 0xFF;
                    int green = pixels.get(base + 1) & 0xFF;
                    int blue = pixels.get(base + 2) & 0xFF;
                    image.setRGB(x, HEIGHT - 1 - y, red << 16 | green << 8 | blue);
                }
            }
            try {
                javax.imageio.ImageIO.write(image, "png", new java.io.File("/tmp/claude-1000/-home-meek-Desktop-DEV-Epysia/45ed638e-4fea-4845-812d-ed81bb16dc54/scratchpad/embers.png"));
                System.out.println("[screenshot] saved");
            } catch (java.io.IOException error) {
                System.out.println("[screenshot] failed: " + error.getMessage());
            }
        }

        private int warmPixelCount() {
            PostProcessSystem postProcess = engine.renderSystem(PostProcessSystem.class);
            int warm = 0;
            for (int offsetY = -SCAN_HALF_EXTENT; offsetY <= SCAN_HALF_EXTENT; offsetY += 4) {
                for (int offsetX = -SCAN_HALF_EXTENT; offsetX <= SCAN_HALF_EXTENT; offsetX += 4) {
                    int pixel = backend.readPixelArgb(postProcess.sceneTarget(),
                            WIDTH / 2 + offsetX, HEIGHT / 2 + offsetY);
                    int red = pixel >> 16 & 0xFF;
                    int blue = pixel & 0xFF;
                    if (red > 120 && red > blue + RED_MARGIN) {
                        warm++;
                    }
                }
            }
            return warm;
        }

        private static void report(int warmPixels) {
            System.out.println("[vfx-graph-check] warm pixels in center block: " + warmPixels);
            if (warmPixels > 3) {
                System.out.println("[vfx-graph-check] PASS: graph compiled effect renders");
                System.exit(0);
            }
            System.out.println("[vfx-graph-check] FAIL: graph effect produced nothing");
            System.exit(1);
        }

        @Override
        public void shutdown(RenderBackend renderBackend) {
        }
    }
}
