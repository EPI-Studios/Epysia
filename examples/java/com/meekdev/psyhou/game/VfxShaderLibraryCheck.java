package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.ComputePipelineDescriptor;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;

import java.util.List;

public final class VfxShaderLibraryCheck {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;
    private static final String VIRTUAL_PATH = "vfx/shader_library_check.comp.glsl";
    private static final String CHECK_SOURCE = """
            #version 430 core
            #include "vfx/particle_common.glsl"
            #include "vfx/particle_shapes.glsl"
            #include "vfx/particle_noise.glsl"

            layout(local_size_x = 64) in;

            void main() {
                uint key = gl_GlobalInvocationID.x;
                ShapeSample cone = shapeCone(0.5, 1.0, 360.0, 25.0, key);
                ShapeSample sphere = shapeSphere(1.0, 1.0, key);
                ShapeSample hemisphere = shapeHemisphere(1.0, 0.0, key);
                ShapeSample box = shapeBox(vec3(0.5, 0.25, 0.5), 0.5, key);
                ShapeSample circle = shapeCircle(1.0, 0.0, 180.0, key);
                ShapeSample cylinder = shapeCylinder(0.5, 0.5, 2.0, 360.0, key);
                ShapeSample origin = shapeDot(key);
                ShapeSample edge = shapeEdge(2.0, key);
                vec3 accumulated = cone.position + cone.direction
                        + sphere.position + sphere.direction
                        + hemisphere.position + hemisphere.direction
                        + box.position + box.direction
                        + circle.position + circle.direction
                        + cylinder.position + cylinder.direction
                        + origin.position + origin.direction
                        + edge.position + edge.direction;
                float scalarNoise = perlin3(accumulated) + fbm3(accumulated * 0.5, 4);
                vec3 curl = curlNoise(accumulated * 0.25);
                if (key >= effect.spawnSeedPool.w) {
                    return;
                }
                particles[key].userExtra = vec4(curl, scalarNoise);
            }
            """;

    private VfxShaderLibraryCheck() {
    }

    public static void main(String[] arguments) {
        StandaloneRunner.runStandalone("VfxShaderLibraryCheck", WIDTH, HEIGHT,
                VfxShaderLibraryCheck::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        engine.addRenderSystem(new ShaderLibraryCheckSystem());
    }

    private static final class ShaderLibraryCheckSystem implements RenderSystem {

        @Override
        public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
            try {
                ShaderLoader shaderLoader = ShaderLoader.autoDetect();
                shaderLoader.putVirtualSource(VIRTUAL_PATH, CHECK_SOURCE);
                String resolved = shaderLoader.load(VIRTUAL_PATH).source();
                requireResolvedIncludes(resolved);
                renderBackend.createComputePipeline(new ComputePipelineDescriptor(resolved, layout()));
            } catch (RuntimeException failure) {
                System.out.println("[vfx-shader-library-check] FAIL: " + failure.getMessage());
                System.exit(1);
            }
            System.out.println("[vfx-shader-library-check] PASS: shapes and noise compile and link");
            System.exit(0);
        }

        private static void requireResolvedIncludes(String resolved) {
            if (resolved.contains("#include")) {
                System.out.println("[vfx-shader-library-check] FAIL: an include was not resolved");
                System.exit(1);
            }
            boolean hasShapes = resolved.contains("ShapeSample shapeCylinder");
            boolean hasNoise = resolved.contains("vec3 curlNoise");
            if (!hasShapes || !hasNoise) {
                System.out.println("[vfx-shader-library-check] FAIL: library sources were not included");
                System.exit(1);
            }
        }

        private static BindingSetLayout layout() {
            return new BindingSetLayout(List.of(
                    new BindingSlot(5, BindingType.STORAGE_BUFFER),
                    new BindingSlot(6, BindingType.STORAGE_BUFFER),
                    new BindingSlot(7, BindingType.STORAGE_BUFFER),
                    new BindingSlot(8, BindingType.STORAGE_BUFFER),
                    new BindingSlot(1, BindingType.UNIFORM_BUFFER)));
        }

        @Override
        public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        }

        @Override
        public void shutdown(RenderBackend renderBackend) {
        }
    }
}
