package fr.epistudio.epysia.graph.shader;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.GraphValues;
import fr.epistudio.epysia.graph.PinDefinition;
import fr.epistudio.epysia.graph.PinType;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ShaderStagePass {

    private static final String CUSTOM_FUNCTION_PREFIX = "graphCustomFunction";
    private static final String POST_SAMPLER_PREFIX = "graphTexture";
    private static final String[] COMPONENT_SUFFIXES = {".x", ".y", ".z", ".w"};

    private final GraphAsset asset;
    private final GraphNodeRegistry registry;
    private final ShaderStage stage;
    private final ShaderSharedDeclarations shared;
    private final StringBuilder body = new StringBuilder();
    private final Map<String, ShaderExpression> memo = new HashMap<>();
    private final Set<Integer> inProgress = new HashSet<>();

    ShaderStagePass(GraphAsset asset, GraphNodeRegistry registry, ShaderStage stage,
                    ShaderSharedDeclarations shared) {
        this.asset = asset;
        this.registry = registry;
        this.stage = stage;
        this.shared = shared;
    }

    String body() {
        return body.toString();
    }

    void appendStatement(String statement) {
        body.append("    ").append(statement).append('\n');
    }

    ShaderExpression inputExpression(GraphNode node, PinDefinition pin) {
        Optional<GraphEdge> edge = asset.edgeInto(node.id(), pin.name());
        if (edge.isPresent()) {
            return outputExpression(edge.get().fromNode(), edge.get().fromPin()).promoteTo(pin.type());
        }
        if (ShaderNodes.defaultsToUv(node.typeKey(), pin.name())) {
            return new ShaderExpression(PinType.VECTOR2, "uv");
        }
        return literalExpression(node, pin);
    }

    private static ShaderExpression literalExpression(GraphNode node, PinDefinition pin) {
        PinType type = pin.type() == PinType.NUMERIC ? PinType.FLOAT : pin.type();
        Object value = node.values().containsKey(pin.name())
                ? node.values().get(pin.name())
                : ShaderNodes.defaultPinValue(node.typeKey(), pin);
        return new ShaderExpression(type, literalText(type, value));
    }

    static String literalText(PinType type, Object value) {
        return switch (type) {
            case FLOAT -> floatText(GraphValues.asFloat(value));
            case VECTOR2 -> vectorText(GraphValues.asVector2(value));
            case VECTOR3 -> vectorText(GraphValues.asVector(value));
            case VECTOR4 -> vectorText(GraphValues.asVector4(value));
            default -> throw new EpysiaException("No shader literal for type " + type);
        };
    }

    private static String vectorText(Vector2f vector) {
        return "vec2(" + floatText(vector.x) + ", " + floatText(vector.y) + ")";
    }

    private static String vectorText(Vector3f vector) {
        return "vec3(" + floatText(vector.x) + ", " + floatText(vector.y) + ", " + floatText(vector.z) + ")";
    }

    private static String vectorText(Vector4f vector) {
        return "vec4(" + floatText(vector.x) + ", " + floatText(vector.y) + ", "
                + floatText(vector.z) + ", " + floatText(vector.w) + ")";
    }

    static String floatText(float value) {
        return Float.toString(value);
    }

    ShaderExpression outputExpression(int nodeId, String pinName) {
        ShaderExpression known = memo.get(memoKey(nodeId, pinName));
        if (known != null) {
            return known;
        }
        GraphNode node = asset.findNode(nodeId)
                .orElseThrow(() -> new EpysiaException("Shader graph edge references missing node " + nodeId));
        if (!inProgress.add(nodeId)) {
            throw new EpysiaException("Shader graph contains a cycle through node " + nodeId);
        }
        emitNode(node);
        inProgress.remove(nodeId);
        return requireMemo(node, pinName);
    }

    private ShaderExpression requireMemo(GraphNode node, String pinName) {
        ShaderExpression produced = memo.get(memoKey(node.id(), pinName));
        if (produced == null) {
            throw new EpysiaException("Node '" + node.typeKey() + "' has no output pin '" + pinName + "'");
        }
        return produced;
    }

    private static String memoKey(int nodeId, String pinName) {
        return nodeId + "|" + pinName;
    }

    private static String localName(int nodeId, String pinName) {
        return "v" + nodeId + "_" + pinName.replaceAll("[^A-Za-z0-9]", "");
    }

    private ShaderExpression store(GraphNode node, String pinName, PinType type, String code) {
        String local = localName(node.id(), pinName);
        appendStatement(ShaderExpression.glslType(type) + " " + local + " = " + code + ";");
        return remember(node, pinName, new ShaderExpression(type, local));
    }

    private ShaderExpression remember(GraphNode node, String pinName, ShaderExpression expression) {
        memo.put(memoKey(node.id(), pinName), expression);
        return expression;
    }

    private void emitNode(GraphNode node) {
        String key = node.typeKey();
        if (emitConstant(node, key) || emitBuiltinInput(node, key) || emitTexture(node, key)
                || emitVector(node, key) || emitMath(node, key) || emitEffect(node, key)
                || emitParameter(node, key) || emitCustomCode(node, key)) {
            return;
        }
        throw new EpysiaException("Node '" + key + "' cannot be used in a shader graph");
    }

    private boolean emitConstant(GraphNode node, String key) {
        boolean constant = key.equals(ShaderNodes.CONSTANT_FLOAT) || key.equals(ShaderNodes.CONSTANT_VECTOR2)
                || key.equals(ShaderNodes.CONSTANT_VECTOR3) || key.equals(ShaderNodes.CONSTANT_VECTOR4)
                || key.equals(ShaderNodes.CONSTANT_COLOR);
        if (!constant) {
            return false;
        }
        PinDefinition valuePin = registry.effectiveInputPins(asset, node).get(0);
        remember(node, ShaderNodes.RESULT_PIN, inputExpression(node, valuePin));
        return true;
    }

    private boolean emitBuiltinInput(GraphNode node, String key) {
        Optional<ShaderExpression> builtin = builtinExpression(key);
        if (builtin.isEmpty()) {
            return false;
        }
        remember(node, ShaderNodes.RESULT_PIN, builtin.get());
        return true;
    }

    private Optional<ShaderExpression> builtinExpression(String key) {
        return switch (key) {
            case ShaderNodes.INPUT_UV -> Optional.of(new ShaderExpression(PinType.VECTOR2, "uv"));
            case ShaderNodes.INPUT_TIME -> Optional.of(new ShaderExpression(PinType.FLOAT, "time"));
            case ShaderNodes.INPUT_WORLD_POSITION -> Optional.of(surfaceOnly(key, PinType.VECTOR3, "worldPosition"));
            case ShaderNodes.INPUT_WORLD_NORMAL -> Optional.of(worldNormalExpression());
            case ShaderNodes.INPUT_LOCAL_POSITION -> Optional.of(stageOnly(key, ShaderStage.SURFACE_VERTEX,
                    PinType.VECTOR3, "localPosition"));
            case ShaderNodes.INPUT_SCENE_COLOR -> Optional.of(stageOnly(key, ShaderStage.POST,
                    PinType.VECTOR4, "sceneColor"));
            case ShaderNodes.INPUT_SCENE_DEPTH -> Optional.of(stageOnly(key, ShaderStage.POST,
                    PinType.FLOAT, "texture(sceneDepth, uv).r"));
            case ShaderNodes.INPUT_SCENE_VIEW_DEPTH -> Optional.of(stageOnly(key, ShaderStage.POST,
                    PinType.FLOAT, "sceneViewDepth(uv)"));
            case ShaderNodes.INPUT_SCENE_WORLD_POSITION -> Optional.of(stageOnly(key, ShaderStage.POST,
                    PinType.VECTOR3, "sceneWorldPosition(uv)"));
            case ShaderNodes.INPUT_SCENE_CAMERA_DISTANCE -> Optional.of(stageOnly(key, ShaderStage.POST,
                    PinType.FLOAT, "sceneCameraDistance(uv)"));
            case ShaderNodes.INPUT_SCENE_IS_SKY -> Optional.of(stageOnly(key, ShaderStage.POST,
                    PinType.FLOAT, "(sceneIsSky(uv) ? 1.0 : 0.0)"));
            case ShaderNodes.INPUT_CAMERA_POSITION -> Optional.of(stageOnly(key, ShaderStage.POST,
                    PinType.VECTOR3, "cameraPosition"));
            case ShaderNodes.INPUT_RESOLUTION -> Optional.of(stageOnly(key, ShaderStage.POST,
                    PinType.VECTOR2, "resolution"));
            default -> Optional.empty();
        };
    }

    private ShaderExpression surfaceOnly(String key, PinType type, String code) {
        if (stage == ShaderStage.POST) {
            throw new EpysiaException("'" + key + "' is only available in surface shader graphs");
        }
        return new ShaderExpression(type, code);
    }

    private ShaderExpression stageOnly(String key, ShaderStage required, PinType type, String code) {
        if (stage != required) {
            throw new EpysiaException("'" + key + "' is not available in the " + stageName() + " stage");
        }
        return new ShaderExpression(type, code);
    }

    private ShaderExpression worldNormalExpression() {
        surfaceOnly(ShaderNodes.INPUT_WORLD_NORMAL, PinType.VECTOR3, "");
        return stage == ShaderStage.SURFACE_VERTEX
                ? new ShaderExpression(PinType.VECTOR3, "worldNormal")
                : new ShaderExpression(PinType.VECTOR3, "normalize(vertexWorldNormal)");
    }

    private String stageName() {
        return switch (stage) {
            case SURFACE_FRAGMENT -> "surface color";
            case SURFACE_VERTEX -> "surface vertex";
            case POST -> "post effect";
        };
    }

    private boolean emitTexture(GraphNode node, String key) {
        if (!key.equals(ShaderNodes.TEXTURE_SAMPLE)) {
            return false;
        }
        emitSampleOutputs(node, ShaderNodes.RGBA_PIN, textureSamplerName(node));
        return true;
    }

    private void emitSampleOutputs(GraphNode node, String rgbaPin, String samplerName) {
        PinDefinition uvPin = new PinDefinition(ShaderNodes.UV_PIN, PinType.VECTOR2);
        ShaderExpression uv = inputExpression(node, uvPin);
        ShaderExpression rgba = store(node, rgbaPin, PinType.VECTOR4,
                "texture(" + samplerName + ", " + uv.code() + ")");
        String[] channels = {"R", "G", "B", "A"};
        String[] suffixes = {".r", ".g", ".b", ".a"};
        for (int index = 0; index < channels.length; index++) {
            remember(node, channels[index], new ShaderExpression(PinType.FLOAT, rgba.code() + suffixes[index]));
        }
    }

    private String textureSamplerName(GraphNode node) {
        if (stage == ShaderStage.POST) {
            String samplerName = POST_SAMPLER_PREFIX + node.id();
            shared.declare(samplerName, samplerDeclaration(samplerName, textureSettingPath(node)));
            return samplerName;
        }
        if (stage == ShaderStage.SURFACE_VERTEX) {
            throw new EpysiaException("Texture Sample is not available in the surface vertex stage");
        }
        return materialSamplerName(node);
    }

    private static String samplerDeclaration(String samplerName, String path) {
        String declaration = "uniform sampler2D " + samplerName + ";";
        return path.isEmpty() ? declaration : declaration + " // @default " + path;
    }

    private static String textureSettingPath(GraphNode node) {
        return GraphValues.asString(node.values().getOrDefault(ShaderNodes.PATH_SETTING, "")).strip();
    }

    private static String materialSamplerName(GraphNode node) {
        String requested = GraphValues.asString(node.values()
                .getOrDefault(ShaderNodes.MATERIAL_SAMPLER_SETTING, ShaderNodes.MATERIAL_SAMPLERS.get(0)));
        return ShaderNodes.MATERIAL_SAMPLERS.contains(requested)
                ? requested : ShaderNodes.MATERIAL_SAMPLERS.get(0);
    }

    private boolean emitVector(GraphNode node, String key) {
        return switch (key) {
            case ShaderNodes.SPLIT -> emitSplit(node);
            case ShaderNodes.COMBINE_VECTOR2 -> emitCombine(node, PinType.VECTOR2);
            case ShaderNodes.COMBINE_VECTOR3 -> emitCombine(node, PinType.VECTOR3);
            case ShaderNodes.COMBINE_VECTOR4 -> emitCombine(node, PinType.VECTOR4);
            case ShaderNodes.DOT -> emitReducer(node, "dot", 2);
            case ShaderNodes.CROSS -> emitCross(node);
            case ShaderNodes.NORMALIZE -> emitUnaryCall(node, "normalize");
            case ShaderNodes.LENGTH -> emitReducer(node, "length", 1);
            case ShaderNodes.DISTANCE -> emitReducer(node, "distance", 2);
            default -> false;
        };
    }

    private boolean emitSplit(GraphNode node) {
        ShaderExpression value = inputExpression(node,
                new PinDefinition(ShaderNodes.VALUE_PIN, PinType.NUMERIC));
        ShaderExpression stored = store(node, ShaderNodes.VALUE_PIN, value.type(), value.code());
        int width = ShaderExpression.componentCount(value.type());
        String[] channels = {"X", "Y", "Z", "W"};
        for (int index = 0; index < channels.length; index++) {
            remember(node, channels[index], new ShaderExpression(PinType.FLOAT,
                    splitComponent(stored, width, index)));
        }
        return true;
    }

    private static String splitComponent(ShaderExpression stored, int width, int index) {
        if (index >= width) {
            return "0.0";
        }
        return width == 1 ? stored.code() : stored.code() + COMPONENT_SUFFIXES[index];
    }

    private boolean emitCombine(GraphNode node, PinType resultType) {
        List<PinDefinition> pins = registry.effectiveInputPins(asset, node);
        List<String> parts = new ArrayList<>();
        for (PinDefinition pin : pins) {
            parts.add(inputExpression(node, pin).code());
        }
        store(node, ShaderNodes.RESULT_PIN, resultType,
                ShaderExpression.glslType(resultType) + "(" + String.join(", ", parts) + ")");
        return true;
    }

    private boolean emitCross(GraphNode node) {
        ShaderExpression first = inputExpression(node, new PinDefinition("A", PinType.VECTOR3));
        ShaderExpression second = inputExpression(node, new PinDefinition("B", PinType.VECTOR3));
        store(node, ShaderNodes.RESULT_PIN, PinType.VECTOR3,
                "cross(" + first.code() + ", " + second.code() + ")");
        return true;
    }

    private boolean emitReducer(GraphNode node, String functionName, int argumentCount) {
        List<ShaderExpression> arguments = unifiedInputs(node);
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < argumentCount; index++) {
            parts.add(arguments.get(index).code());
        }
        store(node, ShaderNodes.RESULT_PIN, PinType.FLOAT,
                functionName + "(" + String.join(", ", parts) + ")");
        return true;
    }

    private boolean emitMath(GraphNode node, String key) {
        return switch (key) {
            case ShaderNodes.ADD -> emitInfix(node, "+");
            case ShaderNodes.SUBTRACT -> emitInfix(node, "-");
            case ShaderNodes.MULTIPLY -> emitInfix(node, "*");
            case ShaderNodes.DIVIDE -> emitInfix(node, "/");
            case ShaderNodes.LERP -> emitCall(node, "mix");
            case ShaderNodes.CLAMP -> emitCall(node, "clamp");
            case ShaderNodes.POWER -> emitCall(node, "pow");
            case ShaderNodes.MINIMUM -> emitCall(node, "min");
            case ShaderNodes.MAXIMUM -> emitCall(node, "max");
            case ShaderNodes.STEP -> emitCall(node, "step");
            case ShaderNodes.SMOOTHSTEP -> emitCall(node, "smoothstep");
            default -> emitSimpleMath(node, key);
        };
    }

    private boolean emitSimpleMath(GraphNode node, String key) {
        return switch (key) {
            case ShaderNodes.SATURATE -> emitUnary(node, "clamp(%s, 0.0, 1.0)");
            case ShaderNodes.ONE_MINUS -> emitUnary(node, "(1.0 - %s)");
            case ShaderNodes.ABSOLUTE -> emitUnaryCall(node, "abs");
            case ShaderNodes.SINE -> emitUnaryCall(node, "sin");
            case ShaderNodes.COSINE -> emitUnaryCall(node, "cos");
            case ShaderNodes.FRACT -> emitUnaryCall(node, "fract");
            case ShaderNodes.REMAP -> emitRemap(node);
            default -> false;
        };
    }

    private List<ShaderExpression> unifiedInputs(GraphNode node) {
        List<PinDefinition> pins = registry.effectiveInputPins(asset, node);
        List<ShaderExpression> resolved = new ArrayList<>();
        PinType unified = PinType.FLOAT;
        for (PinDefinition pin : pins) {
            ShaderExpression expression = inputExpression(node, pin);
            unified = widen(unified, expression.type());
            resolved.add(expression);
        }
        return promoteAll(resolved, unified);
    }

    private static PinType widen(PinType current, PinType candidate) {
        if (current == candidate || candidate == PinType.FLOAT) {
            return current;
        }
        if (current == PinType.FLOAT) {
            return candidate;
        }
        throw new EpysiaException("Mismatched vector sizes in shader graph: " + current + " vs " + candidate);
    }

    private static List<ShaderExpression> promoteAll(List<ShaderExpression> expressions, PinType target) {
        List<ShaderExpression> promoted = new ArrayList<>();
        for (ShaderExpression expression : expressions) {
            promoted.add(expression.promoteTo(target));
        }
        return promoted;
    }

    private boolean emitInfix(GraphNode node, String operator) {
        List<ShaderExpression> arguments = unifiedInputs(node);
        store(node, ShaderNodes.RESULT_PIN, arguments.get(0).type(),
                "(" + arguments.get(0).code() + " " + operator + " " + arguments.get(1).code() + ")");
        return true;
    }

    private boolean emitCall(GraphNode node, String functionName) {
        List<ShaderExpression> arguments = unifiedInputs(node);
        List<String> parts = new ArrayList<>();
        for (ShaderExpression argument : arguments) {
            parts.add(argument.code());
        }
        store(node, ShaderNodes.RESULT_PIN, arguments.get(0).type(),
                functionName + "(" + String.join(", ", parts) + ")");
        return true;
    }

    private boolean emitUnary(GraphNode node, String template) {
        ShaderExpression value = unifiedInputs(node).get(0);
        store(node, ShaderNodes.RESULT_PIN, value.type(), template.replace("%s", value.code()));
        return true;
    }

    private boolean emitUnaryCall(GraphNode node, String functionName) {
        return emitUnary(node, functionName + "(%s)");
    }

    private boolean emitRemap(GraphNode node) {
        List<ShaderExpression> arguments = unifiedInputs(node);
        String value = arguments.get(0).code();
        String inputStart = arguments.get(1).code();
        String inputEnd = arguments.get(2).code();
        String outputStart = arguments.get(3).code();
        String outputEnd = arguments.get(4).code();
        store(node, ShaderNodes.RESULT_PIN, arguments.get(0).type(),
                "(" + outputStart + " + (" + value + " - " + inputStart + ") * (" + outputEnd + " - "
                        + outputStart + ") / (" + inputEnd + " - " + inputStart + "))");
        return true;
    }

    private boolean emitEffect(GraphNode node, String key) {
        return switch (key) {
            case ShaderNodes.SIMPLEX_NOISE -> emitNoise(node);
            case ShaderNodes.FRESNEL -> emitFresnel(node);
            case ShaderNodes.RIM -> emitRim(node);
            case ShaderNodes.PANNER -> emitPanner(node);
            case ShaderNodes.TILING_AND_OFFSET -> emitTilingAndOffset(node);
            case ShaderNodes.ROTATOR -> emitRotator(node);
            case ShaderNodes.DISSOLVE -> emitDissolve(node);
            default -> false;
        };
    }

    private boolean emitNoise(GraphNode node) {
        shared.requireNoise();
        ShaderExpression uv = inputExpression(node, new PinDefinition(ShaderNodes.UV_PIN, PinType.VECTOR2));
        ShaderExpression scale = inputExpression(node, new PinDefinition("Scale", PinType.FLOAT));
        store(node, ShaderNodes.RESULT_PIN, PinType.FLOAT,
                "graphSimplexNoise(" + uv.code() + " * " + scale.code() + ")");
        return true;
    }

    private boolean emitFresnel(GraphNode node) {
        requireSurfaceFragment("Fresnel");
        ShaderExpression power = inputExpression(node, new PinDefinition("Power", PinType.FLOAT));
        ShaderExpression view = store(node, "ViewDirection", PinType.VECTOR3,
                "normalize(frame.cameraPosition.xyz - worldPosition)");
        store(node, ShaderNodes.RESULT_PIN, PinType.FLOAT,
                "pow(1.0 - clamp(dot(normalize(vertexWorldNormal), " + view.code() + "), 0.0, 1.0), "
                        + power.code() + ")");
        return true;
    }

    private boolean emitRim(GraphNode node) {
        requireSurfaceFragment("Rim");
        ShaderExpression direction = inputExpression(node, new PinDefinition("Direction", PinType.VECTOR3));
        ShaderExpression power = inputExpression(node, new PinDefinition("Power", PinType.FLOAT));
        store(node, ShaderNodes.RESULT_PIN, PinType.FLOAT,
                "pow(1.0 - clamp(dot(normalize(vertexWorldNormal), normalize(" + direction.code()
                        + ")), 0.0, 1.0), " + power.code() + ")");
        return true;
    }

    private void requireSurfaceFragment(String nodeName) {
        if (stage != ShaderStage.SURFACE_FRAGMENT) {
            throw new EpysiaException(nodeName + " is only available in the surface color stage");
        }
    }

    private boolean emitPanner(GraphNode node) {
        ShaderExpression uv = inputExpression(node, new PinDefinition(ShaderNodes.UV_PIN, PinType.VECTOR2));
        ShaderExpression speed = inputExpression(node, new PinDefinition("Speed", PinType.VECTOR2));
        store(node, ShaderNodes.RESULT_PIN, PinType.VECTOR2,
                "(" + uv.code() + " + " + speed.code() + " * time)");
        return true;
    }

    private boolean emitTilingAndOffset(GraphNode node) {
        ShaderExpression uv = inputExpression(node, new PinDefinition(ShaderNodes.UV_PIN, PinType.VECTOR2));
        ShaderExpression tiling = inputExpression(node, new PinDefinition("Tiling", PinType.VECTOR2));
        ShaderExpression offset = inputExpression(node, new PinDefinition("Offset", PinType.VECTOR2));
        store(node, ShaderNodes.RESULT_PIN, PinType.VECTOR2,
                "(" + uv.code() + " * " + tiling.code() + " + " + offset.code() + ")");
        return true;
    }

    private boolean emitRotator(GraphNode node) {
        ShaderExpression uv = inputExpression(node, new PinDefinition(ShaderNodes.UV_PIN, PinType.VECTOR2));
        ShaderExpression center = inputExpression(node, new PinDefinition("Center", PinType.VECTOR2));
        ShaderExpression angle = inputExpression(node, new PinDefinition("Angle", PinType.FLOAT));
        ShaderExpression offset = store(node, "Offset", PinType.VECTOR2,
                "(" + uv.code() + " - " + center.code() + ")");
        ShaderExpression sine = store(node, "Sine", PinType.FLOAT, "sin(" + angle.code() + ")");
        ShaderExpression cosine = store(node, "Cosine", PinType.FLOAT, "cos(" + angle.code() + ")");
        store(node, ShaderNodes.RESULT_PIN, PinType.VECTOR2, rotatedUv(offset, sine, cosine, center));
        return true;
    }

    private static String rotatedUv(ShaderExpression offset, ShaderExpression sine,
                                    ShaderExpression cosine, ShaderExpression center) {
        return "(vec2(" + offset.code() + ".x * " + cosine.code() + " - " + offset.code() + ".y * "
                + sine.code() + ", " + offset.code() + ".x * " + sine.code() + " + " + offset.code()
                + ".y * " + cosine.code() + ") + " + center.code() + ")";
    }

    private boolean emitDissolve(GraphNode node) {
        ShaderExpression noise = inputExpression(node, new PinDefinition("Noise", PinType.FLOAT));
        ShaderExpression threshold = inputExpression(node, new PinDefinition("Threshold", PinType.FLOAT));
        ShaderExpression edgeWidth = inputExpression(node, new PinDefinition("Edge Width", PinType.FLOAT));
        ShaderExpression edgeColor = inputExpression(node, new PinDefinition("Edge Color", PinType.VECTOR3));
        ShaderExpression alpha = store(node, ShaderNodes.ALPHA_PIN, PinType.FLOAT,
                "step(" + threshold.code() + ", " + noise.code() + ")");
        ShaderExpression edge = store(node, "EdgeMask", PinType.FLOAT,
                alpha.code() + " * (1.0 - smoothstep(" + threshold.code() + ", " + threshold.code()
                        + " + " + edgeWidth.code() + ", " + noise.code() + "))");
        store(node, ShaderNodes.EDGE_EMISSIVE_PIN, PinType.VECTOR3,
                edgeColor.code() + " * " + edge.code());
        return true;
    }

    private boolean emitParameter(GraphNode node, String key) {
        return switch (key) {
            case ShaderNodes.PARAMETER_FLOAT -> emitValueParameter(node, PinType.FLOAT);
            case ShaderNodes.PARAMETER_COLOR -> emitValueParameter(node, PinType.VECTOR4);
            case ShaderNodes.PARAMETER_TEXTURE -> emitTextureParameter(node);
            default -> false;
        };
    }

    private boolean emitValueParameter(GraphNode node, PinType type) {
        requireLiteralDefault(node);
        String name = parameterName(node);
        shared.declare(name, valueParameterDeclaration(node, type, name));
        remember(node, ShaderNodes.RESULT_PIN, new ShaderExpression(type, name));
        return true;
    }

    private String valueParameterDeclaration(GraphNode node, PinType type, String name) {
        String annotations = node.typeKey().equals(ShaderNodes.PARAMETER_COLOR) ? " // @color @default " : " // @default ";
        return "uniform " + ShaderExpression.glslType(type) + " " + name + ";"
                + annotations + defaultComponents(node, type);
    }

    private static String defaultComponents(GraphNode node, PinType type) {
        Object value = node.values().getOrDefault(ShaderNodes.VALUE_PIN,
                GraphValues.defaultFor(type));
        if (type == PinType.FLOAT) {
            return floatText(GraphValues.asFloat(value));
        }
        Vector4f vector = GraphValues.asVector4(value);
        return floatText(vector.x) + "," + floatText(vector.y) + ","
                + floatText(vector.z) + "," + floatText(vector.w);
    }

    private void requireLiteralDefault(GraphNode node) {
        if (asset.edgeInto(node.id(), ShaderNodes.VALUE_PIN).isPresent()) {
            throw new EpysiaException("Parameter defaults must be literals, disconnect the Value pin");
        }
    }

    private boolean emitTextureParameter(GraphNode node) {
        if (stage != ShaderStage.POST) {
            throw new EpysiaException("Texture Parameter is only available in post effect graphs");
        }
        String name = parameterName(node);
        shared.declare(name, samplerDeclaration(name, textureSettingPath(node)));
        emitSampleOutputs(node, ShaderNodes.RGBA_PIN, name);
        return true;
    }

    private String parameterName(GraphNode node) {
        String requested = GraphValues.asString(node.values()
                .getOrDefault(ShaderNodes.NAME_SETTING, "")).replaceAll("[^A-Za-z0-9_]", "");
        if (requested.isEmpty() || Character.isDigit(requested.charAt(0))) {
            return "graphParameter" + node.id();
        }
        return requested;
    }

    private boolean emitCustomCode(GraphNode node, String key) {
        if (!key.equals(ShaderNodes.CUSTOM_CODE)) {
            return false;
        }
        List<PinDefinition> inputs = ShaderNodes.customCodeInputs(node);
        PinType outputType = ShaderNodes.customValueType(node, ShaderNodes.OUTPUT_TYPE_SETTING);
        String functionName = CUSTOM_FUNCTION_PREFIX + node.id();
        shared.declareFunction(node.id(), customFunctionSource(node, functionName, inputs, outputType));
        store(node, ShaderNodes.RESULT_PIN, outputType,
                functionName + "(" + customArguments(node, inputs) + ")");
        return true;
    }

    private String customArguments(GraphNode node, List<PinDefinition> inputs) {
        List<String> parts = new ArrayList<>();
        for (PinDefinition pin : inputs) {
            parts.add(inputExpression(node, pin).promoteTo(pin.type()).code());
        }
        return String.join(", ", parts);
    }

    private static String customFunctionSource(GraphNode node, String functionName,
                                               List<PinDefinition> inputs, PinType outputType) {
        List<String> parameters = new ArrayList<>();
        for (PinDefinition pin : inputs) {
            parameters.add(ShaderExpression.glslType(pin.type()) + " " + customParameterName(pin.name()));
        }
        return ShaderExpression.glslType(outputType) + " " + functionName
                + "(" + String.join(", ", parameters) + ") {\n" + customBody(node) + "\n}";
    }

    private static String customParameterName(String pinName) {
        String cleaned = pinName.replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0)) ? "input" + cleaned : cleaned;
    }

    private static String customBody(GraphNode node) {
        String code = GraphValues.asString(node.values().getOrDefault(ShaderNodes.CODE_SETTING, "")).strip();
        String body = code.contains("return") ? code : "return (" + code + ");";
        return "    " + body.replace("\n", "\n    ");
    }
}
