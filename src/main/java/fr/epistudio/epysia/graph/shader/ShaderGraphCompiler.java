package fr.epistudio.epysia.graph.shader;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.GraphValues;
import fr.epistudio.epysia.graph.PinDefinition;
import fr.epistudio.epysia.graph.PinType;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ShaderGraphCompiler {

    public static final String SURFACE_GENERATED_SUFFIX = ".surf.glsl";
    public static final String POST_GENERATED_SUFFIX = ".post.glsl";

    private static final String SURFACE_VERTEX_SIGNATURE =
            "void surfaceVertex(inout vec3 worldPosition, in vec3 localPosition, in vec3 worldNormal, "
                    + "in vec2 uv, in float time)";
    private static final String SURFACE_COLOR_SIGNATURE =
            "void surfaceColor(inout vec4 albedoColor, inout float metallic, inout float roughness, "
                    + "inout vec3 emissive, in vec2 uv, in vec3 worldPosition, in float time)";
    private static final String POST_SIGNATURE = "vec4 postEffect(vec4 sceneColor, vec2 uv)";
    private static final String UNLIT_COLOR_LOCAL = "graphUnlitColor";
    private static final String PREVIEW_VALUE_LOCAL = "graphPreviewValue";
    private static final String PREVIEW_RGB_LOCAL = "graphPreviewRgb";
    private static final String PREVIEW_CHECKER_NAME = "graphPreviewChecker";
    private static final String PREVIEW_CHECKER_FUNCTION = """
            vec3 graphPreviewChecker(vec2 previewUv) {
                vec2 cell = floor(previewUv * 8.0);
                float parity = mod(cell.x + cell.y, 2.0);
                return mix(vec3(0.32, 0.32, 0.34), vec3(0.52, 0.52, 0.55), parity);
            }""";

    private final GraphNodeRegistry registry = GraphNodeRegistry.withBuiltins();

    public String compile(GraphAsset asset, String sourceName) {
        return switch (asset.kind()) {
            case SHADER_SURFACE -> compileSurface(asset, sourceName);
            case SHADER_POST -> compilePost(asset, sourceName);
            default -> throw new EpysiaException("Graph kind " + asset.kind() + " is not a shader graph");
        };
    }

    public String compilePreview(GraphAsset asset, int nodeId, String pinName, String sourceName) {
        return switch (asset.kind()) {
            case SHADER_SURFACE -> compilePreviewSurface(asset, nodeId, pinName, sourceName);
            case SHADER_POST -> compilePreviewPost(asset, nodeId, pinName, sourceName);
            default -> throw new EpysiaException("Graph kind " + asset.kind() + " is not a shader graph");
        };
    }

    private String compilePreviewSurface(GraphAsset asset, int nodeId, String pinName, String sourceName) {
        ShaderSharedDeclarations shared = new ShaderSharedDeclarations();
        ShaderStagePass pass = new ShaderStagePass(asset, registry, ShaderStage.SURFACE_FRAGMENT, shared);
        emitPreviewValue(asset, pass, shared, nodeId, pinName);
        pass.appendStatement("emissive = " + PREVIEW_RGB_LOCAL + ";");
        pass.appendStatement("albedoColor = vec4(0.0, 0.0, 0.0, 1.0);");
        pass.appendStatement("metallic = 1.0;");
        pass.appendStatement("roughness = 1.0;");
        return assembleSurface(sourceName, shared, "", pass.body());
    }

    private String compilePreviewPost(GraphAsset asset, int nodeId, String pinName, String sourceName) {
        ShaderSharedDeclarations shared = new ShaderSharedDeclarations();
        ShaderStagePass pass = new ShaderStagePass(asset, registry, ShaderStage.POST, shared);
        emitPreviewValue(asset, pass, shared, nodeId, pinName);
        StringBuilder out = new StringBuilder(header(sourceName, shared));
        out.append(POST_SIGNATURE).append(" {\n").append(pass.body());
        out.append("    return vec4(").append(PREVIEW_RGB_LOCAL).append(", 1.0);\n}\n");
        return out.toString();
    }

    private void emitPreviewValue(GraphAsset asset, ShaderStagePass pass, ShaderSharedDeclarations shared,
                                  int nodeId, String pinName) {
        requirePreviewNode(asset, nodeId);
        shared.declare(PREVIEW_CHECKER_NAME, PREVIEW_CHECKER_FUNCTION);
        ShaderExpression value = pass.outputExpression(nodeId, pinName);
        pass.appendStatement("vec4 " + PREVIEW_VALUE_LOCAL + " = " + previewVector4(value) + ";");
        pass.appendStatement("vec3 " + PREVIEW_RGB_LOCAL + " = mix(" + PREVIEW_CHECKER_NAME + "(uv), "
                + PREVIEW_VALUE_LOCAL + ".rgb, clamp(" + PREVIEW_VALUE_LOCAL + ".a, 0.0, 1.0));");
    }

    private static void requirePreviewNode(GraphAsset asset, int nodeId) {
        GraphNode node = asset.findNode(nodeId)
                .orElseThrow(() -> new EpysiaException("Preview references missing node " + nodeId));
        if (ShaderNodes.isOutput(node.typeKey())) {
            throw new EpysiaException("Output nodes have no previewable value");
        }
    }

    private static String previewVector4(ShaderExpression value) {
        return switch (value.type()) {
            case FLOAT -> "vec4(vec3(" + value.code() + "), 1.0)";
            case VECTOR2 -> "vec4(" + value.code() + ", 0.0, 1.0)";
            case VECTOR3 -> "vec4(" + value.code() + ", 1.0)";
            case VECTOR4 -> value.code();
            default -> throw new EpysiaException("Pin type " + value.type() + " cannot be previewed");
        };
    }

    public static String generatedSuffix(GraphAsset asset) {
        return switch (asset.kind()) {
            case SHADER_SURFACE -> SURFACE_GENERATED_SUFFIX;
            case SHADER_POST -> POST_GENERATED_SUFFIX;
            default -> throw new EpysiaException("Graph kind " + asset.kind() + " is not a shader graph");
        };
    }

    private String compileSurface(GraphAsset asset, String sourceName) {
        GraphNode output = outputNode(asset, ShaderNodes.OUTPUT_SURFACE);
        ShaderSharedDeclarations shared = new ShaderSharedDeclarations();
        ShaderStagePass fragmentPass = new ShaderStagePass(asset, registry, ShaderStage.SURFACE_FRAGMENT, shared);
        emitSurfaceFragment(asset, output, fragmentPass);
        ShaderStagePass vertexPass = new ShaderStagePass(asset, registry, ShaderStage.SURFACE_VERTEX, shared);
        emitSurfaceVertex(asset, output, vertexPass);
        return assembleSurface(sourceName, shared, vertexPass.body(), fragmentPass.body());
    }

    private void emitSurfaceFragment(GraphAsset asset, GraphNode output, ShaderStagePass pass) {
        if (isUnlit(output)) {
            emitUnlitFragment(asset, output, pass);
            return;
        }
        assignIfConnected(asset, output, pass, ShaderNodes.ALBEDO_PIN, PinType.VECTOR4, "albedoColor");
        assignIfConnected(asset, output, pass, ShaderNodes.METALLIC_PIN, PinType.FLOAT, "metallic");
        assignIfConnected(asset, output, pass, ShaderNodes.ROUGHNESS_PIN, PinType.FLOAT, "roughness");
        assignIfConnected(asset, output, pass, ShaderNodes.EMISSIVE_PIN, PinType.VECTOR3, "emissive");
    }

    private void emitUnlitFragment(GraphAsset asset, GraphNode output, ShaderStagePass pass) {
        Optional<String> color = connectedExpression(asset, output, pass, ShaderNodes.ALBEDO_PIN, PinType.VECTOR4);
        Optional<String> extraEmissive = connectedExpression(asset, output, pass,
                ShaderNodes.EMISSIVE_PIN, PinType.VECTOR3);
        pass.appendStatement("vec4 " + UNLIT_COLOR_LOCAL + " = " + color.orElse("albedoColor") + ";");
        pass.appendStatement("emissive = " + UNLIT_COLOR_LOCAL + ".rgb"
                + extraEmissive.map(expression -> " + " + expression).orElse("") + ";");
        pass.appendStatement("albedoColor = vec4(0.0, 0.0, 0.0, " + UNLIT_COLOR_LOCAL + ".a);");
        pass.appendStatement("metallic = 1.0;");
        pass.appendStatement("roughness = 1.0;");
    }

    private static boolean isUnlit(GraphNode output) {
        return ShaderNodes.MASTER_UNLIT.equals(GraphValues.asString(
                output.values().getOrDefault(ShaderNodes.MASTER_SETTING, ShaderNodes.MASTER_LIT)));
    }

    private void emitSurfaceVertex(GraphAsset asset, GraphNode output, ShaderStagePass pass) {
        connectedExpression(asset, output, pass, ShaderNodes.WORLD_POSITION_OFFSET_PIN, PinType.VECTOR3)
                .ifPresent(expression -> pass.appendStatement("worldPosition += " + expression + ";"));
    }

    private void assignIfConnected(GraphAsset asset, GraphNode output, ShaderStagePass pass,
                                   String pinName, PinType type, String target) {
        connectedExpression(asset, output, pass, pinName, type)
                .ifPresent(expression -> pass.appendStatement(target + " = " + expression + ";"));
    }

    private Optional<String> connectedExpression(GraphAsset asset, GraphNode output, ShaderStagePass pass,
                                                 String pinName, PinType type) {
        Optional<GraphEdge> edge = asset.edgeInto(output.id(), pinName);
        if (edge.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pass.outputExpression(edge.get().fromNode(), edge.get().fromPin())
                .promoteTo(type).code());
    }

    private String compilePost(GraphAsset asset, String sourceName) {
        GraphNode output = outputNode(asset, ShaderNodes.OUTPUT_POST);
        ShaderSharedDeclarations shared = new ShaderSharedDeclarations();
        ShaderStagePass pass = new ShaderStagePass(asset, registry, ShaderStage.POST, shared);
        String result = connectedExpression(asset, output, pass, ShaderNodes.COLOR_PIN, PinType.VECTOR4)
                .orElse("sceneColor");
        StringBuilder out = new StringBuilder(header(sourceName, shared));
        out.append(POST_SIGNATURE).append(" {\n").append(pass.body());
        out.append("    return ").append(result).append(";\n}\n");
        return out.toString();
    }

    private static String assembleSurface(String sourceName, ShaderSharedDeclarations shared,
                                          String vertexBody, String fragmentBody) {
        StringBuilder out = new StringBuilder(header(sourceName, shared));
        out.append(SURFACE_VERTEX_SIGNATURE).append(" {\n").append(vertexBody).append("}\n\n");
        out.append(SURFACE_COLOR_SIGNATURE).append(" {\n").append(fragmentBody).append("}\n");
        return out.toString();
    }

    private static String header(String sourceName, ShaderSharedDeclarations shared) {
        StringBuilder out = new StringBuilder();
        out.append("// Generated from ").append(sourceName)
                .append(" by the Epysia shader graph. Edit the graph; this file is overwritten on save.\n");
        if (shared.noiseRequired()) {
            out.append(ShaderSharedDeclarations.NOISE_INCLUDE).append('\n');
        }
        String declarations = shared.block();
        if (!declarations.isEmpty()) {
            out.append(declarations);
        }
        return out.append('\n').toString();
    }

    private static GraphNode outputNode(GraphAsset asset, String outputTypeKey) {
        List<GraphNode> outputs = asset.nodesOfType(outputTypeKey);
        return outputs.stream()
                .min(Comparator.comparingInt(GraphNode::id))
                .orElseThrow(() -> new EpysiaException(
                        "Shader graph is missing its output node (" + outputTypeKey + ")"));
    }
}
