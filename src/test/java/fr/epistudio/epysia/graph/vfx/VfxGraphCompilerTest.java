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
