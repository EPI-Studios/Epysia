package fr.epistudio.epysia.graph.shader;

import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderGraphParameterTest {

    private static GraphAsset surfaceGraph() {
        GraphAsset asset = new GraphAsset();
        asset.setKind(GraphKind.SHADER_SURFACE);
        return asset;
    }

    @Test
    void surfaceFloatParameterCompilesToUniform() {
        GraphAsset asset = surfaceGraph();
        GraphNode output = asset.addNode(ShaderNodes.OUTPUT_SURFACE, 0.0f, 0.0f);
        GraphNode parameter = asset.addNode(ShaderNodes.PARAMETER_FLOAT, 0.0f, 0.0f);
        parameter.values().put(ShaderNodes.NAME_SETTING, "dissolveProgress");
        parameter.values().put(ShaderNodes.VALUE_PIN, 0.25f);
        asset.edges().add(new GraphEdge(parameter.id(), ShaderNodes.RESULT_PIN,
                output.id(), ShaderNodes.METALLIC_PIN));
        String source = new ShaderGraphCompiler().compile(asset, "test.epygraph");
        assertTrue(source.contains("uniform float dissolveProgress;"));
        assertTrue(source.contains("@default 0.25"));
        assertFalse(source.contains("const float dissolveProgress"));
    }

    @Test
    void surfaceColorParameterCompilesToAnnotatedUniform() {
        GraphAsset asset = surfaceGraph();
        GraphNode output = asset.addNode(ShaderNodes.OUTPUT_SURFACE, 0.0f, 0.0f);
        GraphNode parameter = asset.addNode(ShaderNodes.PARAMETER_COLOR, 0.0f, 0.0f);
        parameter.values().put(ShaderNodes.NAME_SETTING, "tintColor");
        asset.edges().add(new GraphEdge(parameter.id(), ShaderNodes.RESULT_PIN,
                output.id(), ShaderNodes.ALBEDO_PIN));
        String source = new ShaderGraphCompiler().compile(asset, "test.epygraph");
        assertTrue(source.contains("uniform vec4 tintColor;"));
        assertTrue(source.contains("@color"));
        assertFalse(source.contains("const vec4 tintColor"));
    }
}
