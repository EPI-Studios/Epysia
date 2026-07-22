package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.PixelColor;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.vfx.ParticleEffect;

public final class VfxExamplesPixelCheck {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;
    private static final float SAMPLE_EFFECT_SECONDS =
            VfxExampleScene.SPARKS_DURATION * 2.0f + VfxExampleScene.SPARKS_BURST_INTERVAL * 3.5f;
    private static final long DEADLINE_SECONDS = 60L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int SAMPLE_STEP = 3;
    private static final int MINIMUM_MATCHES = 30;
    private static final int BACKGROUND_PROBE_X = 6;
    private static final int BACKGROUND_PROBE_Y = 6;
    private static final float FLAME_MINIMUM_RED = 0.40f;
    private static final float FLAME_RED_OVER_BLUE = 0.24f;
    private static final float FLAME_GREEN_OVER_BLUE = 0.08f;
    private static final float SPARK_MINIMUM_RED = 0.59f;
    private static final float SPARK_RED_OVER_BLUE = 0.28f;
    private static final float ARCANE_MINIMUM_BLUE = 0.40f;
    private static final float ARCANE_BLUE_OVER_RED = 0.24f;
    private static final float ARCANE_BLUE_OVER_GREEN = 0.10f;
    private static final float SMOKE_MINIMUM_BRIGHTNESS = 0.25f;
    private static final float LIT_MINIMUM_BRIGHTNESS = 0.10f;

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
        boolean matches(float red, float green, float blue);
    }

    private record RegionCounts(int matches, int lit) {
    }

    private static ColorTest testFor(String name) {
        return switch (name) {
            case "Fire" -> VfxExamplesPixelCheck::isFlame;
            case "Smoke" -> VfxExamplesPixelCheck::isSmoke;
            case "Sparks" -> VfxExamplesPixelCheck::isSpark;
            default -> VfxExamplesPixelCheck::isArcane;
        };
    }

    private static boolean isFlame(float red, float green, float blue) {
        return red > FLAME_MINIMUM_RED && red > blue + FLAME_RED_OVER_BLUE
                && green > blue + FLAME_GREEN_OVER_BLUE;
    }

    private static boolean isSpark(float red, float green, float blue) {
        return red > SPARK_MINIMUM_RED && red > blue + SPARK_RED_OVER_BLUE && green * 10.0f > blue * 14.0f;
    }

    private static boolean isArcane(float red, float green, float blue) {
        return blue > ARCANE_MINIMUM_BLUE && blue > red + ARCANE_BLUE_OVER_RED
                && blue > green + ARCANE_BLUE_OVER_GREEN;
    }

    private static boolean isSmoke(float red, float green, float blue) {
        float brightest = Math.max(red, Math.max(green, blue));
        float darkest = Math.min(red, Math.min(green, blue));
        return brightest > SMOKE_MINIMUM_BRIGHTNESS && (brightest - darkest) * 100.0f < brightest * 22.0f
                && blue * 100.0f >= red * 80.0f;
    }

    private static boolean isLit(float red, float green, float blue) {
        return Math.max(red, Math.max(green, blue)) > LIT_MINIMUM_BRIGHTNESS;
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
        private final long deadlineNanos = System.nanoTime() + DEADLINE_SECONDS * NANOS_PER_SECOND;
        private RenderBackend backend;
        private PixelColor background = new PixelColor(0.0f, 0.0f, 0.0f, 1.0f);

        private PixelCheckSystem(EpysiaEngine engine) {
            this.engine = engine;
        }

        @Override
        public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
            this.backend = renderBackend;
        }

        @Override
        public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
            float effectSeconds = pacemakerSeconds(scene);
            if (effectSeconds < SAMPLE_EFFECT_SECONDS) {
                requireDeadline(effectSeconds);
                return;
            }
            System.out.println("[vfx-examples] sampling at effect time "
                    + String.format("%.3f", effectSeconds) + "s");
            readBackground();
            report(countAll());
        }

        private void requireDeadline(float effectSeconds) {
            if (System.nanoTime() < deadlineNanos) {
                return;
            }
            System.out.println("[vfx-examples] FAIL: effect clock stalled at "
                    + String.format("%.3f", effectSeconds) + "s after " + DEADLINE_SECONDS + "s");
            System.exit(1);
        }

        private float pacemakerSeconds(Scene scene) {
            return scene.gameObjects().stream()
                    .filter(candidate -> VfxExampleScene.PACEMAKER_NAME.equals(candidate.name()))
                    .flatMap(candidate -> candidate.getComponent(ParticleEffect.class).stream())
                    .findFirst()
                    .map(ParticleEffect::elapsedSeconds)
                    .orElse(0.0f);
        }

        private void readBackground() {
            background = rawPixel(BACKGROUND_PROBE_X, BACKGROUND_PROBE_Y);
            System.out.println("[vfx-examples] background reference: " + background);
        }

        private PixelColor rawPixel(int x, int y) {
            PostProcessSystem postProcess = engine.renderSystem(PostProcessSystem.class);
            return backend.readPixelFloat(postProcess.sceneTarget(), x, y);
        }

        private int countAll() {
            int failures = 0;
            for (VfxExampleScene.Placement placement : VfxExampleScene.placements()) {
                RegionCounts counts = scan(placement);
                System.out.println("[vfx-examples] " + placement.name() + " matching pixels: "
                        + counts.matches() + " (lit " + counts.lit() + ")");
                failures += counts.matches() >= MINIMUM_MATCHES ? 0 : 1;
            }
            return failures;
        }

        private RegionCounts scan(VfxExampleScene.Placement placement) {
            ColorTest test = testFor(placement.name());
            int centerX = Math.round(screenX(placement.x()));
            int centerY = Math.round(screenY(VfxExampleScene.BASE_Y + placement.sampleHeight()));
            int extent = placement.regionHalfExtent();
            RegionCounts counts = new RegionCounts(0, 0);
            for (int offsetY = -extent; offsetY <= extent; offsetY += SAMPLE_STEP) {
                counts = scanRow(counts, test, centerX, centerY + offsetY, extent);
            }
            return counts;
        }

        private RegionCounts scanRow(RegionCounts counts, ColorTest test, int centerX, int y, int extent) {
            int matches = counts.matches();
            int lit = counts.lit();
            for (int offsetX = -extent; offsetX <= extent; offsetX += SAMPLE_STEP) {
                PixelColor color = rawPixel(centerX + offsetX, y);
                float red = above(color.red(), background.red());
                float green = above(color.green(), background.green());
                float blue = above(color.blue(), background.blue());
                matches += test.matches(red, green, blue) ? 1 : 0;
                lit += isLit(red, green, blue) ? 1 : 0;
            }
            return new RegionCounts(matches, lit);
        }

        private static float above(float channel, float backgroundChannel) {
            return Math.max(0.0f, channel - backgroundChannel);
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
