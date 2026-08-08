package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.NodeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxNodesTest {
    @Test
    void builtinsResolveEveryVfxNode() {
        GraphNodeRegistry registry = GraphNodeRegistry.withBuiltins();
        List<String> typeKeys = List.of(VfxNodes.OUTPUT_SPAWN_RATE, VfxNodes.OUTPUT_PARTICLE,
                VfxNodes.OUTPUT_UPDATE, VfxNodes.OUTPUT_RENDER, VfxNodes.PARTICLE_AGE,
                VfxNodes.PARTICLE_AGE_NORMALIZED, VfxNodes.PARTICLE_SEED, VfxNodes.EMITTER_POSITION,
                VfxNodes.PARTICLE_POSITION, VfxNodes.PARTICLE_VELOCITY, VfxNodes.DELTA_TIME,
                VfxNodes.RANDOM_RANGE, VfxNodes.CONE_DIRECTION);
        for (String typeKey : typeKeys) {
            assertTrue(registry.find(typeKey).isPresent(), typeKey);
        }
        NodeDefinition particleOutput = registry.find(VfxNodes.OUTPUT_PARTICLE).orElseThrow();
        assertEquals(7, particleOutput.inputPins().size());
        assertEquals(0, particleOutput.outputPins().size());
    }

    @Test
    void vfxGraphRoundTripsThroughJson() {
        GraphAsset asset = new GraphAsset();
        asset.setKind(GraphKind.VFX);
        GraphNode output = asset.addNode(VfxNodes.OUTPUT_SPAWN_RATE, 10.0f, 20.0f);
        GraphJsonCodec codec = new GraphJsonCodec();
        GraphAsset decoded = codec.read(codec.write(asset));
        assertEquals(GraphKind.VFX, decoded.kind());
        assertEquals(1, decoded.nodes().size());
        assertEquals(output.typeKey(), decoded.nodes().get(0).typeKey());
    }
}
