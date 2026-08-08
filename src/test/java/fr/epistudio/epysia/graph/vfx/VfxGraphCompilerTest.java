package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.vfx.VfxGraphCompiler.VfxCompiledSources;
import fr.epistudio.epysia.vfx.lut.VfxCurve;
import fr.epistudio.epysia.vfx.lut.VfxGradient;
import fr.epistudio.epysia.vfx.lut.VfxLutPack;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxGraphCompilerTest {
    private static VfxGraphCompiler compiler() {
        return new VfxGraphCompiler(shaderSource("particle_common.glsl"),
                shaderSource("particle_shapes.glsl"), shaderSource("particle_noise.glsl"));
    }

    private static String shaderSource(String fileName) {
        try (InputStream stream = VfxGraphCompilerTest.class
                .getResourceAsStream("/shaders/vfx/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static GraphAsset vfxGraph() {
        GraphAsset asset = new GraphAsset();
        asset.setKind(GraphKind.VFX);
        return asset;
    }

    private static String mainBody(String source) {
        int start = source.indexOf("void main()");
        assertTrue(start >= 0, "compiled source has no main function");
        return source.substring(start);
    }

    private static void assertBalancedBraces(String source) {
        long opening = source.chars().filter(character -> character == '{').count();
        long closing = source.chars().filter(character -> character == '}').count();
        assertEquals(opening, closing);
    }

    @Test
    void emptyGraphCompilesWithDefaults() {
        VfxCompiledSources sources = compiler().compile(vfxGraph(), "empty.epygraph");
        assertEquals(100.0f, sources.spawnRatePerSecond());
        assertTrue(sources.spawnCompute().contains("EffectUbo"));
        assertTrue(sources.spawnCompute().contains("hashFloat"));
        assertTrue(sources.updateCompute().contains("atomicAdd(instanceCount, 1u)"));
        assertTrue(sources.fragmentBody().contains("falloff"));
        assertBalancedBraces(sources.spawnCompute());
        assertBalancedBraces(sources.updateCompute());
    }

    @Test
    void coneDirectionWiresIntoSpawnVelocity() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_PARTICLE, 0.0f, 0.0f);
        GraphNode cone = asset.addNode(VfxNodes.CONE_DIRECTION, 0.0f, 0.0f);
        cone.values().put(VfxNodes.ANGLE_SETTING, 40.0f);
        asset.edges().add(new GraphEdge(cone.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.VELOCITY_PIN));
        VfxCompiledSources sources = compiler().compile(asset, "cone.epygraph");
        assertTrue(sources.spawnCompute().contains("coneDirection(normalize(vec3("));
        assertTrue(sources.spawnCompute().contains("40.000000"));
    }

    @Test
    void ageNormalizedWiresIntoKill() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_UPDATE, 0.0f, 0.0f);
        GraphNode age = asset.addNode(VfxNodes.PARTICLE_AGE_NORMALIZED, 0.0f, 0.0f);
        asset.edges().add(new GraphEdge(age.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.KILL_PIN));
        VfxCompiledSources sources = compiler().compile(asset, "kill.epygraph");
        assertTrue(sources.updateCompute().contains("(ageNormalized) > 0.5"));
    }

    @Test
    void curveNodesEmitTheLutIndexTheBakeAssigns() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_UPDATE, 0.0f, 0.0f);
        GraphNode size = asset.addNode(VfxNodes.CURVE, 0.0f, 0.0f);
        GraphNode color = asset.addNode(VfxNodes.GRADIENT, 0.0f, 0.0f);
        GraphNode kill = asset.addNode(VfxNodes.CURVE, 0.0f, 0.0f);
        size.values().put(VfxNodes.CURVE_SETTING, VfxCurve.linear(0.0f, 1.0f).encode());
        kill.values().put(VfxNodes.CURVE_SETTING, VfxCurve.constant(0.25f).encode());
        color.values().put(VfxNodes.GRADIENT_SETTING, VfxGradient.opaqueWhite().encode());
        asset.edges().add(new GraphEdge(size.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.SIZE_PIN));
        asset.edges().add(new GraphEdge(color.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.COLOR_PIN));
        asset.edges().add(new GraphEdge(kill.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.KILL_PIN));
        VfxCompiledSources sources = compiler().compile(asset, "lut.epygraph");
        assertEquals(2, VfxLutPack.build(asset).curveCount());
        assertTrue(sources.updateCompute().contains("sampleCurve(%d, ageNormalized)".formatted(
                VfxLutPack.curveIndexOf(asset, size.id(), VfxNodes.CURVE_SETTING))));
        assertTrue(sources.updateCompute().contains("sampleCurve(%d, ageNormalized)".formatted(
                VfxLutPack.curveIndexOf(asset, kill.id(), VfxNodes.CURVE_SETTING))));
        assertTrue(sources.updateCompute().contains("sampleGradient(%d, ageNormalized)".formatted(
                VfxLutPack.gradientIndexOf(asset, color.id(), VfxNodes.GRADIENT_SETTING))));
    }

    private static Map<String, String> shapeCalls() {
        return Map.of(
                VfxNodes.SHAPE_CONE, "shapeCone(1.000000, 1.000000, 360.000000, 25.000000, spawnKey)",
                VfxNodes.SHAPE_SPHERE, "shapeSphere(1.000000, 1.000000, spawnKey)",
                VfxNodes.SHAPE_HEMISPHERE, "shapeHemisphere(1.000000, 1.000000, spawnKey)",
                VfxNodes.SHAPE_BOX, "shapeBox(vec3(0.500000, 0.500000, 0.500000), 1.000000, spawnKey)",
                VfxNodes.SHAPE_CIRCLE, "shapeCircle(1.000000, 1.000000, 360.000000, spawnKey)",
                VfxNodes.SHAPE_CYLINDER, "shapeCylinder(1.000000, 1.000000, 1.000000, 360.000000, spawnKey)",
                VfxNodes.SHAPE_DOT, "shapeDot(spawnKey)",
                VfxNodes.SHAPE_EDGE, "shapeEdge(1.000000, spawnKey)");
    }

    private static String shapeSpawnBody(String mode) {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_PARTICLE, 0.0f, 0.0f);
        GraphNode shape = asset.addNode(VfxNodes.SHAPE, 0.0f, 0.0f);
        shape.values().put(VfxNodes.SHAPE_SETTING, mode);
        asset.edges().add(new GraphEdge(shape.id(), VfxNodes.POSITION_PIN,
                output.id(), VfxNodes.POSITION_PIN));
        asset.edges().add(new GraphEdge(shape.id(), VfxNodes.DIRECTION_PIN,
                output.id(), VfxNodes.VELOCITY_PIN));
        return mainBody(compiler().compile(asset, "shape.epygraph").spawnCompute());
    }

    @Test
    void everyShapeModeEmitsItsOwnCall() {
        Map<String, String> expectedCalls = shapeCalls();
        for (Map.Entry<String, String> expected : expectedCalls.entrySet()) {
            String body = shapeSpawnBody(expected.getKey());
            assertTrue(body.contains("emitterSpawnPosition(" + expected.getValue() + ".position)"),
                    expected.getKey() + " position: " + body);
            assertTrue(body.contains(expected.getValue() + ".direction"),
                    expected.getKey() + " direction: " + body);
            for (Map.Entry<String, String> other : expectedCalls.entrySet()) {
                if (!other.getKey().equals(expected.getKey())) {
                    assertFalse(body.contains(callName(other.getValue())),
                            expected.getKey() + " leaked " + other.getKey());
                }
            }
        }
    }

    private static String callName(String call) {
        return call.substring(0, call.indexOf('(') + 1);
    }

    @Test
    void emptyGraphMainBodyCallsNoShapeFunction() {
        String body = mainBody(compiler().compile(vfxGraph(), "empty.epygraph").spawnCompute());
        for (String call : shapeCalls().values()) {
            assertFalse(body.contains(callName(call)), call);
        }
    }

    @Test
    void unwiredSpawnPositionLandsOnTheEmitterExactlyOnce() {
        String body = mainBody(compiler().compile(vfxGraph(), "empty.epygraph").spawnCompute());
        assertTrue(body.contains("emitterSpawnPosition(vec3(0.0, 0.0, 0.0))"), body);
        assertEquals(0, occurrences(body, "effect.emitterPositionDelta.xyz"), body);
    }

    @Test
    void wiredSpawnPositionComposesWithTheEmitterWorldPosition() {
        String body = shapeSpawnBody(VfxNodes.SHAPE_SPHERE);
        assertTrue(body.contains(
                "vec4(emitterSpawnPosition(shapeSphere(1.000000, 1.000000, spawnKey).position), 0.0)"), body);
        assertEquals(1, occurrences(body, "emitterSpawnPosition("), body);
    }

    @Test
    void compiledUpdateAppliesTheSimulationSpaceOffset() {
        String body = mainBody(compiler().compile(vfxGraph(), "empty.epygraph").updateCompute());
        assertTrue(body.contains("particle.positionAge.xyz + velocity * deltaTime"), body);
        assertEquals(1, occurrences(body, "simulationSpaceOffset()"), body);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }

    @Test
    void mixedFloatAndVectorPinsConvertExplicitly() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_PARTICLE, 0.0f, 0.0f);
        GraphNode scalar = asset.addNode(VfxNodes.CONSTANT, 0.0f, 0.0f);
        GraphNode gradient = asset.addNode(VfxNodes.GRADIENT, 0.0f, 0.0f);
        scalar.values().put(VfxNodes.VALUE_X_SETTING, 0.75f);
        gradient.values().put(VfxNodes.GRADIENT_SETTING, VfxGradient.opaqueWhite().encode());
        asset.edges().add(new GraphEdge(scalar.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.COLOR_PIN));
        asset.edges().add(new GraphEdge(scalar.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.POSITION_PIN));
        asset.edges().add(new GraphEdge(gradient.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.SIZE_PIN));
        String spawn = compiler().compile(asset, "mixed.epygraph").spawnCompute();
        assertTrue(spawn.contains("vec4(vec3(0.750000), 1.0)"));
        assertTrue(spawn.contains("vec3(0.750000)"));
        assertTrue(spawn.contains(").x"));
        assertBalancedBraces(spawn);
    }

    @Test
    void wiredSpawnRateEvaluatesOverTheEffectDuration() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_SPAWN_RATE, 0.0f, 0.0f);
        GraphNode curve = asset.addNode(VfxNodes.CURVE, 0.0f, 0.0f);
        curve.values().put(VfxNodes.CURVE_SETTING, VfxCurve.linear(0.0f, 1.0f).encode());
        curve.values().put(VfxNodes.MINIMUM_SETTING, 0.0f);
        curve.values().put(VfxNodes.MAXIMUM_SETTING, 200.0f);
        asset.edges().add(new GraphEdge(curve.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.RATE_PIN));
        VfxCompiledSources sources = compiler().compile(asset, "rate.epygraph");
        assertEquals(0.0f, sources.spawnRateAt(0.0f), 0.001f);
        assertEquals(200.0f, sources.spawnRateAt(1.0f), 0.001f);
        assertEquals(100.0f, sources.spawnRatePerSecond(), 1.0f);
    }

    @Test
    void particleVelocityLetsTheGraphAccumulateGravityItself() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_UPDATE, 0.0f, 0.0f);
        GraphNode velocity = asset.addNode(VfxNodes.PARTICLE_VELOCITY, 0.0f, 0.0f);
        GraphNode gravity = asset.addNode(VfxNodes.CONSTANT, 0.0f, 0.0f);
        GraphNode step = asset.addNode(VfxNodes.MATH_MULTIPLY, 0.0f, 0.0f);
        GraphNode sum = asset.addNode(VfxNodes.MATH_ADD, 0.0f, 0.0f);
        GraphNode deltaTime = asset.addNode(VfxNodes.DELTA_TIME, 0.0f, 0.0f);
        gravity.values().put(VfxNodes.COMPONENTS_SETTING, 3);
        gravity.values().put(VfxNodes.VALUE_Y_SETTING, -9.81f);
        asset.edges().add(new GraphEdge(gravity.id(), VfxNodes.RESULT_PIN, step.id(), VfxNodes.A_PIN));
        asset.edges().add(new GraphEdge(deltaTime.id(), VfxNodes.RESULT_PIN, step.id(), VfxNodes.B_PIN));
        asset.edges().add(new GraphEdge(velocity.id(), VfxNodes.RESULT_PIN, sum.id(), VfxNodes.A_PIN));
        asset.edges().add(new GraphEdge(step.id(), VfxNodes.RESULT_PIN, sum.id(), VfxNodes.B_PIN));
        asset.edges().add(new GraphEdge(sum.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.VELOCITY_PIN));
        String body = mainBody(compiler().compile(asset, "gravity.epygraph").updateCompute());
        assertTrue(body.contains("vec3 velocity = (particle.velocityLifetime.xyz + "
                + "(vec3(0.000000, -9.810000, 0.000000) * vec3(effect.emitterPositionDelta.w)));"), body);
    }

    @Test
    void emptyGraphMainBodyNeverAccumulatesTheGraphAuthoredGravity() {
        String body = mainBody(compiler().compile(vfxGraph(), "empty.epygraph").updateCompute());
        assertFalse(body.contains("vec3 velocity = (particle.velocityLifetime.xyz + "
                + "(vec3(0.000000, -9.810000, 0.000000) * vec3(effect.emitterPositionDelta.w)));"), body);
    }

    @Test
    void particleVelocityIsRejectedOutsideTheUpdateStage() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_PARTICLE, 0.0f, 0.0f);
        GraphNode velocity = asset.addNode(VfxNodes.PARTICLE_VELOCITY, 0.0f, 0.0f);
        asset.edges().add(new GraphEdge(velocity.id(), VfxNodes.RESULT_PIN,
                output.id(), VfxNodes.VELOCITY_PIN));
        EpysiaException error = assertThrows(EpysiaException.class,
                () -> compiler().compile(asset, "spawnVelocity.epygraph"));
        assertEquals("Node reading particle.velocityLifetime.xyz is only available in the update stage.",
                error.getMessage());
    }

    private static GraphAsset noiseGraph(float scrollSpeedY) {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_UPDATE, 0.0f, 0.0f);
        GraphNode noise = asset.addNode(VfxNodes.NOISE, 0.0f, 0.0f);
        noise.values().put(VfxNodes.SCROLL_SPEED_Y_SETTING, scrollSpeedY);
        asset.edges().add(new GraphEdge(noise.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.SIZE_PIN));
        return asset;
    }

    @Test
    void noiseScrollSpeedAdvancesTheSampledPointWithElapsedTime() {
        String body = mainBody(compiler().compile(noiseGraph(0.75f), "noise.epygraph").updateCompute());
        assertTrue(body.contains(
                "perlin3((particle.positionAge.xyz + vec3(0.000000, 0.750000, 0.000000) "
                        + "* effect.effectClock.y) * 1.000000)"), body);
    }

    @Test
    void zeroNoiseScrollSpeedCompilesExactlyLikeAnUnscrolledNoise() {
        GraphAsset unscrolled = vfxGraph();
        GraphNode output = unscrolled.addNode(VfxNodes.OUTPUT_UPDATE, 0.0f, 0.0f);
        GraphNode noise = unscrolled.addNode(VfxNodes.NOISE, 0.0f, 0.0f);
        unscrolled.edges().add(new GraphEdge(noise.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.SIZE_PIN));
        String withoutSetting = compiler().compile(unscrolled, "noise.epygraph").updateCompute();
        String withZeroSetting = compiler().compile(noiseGraph(0.0f), "noise.epygraph").updateCompute();
        assertEquals(withoutSetting, withZeroSetting);
        assertFalse(mainBody(withoutSetting).contains("effect.effectClock.y"), withoutSetting);
        assertTrue(mainBody(withoutSetting).contains("perlin3((particle.positionAge.xyz) * 1.000000)"),
                withoutSetting);
    }

    @Test
    void unwiredSizeYRepeatsTheSizeExpressionOnBothAxes() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_PARTICLE, 0.0f, 0.0f);
        GraphNode size = asset.addNode(VfxNodes.RANDOM_RANGE, 0.0f, 0.0f);
        asset.edges().add(new GraphEdge(size.id(), VfxNodes.RESULT_PIN, output.id(), VfxNodes.SIZE_PIN));
        String body = mainBody(compiler().compile(asset, "size.epygraph").spawnCompute());
        String expression = "randomRange(0.000000, 1.000000, spawnKey, %du)".formatted(size.id());
        assertTrue(body.contains("sizeRotation = vec4(%s, %s, 0.0, 0.0)"
                .formatted(expression, expression)), body);
    }

    @Test
    void wiredSizeYAndRotationDriveTheirOwnComponents() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_PARTICLE, 0.0f, 0.0f);
        output.values().put(VfxNodes.SIZE_PIN, 0.06f);
        output.values().put(VfxNodes.SIZE_Y_PIN, 0.02f);
        output.values().put(VfxNodes.ROTATION_PIN, 45.0f);
        String body = mainBody(compiler().compile(asset, "flake.epygraph").spawnCompute());
        assertTrue(body.contains("sizeRotation = vec4(0.060000, 0.020000, 45.000000, 0.0)"), body);
    }

    @Test
    void angularVelocityIntegratesIntoTheStoredAngle() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_UPDATE, 0.0f, 0.0f);
        output.values().put(VfxNodes.ANGULAR_VELOCITY_PIN, 220.0f);
        String body = mainBody(compiler().compile(asset, "spin.epygraph").updateCompute());
        assertTrue(body.contains("particle.sizeRotation.z + (220.000000) * deltaTime"), body);
        assertTrue(body.contains("particle.sizeRotation.x, particle.sizeRotation.y"), body);
    }

    @Test
    void renderShapeSelectsTheCornerMetric() {
        GraphAsset round = vfxGraph();
        round.addNode(VfxNodes.OUTPUT_RENDER, 0.0f, 0.0f);
        GraphAsset rectangular = vfxGraph();
        rectangular.addNode(VfxNodes.OUTPUT_RENDER, 0.0f, 0.0f)
                .values().put(VfxNodes.SHAPE_SETTING, VfxNodes.RENDER_SHAPE_RECT);
        assertTrue(compiler().compile(round, "round.epygraph").fragmentBody()
                .contains("distanceFromCenter = length(particleCorner)"));
        assertTrue(compiler().compile(rectangular, "rect.epygraph").fragmentBody()
                .contains("distanceFromCenter = max(abs(particleCorner.x), abs(particleCorner.y))"));
    }

    @Test
    void unsupportedNodeThrowsWithItsName() {
        GraphAsset asset = vfxGraph();
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_PARTICLE, 0.0f, 0.0f);
        GraphNode unsupported = asset.addNode("shader.math.add", 0.0f, 0.0f);
        asset.edges().add(new GraphEdge(unsupported.id(), VfxNodes.RESULT_PIN,
                output.id(), VfxNodes.SIZE_PIN));
        EpysiaException error = assertThrows(EpysiaException.class,
                () -> compiler().compile(asset, "unsupported.epygraph"));
        assertTrue(error.getMessage().contains("shader.math.add"));
    }
}
