package fr.epistudio.epysia.graph.shader;

import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.GraphValues;
import fr.epistudio.epysia.graph.NodeDefinition;
import fr.epistudio.epysia.graph.NodeSetting;
import fr.epistudio.epysia.graph.PinDefinition;
import fr.epistudio.epysia.graph.PinType;
import fr.epistudio.epysia.graph.SettingKind;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ShaderNodes {

    public static final String CONSTANT_FLOAT = "shader.constant.float";
    public static final String CONSTANT_VECTOR2 = "shader.constant.vector2";
    public static final String CONSTANT_VECTOR3 = "shader.constant.vector3";
    public static final String CONSTANT_VECTOR4 = "shader.constant.vector4";
    public static final String CONSTANT_COLOR = "shader.constant.color";
    public static final String INPUT_UV = "shader.input.uv";
    public static final String INPUT_TIME = "shader.input.time";
    public static final String INPUT_WORLD_POSITION = "shader.input.worldPosition";
    public static final String INPUT_WORLD_NORMAL = "shader.input.worldNormal";
    public static final String INPUT_LOCAL_POSITION = "shader.input.localPosition";
    public static final String INPUT_SCENE_COLOR = "shader.input.sceneColor";
    public static final String INPUT_SCENE_DEPTH = "shader.input.sceneDepth";
    public static final String INPUT_SCENE_VIEW_DEPTH = "shader.input.sceneViewDepth";
    public static final String INPUT_SCENE_WORLD_POSITION = "shader.input.sceneWorldPosition";
    public static final String INPUT_SCENE_CAMERA_DISTANCE = "shader.input.sceneCameraDistance";
    public static final String INPUT_SCENE_IS_SKY = "shader.input.sceneIsSky";
    public static final String INPUT_CAMERA_POSITION = "shader.input.cameraPosition";
    public static final String INPUT_RESOLUTION = "shader.input.resolution";
    public static final String TEXTURE_SAMPLE = "shader.texture.sample";
    public static final String SPLIT = "shader.vector.split";
    public static final String COMBINE_VECTOR2 = "shader.vector.combine2";
    public static final String COMBINE_VECTOR3 = "shader.vector.combine3";
    public static final String COMBINE_VECTOR4 = "shader.vector.combine4";
    public static final String ADD = "shader.math.add";
    public static final String SUBTRACT = "shader.math.subtract";
    public static final String MULTIPLY = "shader.math.multiply";
    public static final String DIVIDE = "shader.math.divide";
    public static final String LERP = "shader.math.lerp";
    public static final String CLAMP = "shader.math.clamp";
    public static final String SATURATE = "shader.math.saturate";
    public static final String ONE_MINUS = "shader.math.oneMinus";
    public static final String POWER = "shader.math.power";
    public static final String ABSOLUTE = "shader.math.absolute";
    public static final String MINIMUM = "shader.math.minimum";
    public static final String MAXIMUM = "shader.math.maximum";
    public static final String SINE = "shader.math.sine";
    public static final String COSINE = "shader.math.cosine";
    public static final String FRACT = "shader.math.fract";
    public static final String FLOOR = "shader.math.floor";
    public static final String MODULO = "shader.math.modulo";
    public static final String STEP = "shader.math.step";
    public static final String SMOOTHSTEP = "shader.math.smoothstep";
    public static final String REMAP = "shader.math.remap";
    public static final String DOT = "shader.vector.dot";
    public static final String CROSS = "shader.vector.cross";
    public static final String NORMALIZE = "shader.vector.normalize";
    public static final String LENGTH = "shader.vector.length";
    public static final String DISTANCE = "shader.vector.distance";
    public static final String SIMPLEX_NOISE = "shader.noise.simplex";
    public static final String FRESNEL = "shader.effect.fresnel";
    public static final String RIM = "shader.effect.rim";
    public static final String PANNER = "shader.effect.panner";
    public static final String TILING_AND_OFFSET = "shader.effect.tilingAndOffset";
    public static final String ROTATOR = "shader.effect.rotator";
    public static final String DISSOLVE = "shader.effect.dissolve";
    public static final String PARAMETER_FLOAT = "shader.parameter.float";
    public static final String PARAMETER_COLOR = "shader.parameter.color";
    public static final String PARAMETER_TEXTURE = "shader.parameter.texture";
    public static final String CUSTOM_CODE = "shader.custom.code";
    public static final String OUTPUT_SURFACE = "shader.output.surface";
    public static final String OUTPUT_POST = "shader.output.post";

    public static final String VALUE_PIN = "Value";
    public static final String RESULT_PIN = "Result";
    public static final String UV_PIN = "UV";
    public static final String ALBEDO_PIN = "Albedo";
    public static final String METALLIC_PIN = "Metallic";
    public static final String ROUGHNESS_PIN = "Roughness";
    public static final String EMISSIVE_PIN = "Emissive";
    public static final String WORLD_POSITION_OFFSET_PIN = "World Position Offset";
    public static final String COLOR_PIN = "Color";
    public static final String RGBA_PIN = "RGBA";
    public static final String ALPHA_PIN = "Alpha";
    public static final String EDGE_EMISSIVE_PIN = "Edge Emissive";

    public static final String NAME_SETTING = "parameterName";
    public static final String PATH_SETTING = "path";
    public static final String MATERIAL_SAMPLER_SETTING = "materialSampler";
    public static final String MASTER_SETTING = "master";
    public static final String CODE_SETTING = "code";
    public static final String OUTPUT_TYPE_SETTING = "outputType";
    public static final String INPUT_COUNT_SETTING = "inputCount";
    public static final String MASTER_LIT = "Lit";
    public static final String MASTER_UNLIT = "Unlit";
    public static final List<String> MASTER_MODES = List.of(MASTER_LIT, MASTER_UNLIT);
    public static final List<String> MATERIAL_SAMPLERS = List.of(
            "albedo", "normalMap", "metallicRoughnessMap", "occlusionMap", "emissiveMap");
    public static final List<String> CUSTOM_VALUE_TYPES = List.of(
            PinType.FLOAT.name(), PinType.VECTOR2.name(), PinType.VECTOR3.name(), PinType.VECTOR4.name());
    public static final int CUSTOM_CODE_MAX_INPUTS = 4;

    public static final String CATEGORY_INPUT = "Shader Input";
    public static final String CATEGORY_CONSTANT = "Shader Constant";
    public static final String CATEGORY_MATH = "Shader Math";
    public static final String CATEGORY_VECTOR = "Shader Vector";
    public static final String CATEGORY_TEXTURE = "Shader Texture";
    public static final String CATEGORY_EFFECT = "Shader Effect";
    public static final String CATEGORY_PARAMETER = "Shader Parameter";
    public static final String CATEGORY_CUSTOM = "Shader Custom";
    public static final String CATEGORY_OUTPUT = "Shader Output";

    private static final Map<String, Object> DEFAULT_PIN_VALUES = Map.ofEntries(
            Map.entry(MULTIPLY + "|B", 1.0f),
            Map.entry(DIVIDE + "|B", 1.0f),
            Map.entry(LERP + "|T", 0.5f),
            Map.entry(CLAMP + "|Max", 1.0f),
            Map.entry(POWER + "|Exponent", 2.0f),
            Map.entry(SIMPLEX_NOISE + "|Scale", 1.0f),
            Map.entry(FRESNEL + "|Power", 5.0f),
            Map.entry(RIM + "|Power", 3.0f),
            Map.entry(RIM + "|Direction", new Vector3f(0.0f, 1.0f, 0.0f)),
            Map.entry(PANNER + "|Speed", new Vector2f(0.1f, 0.1f)),
            Map.entry(TILING_AND_OFFSET + "|Tiling", new Vector2f(1.0f, 1.0f)),
            Map.entry(ROTATOR + "|Center", new Vector2f(0.5f, 0.5f)),
            Map.entry(DISSOLVE + "|Threshold", 0.5f),
            Map.entry(DISSOLVE + "|Edge Width", 0.1f),
            Map.entry(DISSOLVE + "|Edge Color", new Vector3f(1.0f, 0.5f, 0.0f)),
            Map.entry(SMOOTHSTEP + "|Edge End", 1.0f),
            Map.entry(MODULO + "|Divisor", 2.0f),
            Map.entry(REMAP + "|Input End", 1.0f),
            Map.entry(REMAP + "|Output End", 1.0f),
            Map.entry(CONSTANT_COLOR + "|" + VALUE_PIN, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f)));

    private static final Set<String> UV_DEFAULT_NODES = Set.of(
            TEXTURE_SAMPLE, PARAMETER_TEXTURE, PANNER, TILING_AND_OFFSET, ROTATOR, SIMPLEX_NOISE);

    private ShaderNodes() {
    }

    public static boolean defaultsToUv(String typeKey, String pinName) {
        return pinName.equals(UV_PIN) && UV_DEFAULT_NODES.contains(typeKey);
    }

    public static Object defaultPinValue(String typeKey, PinDefinition pin) {
        Object known = DEFAULT_PIN_VALUES.get(typeKey + "|" + pin.name());
        return known == null ? GraphValues.defaultFor(pin.type()) : known;
    }

    public static boolean isShaderNode(String typeKey) {
        return typeKey.startsWith("shader.");
    }

    public static boolean isSurfaceOnly(String typeKey) {
        return typeKey.equals(INPUT_WORLD_POSITION) || typeKey.equals(INPUT_WORLD_NORMAL)
                || typeKey.equals(INPUT_LOCAL_POSITION) || typeKey.equals(FRESNEL)
                || typeKey.equals(RIM) || typeKey.equals(OUTPUT_SURFACE);
    }

    public static boolean isPostOnly(String typeKey) {
        return typeKey.equals(INPUT_SCENE_COLOR) || typeKey.equals(INPUT_SCENE_DEPTH)
                || typeKey.equals(INPUT_RESOLUTION) || typeKey.equals(PARAMETER_TEXTURE)
                || typeKey.equals(OUTPUT_POST);
    }

    public static boolean isOutput(String typeKey) {
        return typeKey.equals(OUTPUT_SURFACE) || typeKey.equals(OUTPUT_POST);
    }

    public static void registerInto(GraphNodeRegistry registry) {
        registerConstants(registry);
        registerInputs(registry);
        registerTexture(registry);
        registerVector(registry);
        registerMath(registry);
        registerEffects(registry);
        registerParameters(registry);
        registerCustom(registry);
        registerOutputs(registry);
    }

    private static void registerConstants(GraphNodeRegistry registry) {
        registry.register(source(CONSTANT_FLOAT, "Float", CATEGORY_CONSTANT, PinType.FLOAT));
        registry.register(source(CONSTANT_VECTOR2, "Vector2", CATEGORY_CONSTANT, PinType.VECTOR2));
        registry.register(source(CONSTANT_VECTOR3, "Vector3", CATEGORY_CONSTANT, PinType.VECTOR3));
        registry.register(source(CONSTANT_VECTOR4, "Vector4", CATEGORY_CONSTANT, PinType.VECTOR4));
        registry.register(source(CONSTANT_COLOR, "Color", CATEGORY_CONSTANT, PinType.VECTOR4));
    }

    private static void registerInputs(GraphNodeRegistry registry) {
        registry.register(input(INPUT_UV, "UV", PinType.VECTOR2));
        registry.register(input(INPUT_TIME, "Time", PinType.FLOAT));
        registry.register(input(INPUT_WORLD_POSITION, "World Position", PinType.VECTOR3));
        registry.register(input(INPUT_WORLD_NORMAL, "World Normal", PinType.VECTOR3));
        registry.register(input(INPUT_LOCAL_POSITION, "Vertex Local Position", PinType.VECTOR3));
        registry.register(input(INPUT_SCENE_COLOR, "Scene Color", PinType.VECTOR4));
        registry.register(input(INPUT_SCENE_DEPTH, "Scene Depth", PinType.FLOAT));
        registry.register(input(INPUT_SCENE_VIEW_DEPTH, "Scene View Depth", PinType.FLOAT));
        registry.register(input(INPUT_SCENE_WORLD_POSITION, "Scene World Position", PinType.VECTOR3));
        registry.register(input(INPUT_SCENE_CAMERA_DISTANCE, "Scene Camera Distance", PinType.FLOAT));
        registry.register(input(INPUT_SCENE_IS_SKY, "Scene Is Sky", PinType.FLOAT));
        registry.register(input(INPUT_CAMERA_POSITION, "Camera Position", PinType.VECTOR3));
        registry.register(input(INPUT_RESOLUTION, "Resolution", PinType.VECTOR2));
    }

    private static void registerTexture(GraphNodeRegistry registry) {
        registry.register(node(TEXTURE_SAMPLE, "Texture Sample", CATEGORY_TEXTURE,
                List.of(new PinDefinition(UV_PIN, PinType.VECTOR2)),
                List.of(new PinDefinition(RGBA_PIN, PinType.VECTOR4),
                        new PinDefinition("R", PinType.FLOAT), new PinDefinition("G", PinType.FLOAT),
                        new PinDefinition("B", PinType.FLOAT), new PinDefinition("A", PinType.FLOAT)),
                List.of(new NodeSetting(PATH_SETTING, SettingKind.ASSET_PATH, ""),
                        new NodeSetting(MATERIAL_SAMPLER_SETTING, SettingKind.TEXT, MATERIAL_SAMPLERS.get(0)))));
    }

    private static void registerVector(GraphNodeRegistry registry) {
        registry.register(node(SPLIT, "Split", CATEGORY_VECTOR,
                List.of(new PinDefinition(VALUE_PIN, PinType.NUMERIC)),
                List.of(new PinDefinition("X", PinType.FLOAT), new PinDefinition("Y", PinType.FLOAT),
                        new PinDefinition("Z", PinType.FLOAT), new PinDefinition("W", PinType.FLOAT)),
                List.of()));
        registry.register(combine(COMBINE_VECTOR2, "Combine Vector2", PinType.VECTOR2, List.of("X", "Y")));
        registry.register(combine(COMBINE_VECTOR3, "Combine Vector3", PinType.VECTOR3, List.of("X", "Y", "Z")));
        registry.register(combine(COMBINE_VECTOR4, "Combine Vector4", PinType.VECTOR4, List.of("X", "Y", "Z", "W")));
        registry.register(numeric(DOT, "Dot", CATEGORY_VECTOR, List.of("A", "B"), PinType.FLOAT));
        registry.register(node(CROSS, "Cross", CATEGORY_VECTOR,
                List.of(new PinDefinition("A", PinType.VECTOR3), new PinDefinition("B", PinType.VECTOR3)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR3)), List.of()));
        registry.register(numeric(NORMALIZE, "Normalize", CATEGORY_VECTOR, List.of(VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(LENGTH, "Length", CATEGORY_VECTOR, List.of(VALUE_PIN), PinType.FLOAT));
        registry.register(numeric(DISTANCE, "Distance", CATEGORY_VECTOR, List.of("A", "B"), PinType.FLOAT));
    }

    private static void registerMath(GraphNodeRegistry registry) {
        registry.register(numeric(ADD, "Add", CATEGORY_MATH, List.of("A", "B"), PinType.NUMERIC));
        registry.register(numeric(SUBTRACT, "Subtract", CATEGORY_MATH, List.of("A", "B"), PinType.NUMERIC));
        registry.register(numeric(MULTIPLY, "Multiply", CATEGORY_MATH, List.of("A", "B"), PinType.NUMERIC));
        registry.register(numeric(DIVIDE, "Divide", CATEGORY_MATH, List.of("A", "B"), PinType.NUMERIC));
        registry.register(numeric(LERP, "Lerp", CATEGORY_MATH, List.of("A", "B", "T"), PinType.NUMERIC));
        registry.register(numeric(CLAMP, "Clamp", CATEGORY_MATH, List.of(VALUE_PIN, "Min", "Max"), PinType.NUMERIC));
        registry.register(numeric(SATURATE, "Saturate", CATEGORY_MATH, List.of(VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(ONE_MINUS, "One Minus", CATEGORY_MATH, List.of(VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(POWER, "Power", CATEGORY_MATH, List.of("Base", "Exponent"), PinType.NUMERIC));
        registry.register(numeric(ABSOLUTE, "Absolute", CATEGORY_MATH, List.of(VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(MINIMUM, "Min", CATEGORY_MATH, List.of("A", "B"), PinType.NUMERIC));
        registry.register(numeric(MAXIMUM, "Max", CATEGORY_MATH, List.of("A", "B"), PinType.NUMERIC));
        registerTrigAndRange(registry);
    }

    private static void registerTrigAndRange(GraphNodeRegistry registry) {
        registry.register(numeric(SINE, "Sine", CATEGORY_MATH, List.of(VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(COSINE, "Cosine", CATEGORY_MATH, List.of(VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(FRACT, "Fract", CATEGORY_MATH, List.of(VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(FLOOR, "Floor", CATEGORY_MATH, List.of(VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(MODULO, "Modulo", CATEGORY_MATH, List.of(VALUE_PIN, "Divisor"), PinType.NUMERIC));
        registry.register(numeric(STEP, "Step", CATEGORY_MATH, List.of("Edge", VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(SMOOTHSTEP, "Smoothstep", CATEGORY_MATH,
                List.of("Edge Start", "Edge End", VALUE_PIN), PinType.NUMERIC));
        registry.register(numeric(REMAP, "Remap", CATEGORY_MATH,
                List.of(VALUE_PIN, "Input Start", "Input End", "Output Start", "Output End"), PinType.NUMERIC));
    }

    private static void registerEffects(GraphNodeRegistry registry) {
        registry.register(node(SIMPLEX_NOISE, "Simplex Noise 2D", CATEGORY_EFFECT,
                List.of(new PinDefinition(UV_PIN, PinType.VECTOR2), new PinDefinition("Scale", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)), List.of()));
        registry.register(node(FRESNEL, "Fresnel", CATEGORY_EFFECT,
                List.of(new PinDefinition("Power", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)), List.of()));
        registry.register(node(RIM, "Rim", CATEGORY_EFFECT,
                List.of(new PinDefinition("Direction", PinType.VECTOR3), new PinDefinition("Power", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)), List.of()));
        registerUvEffects(registry);
        registry.register(node(DISSOLVE, "Dissolve", CATEGORY_EFFECT,
                List.of(new PinDefinition("Noise", PinType.FLOAT), new PinDefinition("Threshold", PinType.FLOAT),
                        new PinDefinition("Edge Width", PinType.FLOAT),
                        new PinDefinition("Edge Color", PinType.VECTOR3)),
                List.of(new PinDefinition(ALPHA_PIN, PinType.FLOAT),
                        new PinDefinition(EDGE_EMISSIVE_PIN, PinType.VECTOR3)),
                List.of()));
    }

    private static void registerUvEffects(GraphNodeRegistry registry) {
        registry.register(node(PANNER, "Panner", CATEGORY_EFFECT,
                List.of(new PinDefinition(UV_PIN, PinType.VECTOR2), new PinDefinition("Speed", PinType.VECTOR2)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR2)), List.of()));
        registry.register(node(TILING_AND_OFFSET, "Tiling And Offset", CATEGORY_EFFECT,
                List.of(new PinDefinition(UV_PIN, PinType.VECTOR2), new PinDefinition("Tiling", PinType.VECTOR2),
                        new PinDefinition("Offset", PinType.VECTOR2)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR2)), List.of()));
        registry.register(node(ROTATOR, "Rotator", CATEGORY_EFFECT,
                List.of(new PinDefinition(UV_PIN, PinType.VECTOR2), new PinDefinition("Center", PinType.VECTOR2),
                        new PinDefinition("Angle", PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR2)), List.of()));
    }

    private static void registerParameters(GraphNodeRegistry registry) {
        registry.register(node(PARAMETER_FLOAT, "Float Parameter", CATEGORY_PARAMETER,
                List.of(new PinDefinition(VALUE_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(new NodeSetting(NAME_SETTING, SettingKind.TEXT, "myFloat"))));
        registry.register(node(PARAMETER_COLOR, "Color Parameter", CATEGORY_PARAMETER,
                List.of(new PinDefinition(VALUE_PIN, PinType.VECTOR4)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR4)),
                List.of(new NodeSetting(NAME_SETTING, SettingKind.TEXT, "myColor"))));
        registry.register(node(PARAMETER_TEXTURE, "Texture Parameter", CATEGORY_PARAMETER,
                List.of(new PinDefinition(UV_PIN, PinType.VECTOR2)),
                List.of(new PinDefinition(RGBA_PIN, PinType.VECTOR4)),
                List.of(new NodeSetting(NAME_SETTING, SettingKind.TEXT, "myTexture"),
                        new NodeSetting(PATH_SETTING, SettingKind.ASSET_PATH, ""))));
    }

    private static void registerCustom(GraphNodeRegistry registry) {
        List<NodeSetting> settings = new ArrayList<>();
        settings.add(new NodeSetting(CODE_SETTING, SettingKind.TEXT, "return a;"));
        settings.add(new NodeSetting(OUTPUT_TYPE_SETTING, SettingKind.TEXT, PinType.FLOAT.name()));
        settings.add(new NodeSetting(INPUT_COUNT_SETTING, SettingKind.WHOLE_NUMBER, 1));
        for (int index = 0; index < CUSTOM_CODE_MAX_INPUTS; index++) {
            settings.add(new NodeSetting(customInputNameSetting(index), SettingKind.TEXT, defaultInputName(index)));
            settings.add(new NodeSetting(customInputTypeSetting(index), SettingKind.TEXT, PinType.FLOAT.name()));
        }
        registry.register(node(CUSTOM_CODE, "Custom Code", CATEGORY_CUSTOM,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.NUMERIC)), List.copyOf(settings)));
    }

    public static String customInputNameSetting(int index) {
        return "input" + index + "Name";
    }

    public static String customInputTypeSetting(int index) {
        return "input" + index + "Type";
    }

    private static String defaultInputName(int index) {
        return String.valueOf((char) ('a' + index));
    }

    public static List<PinDefinition> customCodeInputs(GraphNode node) {
        int count = Math.clamp(GraphValues.asInt(node.values().getOrDefault(INPUT_COUNT_SETTING, 1)),
                0, CUSTOM_CODE_MAX_INPUTS);
        List<PinDefinition> pins = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String name = GraphValues.asString(node.values()
                    .getOrDefault(customInputNameSetting(index), defaultInputName(index)));
            pins.add(new PinDefinition(name.isBlank() ? defaultInputName(index) : name,
                    customValueType(node, customInputTypeSetting(index))));
        }
        return pins;
    }

    public static PinType customValueType(GraphNode node, String settingKey) {
        String name = GraphValues.asString(node.values().getOrDefault(settingKey, PinType.FLOAT.name()))
                .toUpperCase(Locale.ROOT);
        try {
            PinType parsed = PinType.valueOf(name);
            return parsed.isShaderValue() && parsed != PinType.NUMERIC ? parsed : PinType.FLOAT;
        } catch (IllegalArgumentException unknown) {
            return PinType.FLOAT;
        }
    }

    public static List<PinDefinition> customCodeOutputs(GraphNode node) {
        return List.of(new PinDefinition(RESULT_PIN, customValueType(node, OUTPUT_TYPE_SETTING)));
    }

    private static void registerOutputs(GraphNodeRegistry registry) {
        registry.register(node(OUTPUT_SURFACE, "Surface Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(ALBEDO_PIN, PinType.VECTOR4),
                        new PinDefinition(METALLIC_PIN, PinType.FLOAT),
                        new PinDefinition(ROUGHNESS_PIN, PinType.FLOAT),
                        new PinDefinition(EMISSIVE_PIN, PinType.VECTOR3),
                        new PinDefinition(WORLD_POSITION_OFFSET_PIN, PinType.VECTOR3)),
                List.of(),
                List.of(new NodeSetting(MASTER_SETTING, SettingKind.TEXT, MASTER_LIT))));
        registry.register(node(OUTPUT_POST, "Post Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(COLOR_PIN, PinType.VECTOR4)),
                List.of(), List.of()));
    }

    private static NodeDefinition source(String typeKey, String displayName, String category, PinType type) {
        return node(typeKey, displayName, category,
                List.of(new PinDefinition(VALUE_PIN, type)),
                List.of(new PinDefinition(RESULT_PIN, type)), List.of());
    }

    private static NodeDefinition input(String typeKey, String displayName, PinType type) {
        return node(typeKey, displayName, CATEGORY_INPUT,
                List.of(), List.of(new PinDefinition(RESULT_PIN, type)), List.of());
    }

    private static NodeDefinition combine(String typeKey, String displayName, PinType result,
                                          List<String> componentPins) {
        List<PinDefinition> inputs = new ArrayList<>();
        for (String pinName : componentPins) {
            inputs.add(new PinDefinition(pinName, PinType.FLOAT));
        }
        return node(typeKey, displayName, CATEGORY_VECTOR, List.copyOf(inputs),
                List.of(new PinDefinition(RESULT_PIN, result)), List.of());
    }

    private static NodeDefinition numeric(String typeKey, String displayName, String category,
                                          List<String> inputPins, PinType resultType) {
        List<PinDefinition> inputs = new ArrayList<>();
        for (String pinName : inputPins) {
            inputs.add(new PinDefinition(pinName, PinType.NUMERIC));
        }
        return node(typeKey, displayName, category, List.copyOf(inputs),
                List.of(new PinDefinition(RESULT_PIN, resultType)), List.of());
    }

    private static NodeDefinition node(String typeKey, String displayName, String category,
                                       List<PinDefinition> inputs, List<PinDefinition> outputs,
                                       List<NodeSetting> settings) {
        return new NodeDefinition(typeKey, displayName, category, false, false,
                inputs, outputs, settings, context -> {
                });
    }
}
