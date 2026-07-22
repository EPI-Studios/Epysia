package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.scene.Scene;

public final class VfxExamplesPixelCheck {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;
    private static final int CHECK_FRAME = 120;
    private static final int SAMPLE_STEP = 3;
    private static final int MINIMUM_MATCHES = 30;
    private static final int BACKGROUND_PROBE_X = 6;
    private static final int BACKGROUND_PROBE_Y = 6;

    private VfxExamplesPixelCheck() {
    }

    public static void main(String[] arguments) {
        VfxExampleGraphs.validateAll();
        StandaloneRunner.runStandalone("VfxExamplesPixelCheck", WIDTH, HEIGHT,
                VfxExamplesPixelCheck::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        scene.addGameObject(VfxExampleScene.buildCamera());
        for (VfxExampleScene.Placement placement : VfxExampleScene.placements()) {
            scene.addGameObject(VfxExampleScene.buildEffect(placement));
        }
        engine.addRenderSystem(new PixelCheckSystem(engine));
    }

    private interface ColorTest {
        boolean matches(int red, int green, int blue);
    }

    private static ColorTest testFor(String name) {
        return switch (name) {
            case "Fire" -> VfxExamplesPixelCheck::isFlame;
            case "Smoke" -> VfxExamplesPixelCheck::isSmoke;
            case "Sparks" -> VfxExamplesPixelCheck::isSpark;
            default -> VfxExamplesPixelCheck::isArcane;
        };
    }

    private static boolean isFlame(int red, int green, int blue) {
        return red > 100 && red > blue + 60 && green > blue + 20;
    }

    private static boolean isSpark(int red, int green, int blue) {
        return red > 150 && red > blue + 70 && green * 10 > blue * 14;
    }

    private static boolean isArcane(int red, int green, int blue) {
        return blue > 100 && blue > red + 60 && blue > green + 25;
    }

    private static boolean isSmoke(int red, int green, int blue) {
        int brightest = Math.max(red, Math.max(green, blue));
        int darkest = Math.min(red, Math.min(green, blue));
        return brightest > 60 && (brightest - darkest) * 100 < brightest * 22
                && blue * 100 >= red * 80;
    }

    private static float screenX(float worldX) {
        double tangent = Math.tan(Math.toRadians(VfxExampleScene.FIELD_OF_VIEW_DEGREES * 0.5f));
        double aspect = (double) WIDTH / HEIGHT;
        double normalized = worldX / -VfxExampleScene.DEPTH_Z / (tangent * aspect);
        return (float) (WIDTH * 0.5 + normalized * WIDTH * 0.5);
    }

    private static float screenY(float worldY) {
        double tangent = Math.tan(Math.toRadians(VfxExampleScene.FIELD_OF_VIEW_DEGREES * 0.5f));
        double normalized = (worldY - VfxExampleScene.CAMERA_HEIGHT) / -VfxExampleScene.DEPTH_Z / tangent;
        return (float) (HEIGHT * 0.5 + normalized * HEIGHT * 0.5);
    }

    private static final class PixelCheckSystem implements RenderSystem {

        private final EpysiaEngine engine;
        private RenderBackend backend;
        private int frameCount;
        private int backgroundRed;
        private int backgroundGreen;
        private int backgroundBlue;

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
            readBackground();
            report(countAll());
        }

        private void readBackground() {
            int pixel = rawPixel(BACKGROUND_PROBE_X, BACKGROUND_PROBE_Y);
            backgroundRed = pixel >> 16 & 0xFF;
            backgroundGreen = pixel >> 8 & 0xFF;
            backgroundBlue = pixel & 0xFF;
            System.out.println("[vfx-examples] background reference: " + backgroundRed + ", "
                    + backgroundGreen + ", " + backgroundBlue);
        }

        private int rawPixel(int x, int y) {
            PostProcessSystem postProcess = engine.renderSystem(PostProcessSystem.class);
            return backend.readPixelArgb(postProcess.sceneTarget(), x, y);
        }

        private int countAll() {
            int failures = 0;
            for (VfxExampleScene.Placement placement : VfxExampleScene.placements()) {
                int matches = countMatches(placement);
                System.out.println("[vfx-examples] " + placement.name() + " matching pixels: " + matches
                        + " (lit " + countLit(placement) + ")");
                failures += matches >= MINIMUM_MATCHES ? 0 : 1;
            }
            return failures;
        }

        private int countMatches(VfxExampleScene.Placement placement) {
            return scan(placement, testFor(placement.name()));
        }

        private int countLit(VfxExampleScene.Placement placement) {
            return scan(placement, (red, green, blue) -> Math.max(red, Math.max(green, blue)) > 25);
        }

        private int scan(VfxExampleScene.Placement placement, ColorTest test) {
            int centerX = Math.round(screenX(placement.x()));
            int centerY = Math.round(screenY(VfxExampleScene.BASE_Y + placement.sampleHeight()));
            int extent = placement.regionHalfExtent();
            int matches = 0;
            for (int offsetY = -extent; offsetY <= extent; offsetY += SAMPLE_STEP) {
                for (int offsetX = -extent; offsetX <= extent; offsetX += SAMPLE_STEP) {
                    matches += sample(centerX + offsetX, centerY + offsetY, test) ? 1 : 0;
                }
            }
            return matches;
        }

        private boolean sample(int x, int y, ColorTest test) {
            int pixel = rawPixel(x, y);
            return test.matches(above(pixel >> 16 & 0xFF, backgroundRed),
                    above(pixel >> 8 & 0xFF, backgroundGreen),
                    above(pixel & 0xFF, backgroundBlue));
        }

        private static int above(int channel, int background) {
            return Math.max(0, channel - background);
        }

        private void report(int failures) {
            if (failures == 0) {
                System.out.println("[vfx-examples] PASS: all four example effects render their own colors");
                System.exit(0);
            }
            System.out.println("[vfx-examples] FAIL: " + failures + " example effects produced nothing");
            System.exit(1);
        }

        @Override
        public void shutdown(RenderBackend renderBackend) {
        }
    }
}
