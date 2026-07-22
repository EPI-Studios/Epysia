package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.NodeDefinition;
import fr.epistudio.epysia.graph.PinDefinition;
import fr.epistudio.epysia.graph.vfx.VfxGraphCompiler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxGraphTemplateTest {

    private static final String TEMPLATE_RESOURCE = "/templates/NewVfxGraph.epygraph";

    @Test
    void templateParsesWithRegisteredNodesAndPins() {
        GraphAsset asset = new GraphJsonCodec().read(resource(TEMPLATE_RESOURCE));
        GraphNodeRegistry registry = GraphNodeRegistry.withBuiltins();
        assertEquals(GraphKind.VFX, asset.kind());
        for (GraphNode node : asset.nodes()) {
            assertTrue(registry.find(node.typeKey()).isPresent(), node.typeKey());
        }
        for (GraphEdge edge : asset.edges()) {
            assertTrue(hasPin(registry, asset, edge.fromNode(), edge.fromPin(), true), edge.fromPin());
            assertTrue(hasPin(registry, asset, edge.toNode(), edge.toPin(), false), edge.toPin());
        }
    }

    @Test
    void templateCompilesWithCurveGradientAndShape() {
        GraphAsset asset = new GraphJsonCodec().read(resource(TEMPLATE_RESOURCE));
        VfxGraphCompiler compiler = new VfxGraphCompiler(shaderSource("particle_common.glsl"),
                shaderSource("particle_shapes.glsl"), shaderSource("particle_noise.glsl"));
        VfxGraphCompiler.VfxCompiledSources sources = compiler.compile(asset, "NewVfxGraph.epygraph");
        assertEquals(45.0f, sources.spawnRatePerSecond());
        assertTrue(sources.spawnCompute().contains("shapeCone"));
        assertTrue(sources.updateCompute().contains("sampleGradient"));
        assertTrue(sources.updateCompute().contains("sampleCurve"));
    }

    private static boolean hasPin(GraphNodeRegistry registry, GraphAsset asset, int nodeId,
                                  String pinName, boolean output) {
        Optional<GraphNode> node = asset.findNode(nodeId);
        if (node.isEmpty() || registry.find(node.get().typeKey()).isEmpty()) {
            return false;
        }
        NodeDefinition definition = registry.find(node.get().typeKey()).get();
        List<PinDefinition> pins = output ? definition.outputPins() : definition.inputPins();
        return pins.stream().anyMatch(pin -> pin.name().equals(pinName));
    }

    private static String resource(String path) {
        try (InputStream stream = VfxGraphTemplateTest.class.getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String shaderSource(String fileName) {
        return resource("/shaders/vfx/" + fileName);
    }
}
