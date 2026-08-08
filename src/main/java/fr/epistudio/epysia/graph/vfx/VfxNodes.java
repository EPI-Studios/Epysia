package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.NodeDefinition;
import fr.epistudio.epysia.graph.NodeSetting;
import fr.epistudio.epysia.graph.PinDefinition;
import fr.epistudio.epysia.graph.PinType;
import fr.epistudio.epysia.graph.SettingKind;
import fr.epistudio.epysia.vfx.lut.VfxCurve;
import fr.epistudio.epysia.vfx.lut.VfxGradient;

import java.util.List;

public final class VfxNodes {
    public static final String OUTPUT_SPAWN_RATE = "vfx.output.spawnRate";
    public static final String OUTPUT_PARTICLE = "vfx.output.particle";
    public static final String OUTPUT_UPDATE = "vfx.output.update";
    public static final String OUTPUT_RENDER = "vfx.output.render";
    public static final String PARTICLE_AGE = "vfx.particleAge";
    public static final String PARTICLE_AGE_NORMALIZED = "vfx.particleAgeNormalized";
    public static final String PARTICLE_SEED = "vfx.particleSeed";
    public static final String EMITTER_POSITION = "vfx.emitterPosition";
    public static final String PARTICLE_POSITION = "vfx.particlePosition";
    public static final String PARTICLE_VELOCITY = "vfx.particleVelocity";
    public static final String EFFECT_TIME_NORMALIZED = "vfx.effectTimeNormalized";
    public static final String DELTA_TIME = "vfx.deltaTime";
    public static final String RANDOM_RANGE = "vfx.randomRange";
    public static final String RANDOM_RANGE_VECTOR = "vfx.randomRangeVector";
    public static final String CONE_DIRECTION = "vfx.coneDirection";
    public static final String CONSTANT = "vfx.value.constant";
    public static final String CURVE = "vfx.value.curve";
    public static final String GRADIENT = "vfx.value.gradient";
    public static final String SHAPE = "vfx.shape";
    public static final String NOISE = "vfx.noise";

    public static final String MATH_ADD = "vfx.math.add";
    public static final String MATH_SUBTRACT = "vfx.math.subtract";
    public static final String MATH_MULTIPLY = "vfx.math.multiply";
    public static final String MATH_DIVIDE = "vfx.math.divide";
    public static final String MATH_DOT = "vfx.math.dot";
    public static final String MATH_CROSS = "vfx.math.cross";
    public static final String MATH_LERP = "vfx.math.lerp";
    public static final String MATH_CLAMP = "vfx.math.clamp";
    public static final String MATH_REMAP = "vfx.math.remap";
    public static final String MATH_ONE_MINUS = "vfx.math.oneMinus";
    public static final String MATH_NORMALIZE = "vfx.math.normalize";
    public static final String MATH_LENGTH = "vfx.math.length";
    public static final String MATH_SINE = "vfx.math.sine";
    public static final String MATH_VECTOR3 = "vfx.math.vector3";
    public static final String MATH_VECTOR4 = "vfx.math.vector4";
    public static final String MATH_SPLIT_VECTOR3 = "vfx.math.splitVector3";
    public static final String MATH_SPLIT_VECTOR4 = "vfx.math.splitVector4";

    public static final String RATE_PIN = "Rate";
    public static final String POSITION_PIN = "Position";
    public static final String DIRECTION_PIN = "Direction";
    public static final String VELOCITY_PIN = "Velocity";
    public static final String LIFETIME_PIN = "Lifetime";
    public static final String COLOR_PIN = "Color";
    public static final String SIZE_PIN = "Size";
    public static final String SIZE_Y_PIN = "Size Y";
    public static final String ROTATION_PIN = "Rotation";
    public static final String ANGULAR_VELOCITY_PIN = "Angular Velocity";
    public static final String KILL_PIN = "Kill";
    public static final String SOFT_EDGE_PIN = "Soft Edge";
    public static final String INTENSITY_PIN = "Intensity";
    public static final String RESULT_PIN = "Result";
    public static final String TIME_PIN = "Time";
    public static final String A_PIN = "A";
    public static final String B_PIN = "B";
    public static final String T_PIN = "T";
    public static final String VALUE_PIN = "Value";
    public static final String MINIMUM_PIN = "Minimum";
    public static final String MAXIMUM_PIN = "Maximum";
    public static final String FROM_MINIMUM_PIN = "From Minimum";
    public static final String FROM_MAXIMUM_PIN = "From Maximum";
    public static final String TO_MINIMUM_PIN = "To Minimum";
    public static final String TO_MAXIMUM_PIN = "To Maximum";
    public static final String X_PIN = "X";
    public static final String Y_PIN = "Y";
    public static final String Z_PIN = "Z";
    public static final String W_PIN = "W";

    public static final String MINIMUM_SETTING = "minimum";
    public static final String MAXIMUM_SETTING = "maximum";
    public static final String MINIMUM_X_SETTING = "minimumX";
    public static final String MINIMUM_Y_SETTING = "minimumY";
    public static final String MINIMUM_Z_SETTING = "minimumZ";
    public static final String MAXIMUM_X_SETTING = "maximumX";
    public static final String MAXIMUM_Y_SETTING = "maximumY";
    public static final String MAXIMUM_Z_SETTING = "maximumZ";
    public static final String DIRECTION_X_SETTING = "directionX";
    public static final String DIRECTION_Y_SETTING = "directionY";
    public static final String DIRECTION_Z_SETTING = "directionZ";
    public static final String ANGLE_SETTING = "angleDegrees";
    public static final String SPEED_SETTING = "speed";
    public static final String CURVE_SETTING = "curve";
    public static final String GRADIENT_SETTING = "gradient";
    public static final String VALUE_X_SETTING = "valueX";
    public static final String VALUE_Y_SETTING = "valueY";
    public static final String VALUE_Z_SETTING = "valueZ";
    public static final String VALUE_W_SETTING = "valueW";
    public static final String COMPONENTS_SETTING = "components";
    public static final String SHAPE_SETTING = "shape";
    public static final String RADIUS_SETTING = "radius";
    public static final String RADIUS_THICKNESS_SETTING = "radiusThickness";
    public static final String ARC_SETTING = "arcDegrees";
    public static final String HALF_EXTENTS_X_SETTING = "halfExtentsX";
    public static final String HALF_EXTENTS_Y_SETTING = "halfExtentsY";
    public static final String HALF_EXTENTS_Z_SETTING = "halfExtentsZ";
    public static final String HEIGHT_SETTING = "height";
    public static final String EDGE_LENGTH_SETTING = "edgeLength";
    public static final String MODE_SETTING = "mode";
    public static final String FREQUENCY_SETTING = "frequency";
    public static final String OCTAVES_SETTING = "octaves";
    public static final String STRENGTH_SETTING = "strength";
    public static final String SCROLL_SPEED_X_SETTING = "scrollSpeedX";
    public static final String SCROLL_SPEED_Y_SETTING = "scrollSpeedY";
    public static final String SCROLL_SPEED_Z_SETTING = "scrollSpeedZ";

    public static final String SHAPE_CONE = "Cone";
    public static final String SHAPE_SPHERE = "Sphere";
    public static final String SHAPE_HEMISPHERE = "Hemisphere";
    public static final String SHAPE_BOX = "Box";
    public static final String SHAPE_CIRCLE = "Circle";
    public static final String SHAPE_CYLINDER = "Cylinder";
    public static final String SHAPE_DOT = "Dot";
    public static final String SHAPE_EDGE = "Edge";

    public static final String RENDER_SHAPE_ROUND = "Round";
    public static final String RENDER_SHAPE_RECT = "Rect";

    public static final String PLANE_SETTING = "plane";
    public static final String PLANE_GROUND = "Ground XZ";
    public static final String PLANE_SCREEN = "Screen XY";

    public static final String NOISE_PERLIN = "Perlin";
    public static final String NOISE_FBM = "FBM";
    public static final String NOISE_CURL = "Curl";

    public static final String CATEGORY_OUTPUT = "VFX Output";
    public static final String CATEGORY_PARTICLE = "VFX Particle";
    public static final String CATEGORY_RANDOM = "VFX Random";
    public static final String CATEGORY_VALUE = "VFX Value";
    public static final String CATEGORY_SHAPE = "VFX Shape";
    public static final String CATEGORY_MATH = "VFX Math";

    private VfxNodes() {
    }

    public static void registerInto(GraphNodeRegistry registry) {
        registerOutputs(registry);
        registerParticleInputs(registry);
        registerRandom(registry);
        registerValues(registry);
        registerShapeAndNoise(registry);
        registerBinaryMath(registry);
        registerUnaryMath(registry);
        registerBlendMath(registry);
        registerVectorMath(registry);
    }

    private static void registerOutputs(GraphNodeRegistry registry) {
        registry.register(node(OUTPUT_SPAWN_RATE, "Spawn Rate Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(RATE_PIN, PinType.FLOAT)), List.of(), List.of()));
        registry.register(node(OUTPUT_PARTICLE, "Particle Spawn Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(POSITION_PIN, PinType.VECTOR3),
                        new PinDefinition(VELOCITY_PIN, PinType.VECTOR3),
                        new PinDefinition(LIFETIME_PIN, PinType.FLOAT),
                        new PinDefinition(COLOR_PIN, PinType.VECTOR4),
                        new PinDefinition(SIZE_PIN, PinType.FLOAT),
                        new PinDefinition(SIZE_Y_PIN, PinType.FLOAT),
                        new PinDefinition(ROTATION_PIN, PinType.FLOAT)),
                List.of(), List.of()));
        registry.register(node(OUTPUT_UPDATE, "Particle Update Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(VELOCITY_PIN, PinType.VECTOR3),
                        new PinDefinition(COLOR_PIN, PinType.VECTOR4),
                        new PinDefinition(SIZE_PIN, PinType.FLOAT),
                        new PinDefinition(SIZE_Y_PIN, PinType.FLOAT),
                        new PinDefinition(ANGULAR_VELOCITY_PIN, PinType.FLOAT),
                        new PinDefinition(KILL_PIN, PinType.FLOAT)),
                List.of(), List.of()));
        registry.register(node(OUTPUT_RENDER, "Render Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(SOFT_EDGE_PIN, PinType.FLOAT),
                        new PinDefinition(INTENSITY_PIN, PinType.FLOAT)),
                List.of(), List.of(new NodeSetting(SHAPE_SETTING, SettingKind.TEXT, RENDER_SHAPE_ROUND))));
    }

    private static void registerParticleInputs(GraphNodeRegistry registry) {
        registry.register(source(PARTICLE_AGE, "Particle Age", CATEGORY_PARTICLE, PinType.FLOAT));
        registry.register(source(PARTICLE_AGE_NORMALIZED, "Particle Age Normalized",
                CATEGORY_PARTICLE, PinType.FLOAT));
        registry.register(source(PARTICLE_SEED, "Particle Seed", CATEGORY_PARTICLE, PinType.FLOAT));
        registry.register(source(EMITTER_POSITION, "Emitter Position", CATEGORY_PARTICLE, PinType.VECTOR3));
        registry.register(source(PARTICLE_POSITION, "Particle Position", CATEGORY_PARTICLE, PinType.VECTOR3));
        registry.register(source(PARTICLE_VELOCITY, "Particle Velocity", CATEGORY_PARTICLE, PinType.VECTOR3));
        registry.register(source(EFFECT_TIME_NORMALIZED, "Effect Time Normalized",
                CATEGORY_PARTICLE, PinType.FLOAT));
        registry.register(source(DELTA_TIME, "Delta Time", CATEGORY_PARTICLE, PinType.FLOAT));
    }

    private static void registerRandom(GraphNodeRegistry registry) {
        registry.register(node(RANDOM_RANGE, "Random Range", CATEGORY_RANDOM,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(new NodeSetting(MINIMUM_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(MAXIMUM_SETTING, SettingKind.NUMBER, 1.0f))));
        registry.register(node(RANDOM_RANGE_VECTOR, "Random Range Vector", CATEGORY_RANDOM,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR3)),
                List.of(new NodeSetting(MINIMUM_X_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(MINIMUM_Y_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(MINIMUM_Z_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(MAXIMUM_X_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(MAXIMUM_Y_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(MAXIMUM_Z_SETTING, SettingKind.NUMBER, 1.0f))));
        registry.register(node(CONE_DIRECTION, "Cone Direction", CATEGORY_RANDOM,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR3)),
                List.of(new NodeSetting(DIRECTION_X_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(DIRECTION_Y_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(DIRECTION_Z_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(ANGLE_SETTING, SettingKind.NUMBER, 25.0f),
                        new NodeSetting(SPEED_SETTING, SettingKind.NUMBER, 2.5f))));
    }

    private static void registerValues(GraphNodeRegistry registry) {
        registry.register(node(CONSTANT, "Constant", CATEGORY_VALUE,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.NUMERIC)),
                List.of(new NodeSetting(VALUE_X_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(VALUE_Y_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(VALUE_Z_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(VALUE_W_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(COMPONENTS_SETTING, SettingKind.WHOLE_NUMBER, 1))));
        registry.register(node(CURVE, "Curve", CATEGORY_VALUE,
                List.of(new PinDefinition(TIME_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(new NodeSetting(CURVE_SETTING, SettingKind.CURVE,
                                VfxCurve.linear(0.0f, 1.0f).encode()),
                        new NodeSetting(MINIMUM_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(MAXIMUM_SETTING, SettingKind.NUMBER, 1.0f))));
        registry.register(node(GRADIENT, "Gradient", CATEGORY_VALUE,
                List.of(new PinDefinition(TIME_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR4)),
                List.of(new NodeSetting(GRADIENT_SETTING, SettingKind.GRADIENT,
                        VfxGradient.opaqueWhite().encode()))));
    }

    private static void registerShapeAndNoise(GraphNodeRegistry registry) {
        registerShape(registry);
        registerNoise(registry);
    }

    private static void registerShape(GraphNodeRegistry registry) {
        registry.register(node(SHAPE, "Shape", CATEGORY_SHAPE, List.of(),
                List.of(new PinDefinition(POSITION_PIN, PinType.VECTOR3),
                        new PinDefinition(DIRECTION_PIN, PinType.VECTOR3)),
                List.of(new NodeSetting(SHAPE_SETTING, SettingKind.TEXT, SHAPE_CONE),
                        new NodeSetting(PLANE_SETTING, SettingKind.TEXT, PLANE_GROUND),
                        new NodeSetting(RADIUS_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(RADIUS_THICKNESS_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(ARC_SETTING, SettingKind.NUMBER, 360.0f),
                        new NodeSetting(ANGLE_SETTING, SettingKind.NUMBER, 25.0f),
                        new NodeSetting(HALF_EXTENTS_X_SETTING, SettingKind.NUMBER, 0.5f),
                        new NodeSetting(HALF_EXTENTS_Y_SETTING, SettingKind.NUMBER, 0.5f),
                        new NodeSetting(HALF_EXTENTS_Z_SETTING, SettingKind.NUMBER, 0.5f),
                        new NodeSetting(HEIGHT_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(EDGE_LENGTH_SETTING, SettingKind.NUMBER, 1.0f))));
    }

    private static void registerNoise(GraphNodeRegistry registry) {
        registry.register(node(NOISE, "Noise", CATEGORY_SHAPE,
                List.of(new PinDefinition(POSITION_PIN, PinType.VECTOR3)),
                List.of(new PinDefinition(RESULT_PIN, PinType.NUMERIC)),
                List.of(new NodeSetting(MODE_SETTING, SettingKind.TEXT, NOISE_PERLIN),
                        new NodeSetting(FREQUENCY_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(OCTAVES_SETTING, SettingKind.WHOLE_NUMBER, 4),
                        new NodeSetting(STRENGTH_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(SCROLL_SPEED_X_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(SCROLL_SPEED_Y_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(SCROLL_SPEED_Z_SETTING, SettingKind.NUMBER, 0.0f))));
    }

    private static void registerBinaryMath(GraphNodeRegistry registry) {
        registry.register(binary(MATH_ADD, "Add", PinType.NUMERIC));
        registry.register(binary(MATH_SUBTRACT, "Subtract", PinType.NUMERIC));
        registry.register(binary(MATH_MULTIPLY, "Multiply", PinType.NUMERIC));
        registry.register(binary(MATH_DIVIDE, "Divide", PinType.NUMERIC));
        registry.register(binary(MATH_DOT, "Dot", PinType.FLOAT));
        registry.register(binary(MATH_CROSS, "Cross", PinType.VECTOR3));
    }

    private static void registerUnaryMath(GraphNodeRegistry registry) {
        registry.register(unary(MATH_ONE_MINUS, "One Minus", PinType.NUMERIC));
        registry.register(unary(MATH_NORMALIZE, "Normalize", PinType.NUMERIC));
        registry.register(unary(MATH_LENGTH, "Length", PinType.FLOAT));
        registry.register(unary(MATH_SINE, "Sine", PinType.NUMERIC));
    }

    private static void registerBlendMath(GraphNodeRegistry registry) {
        registry.register(node(MATH_LERP, "Lerp", CATEGORY_MATH,
                List.of(new PinDefinition(A_PIN, PinType.NUMERIC),
                        new PinDefinition(B_PIN, PinType.NUMERIC),
                        new PinDefinition(T_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.NUMERIC)), List.of()));
        registry.register(node(MATH_CLAMP, "Clamp", CATEGORY_MATH,
                List.of(new PinDefinition(VALUE_PIN, PinType.NUMERIC),
                        new PinDefinition(MINIMUM_PIN, PinType.FLOAT),
                        new PinDefinition(MAXIMUM_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.NUMERIC)), List.of()));
        registry.register(node(MATH_REMAP, "Remap", CATEGORY_MATH,
                List.of(new PinDefinition(VALUE_PIN, PinType.NUMERIC),
                        new PinDefinition(FROM_MINIMUM_PIN, PinType.FLOAT),
                        new PinDefinition(FROM_MAXIMUM_PIN, PinType.FLOAT),
                        new PinDefinition(TO_MINIMUM_PIN, PinType.FLOAT),
                        new PinDefinition(TO_MAXIMUM_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.NUMERIC)), List.of()));
    }

    private static void registerVectorMath(GraphNodeRegistry registry) {
        registry.register(node(MATH_VECTOR3, "Vector3", CATEGORY_MATH,
                List.of(new PinDefinition(X_PIN, PinType.FLOAT),
                        new PinDefinition(Y_PIN, PinType.FLOAT),
                        new PinDefinition(Z_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR3)), List.of()));
        registry.register(node(MATH_VECTOR4, "Vector4", CATEGORY_MATH,
                List.of(new PinDefinition(X_PIN, PinType.FLOAT),
                        new PinDefinition(Y_PIN, PinType.FLOAT),
                        new PinDefinition(Z_PIN, PinType.FLOAT),
                        new PinDefinition(W_PIN, PinType.FLOAT)),
                List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR4)), List.of()));
        registry.register(node(MATH_SPLIT_VECTOR3, "Split Vector3", CATEGORY_MATH,
                List.of(new PinDefinition(VALUE_PIN, PinType.VECTOR3)),
                List.of(new PinDefinition(X_PIN, PinType.FLOAT),
                        new PinDefinition(Y_PIN, PinType.FLOAT),
                        new PinDefinition(Z_PIN, PinType.FLOAT)), List.of()));
        registry.register(node(MATH_SPLIT_VECTOR4, "Split Vector4", CATEGORY_MATH,
                List.of(new PinDefinition(VALUE_PIN, PinType.VECTOR4)),
                List.of(new PinDefinition(X_PIN, PinType.FLOAT),
                        new PinDefinition(Y_PIN, PinType.FLOAT),
                        new PinDefinition(Z_PIN, PinType.FLOAT),
                        new PinDefinition(W_PIN, PinType.FLOAT)), List.of()));
    }

    private static NodeDefinition binary(String typeKey, String displayName, PinType resultType) {
        return node(typeKey, displayName, CATEGORY_MATH,
                List.of(new PinDefinition(A_PIN, PinType.NUMERIC),
                        new PinDefinition(B_PIN, PinType.NUMERIC)),
                List.of(new PinDefinition(RESULT_PIN, resultType)), List.of());
    }

    private static NodeDefinition unary(String typeKey, String displayName, PinType resultType) {
        return node(typeKey, displayName, CATEGORY_MATH,
                List.of(new PinDefinition(VALUE_PIN, PinType.NUMERIC)),
                List.of(new PinDefinition(RESULT_PIN, resultType)), List.of());
    }

    private static NodeDefinition source(String typeKey, String displayName, String category,
                                         PinType resultType) {
        return node(typeKey, displayName, category, List.of(),
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
