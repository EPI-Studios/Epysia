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

    @Test
    void everyShapeModeEmitsItsOwnCall() {
        Map<String, String> expectedCalls = Map.of(
                VfxNodes.SHAPE_CONE, "shapeCone(",
                VfxNodes.SHAPE_SPHERE, "shapeSphere(",
                VfxNodes.SHAPE_HEMISPHERE, "shapeHemisphere(",
                VfxNodes.SHAPE_BOX, "shapeBox(",
                VfxNodes.SHAPE_CIRCLE, "shapeCircle(",
                VfxNodes.SHAPE_CYLINDER, "shapeCylinder(",
                VfxNodes.SHAPE_DOT, "shapeDot(",
                VfxNodes.SHAPE_EDGE, "shapeEdge(");
        for (Map.Entry<String, String> expected : expectedCalls.entrySet()) {
            GraphAsset asset = vfxGraph();
            GraphNode output = asset.addNode(VfxNodes.OUTPUT_PARTICLE, 0.0f, 0.0f);
            GraphNode shape = asset.addNode(VfxNodes.SHAPE, 0.0f, 0.0f);
            shape.values().put(VfxNodes.SHAPE_SETTING, expected.getKey());
            asset.edges().add(new GraphEdge(shape.id(), VfxNodes.POSITION_PIN,
                    output.id(), VfxNodes.POSITION_PIN));
            asset.edges().add(new GraphEdge(shape.id(), VfxNodes.DIRECTION_PIN,
                    output.id(), VfxNodes.VELOCITY_PIN));
            String spawn = compiler().compile(asset, "shape.epygraph").spawnCompute();
            assertTrue(spawn.contains(expected.getValue() + ""), expected.getKey());
            assertTrue(spawn.contains(").position"), expected.getKey());
            assertTrue(spawn.contains(").direction"), expected.getKey());
        }
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
