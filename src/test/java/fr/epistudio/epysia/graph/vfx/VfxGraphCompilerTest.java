package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.vfx.VfxGraphCompiler.VfxCompiledSources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxGraphCompilerTest {

    private static VfxGraphCompiler compiler() {
        return new VfxGraphCompiler(commonSource());
    }

    private static String commonSource() {
        try (InputStream stream = VfxGraphCompilerTest.class
                .getResourceAsStream("/shaders/vfx/particle_common.glsl")) {
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
