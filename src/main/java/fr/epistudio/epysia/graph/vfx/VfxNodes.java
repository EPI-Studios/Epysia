package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.graph.GraphNodeRegistry;
import fr.epistudio.epysia.graph.NodeDefinition;
import fr.epistudio.epysia.graph.NodeSetting;
import fr.epistudio.epysia.graph.PinDefinition;
import fr.epistudio.epysia.graph.PinType;
import fr.epistudio.epysia.graph.SettingKind;

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
    public static final String DELTA_TIME = "vfx.deltaTime";
    public static final String RANDOM_RANGE = "vfx.randomRange";
    public static final String CONE_DIRECTION = "vfx.coneDirection";

    public static final String RATE_PIN = "Rate";
    public static final String POSITION_PIN = "Position";
    public static final String VELOCITY_PIN = "Velocity";
    public static final String LIFETIME_PIN = "Lifetime";
    public static final String COLOR_PIN = "Color";
    public static final String SIZE_PIN = "Size";
    public static final String KILL_PIN = "Kill";
    public static final String SOFT_EDGE_PIN = "Soft Edge";
    public static final String RESULT_PIN = "Result";

    public static final String MINIMUM_SETTING = "minimum";
    public static final String MAXIMUM_SETTING = "maximum";
    public static final String DIRECTION_X_SETTING = "directionX";
    public static final String DIRECTION_Y_SETTING = "directionY";
    public static final String DIRECTION_Z_SETTING = "directionZ";
    public static final String ANGLE_SETTING = "angleDegrees";
    public static final String SPEED_SETTING = "speed";

    public static final String CATEGORY_OUTPUT = "VFX Output";
    public static final String CATEGORY_PARTICLE = "VFX Particle";
    public static final String CATEGORY_RANDOM = "VFX Random";

    private VfxNodes() {
    }

    public static void registerInto(GraphNodeRegistry registry) {
        registerOutputs(registry);
        registerParticleInputs(registry);
        registerRandom(registry);
    }

    private static void registerOutputs(GraphNodeRegistry registry) {
        registry.register(node(OUTPUT_SPAWN_RATE, "Spawn Rate Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(RATE_PIN, PinType.FLOAT)), List.of(), List.of()));
        registry.register(node(OUTPUT_PARTICLE, "Particle Spawn Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(POSITION_PIN, PinType.VECTOR3),
                        new PinDefinition(VELOCITY_PIN, PinType.VECTOR3),
                        new PinDefinition(LIFETIME_PIN, PinType.FLOAT),
                        new PinDefinition(COLOR_PIN, PinType.VECTOR4),
                        new PinDefinition(SIZE_PIN, PinType.FLOAT)),
                List.of(), List.of()));
        registry.register(node(OUTPUT_UPDATE, "Particle Update Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(VELOCITY_PIN, PinType.VECTOR3),
                        new PinDefinition(COLOR_PIN, PinType.VECTOR4),
                        new PinDefinition(SIZE_PIN, PinType.FLOAT),
                        new PinDefinition(KILL_PIN, PinType.FLOAT)),
                List.of(), List.of()));
        registry.register(node(OUTPUT_RENDER, "Render Output", CATEGORY_OUTPUT,
                List.of(new PinDefinition(SOFT_EDGE_PIN, PinType.FLOAT)),
                List.of(), List.of()));
    }

    private static void registerParticleInputs(GraphNodeRegistry registry) {
        registry.register(node(PARTICLE_AGE, "Particle Age", CATEGORY_PARTICLE,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)), List.of()));
        registry.register(node(PARTICLE_AGE_NORMALIZED, "Particle Age Normalized", CATEGORY_PARTICLE,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)), List.of()));
        registry.register(node(PARTICLE_SEED, "Particle Seed", CATEGORY_PARTICLE,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)), List.of()));
        registry.register(node(EMITTER_POSITION, "Emitter Position", CATEGORY_PARTICLE,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR3)), List.of()));
        registry.register(node(DELTA_TIME, "Delta Time", CATEGORY_PARTICLE,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)), List.of()));
    }

    private static void registerRandom(GraphNodeRegistry registry) {
        registry.register(node(RANDOM_RANGE, "Random Range", CATEGORY_RANDOM,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.FLOAT)),
                List.of(new NodeSetting(MINIMUM_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(MAXIMUM_SETTING, SettingKind.NUMBER, 1.0f))));
        registry.register(node(CONE_DIRECTION, "Cone Direction", CATEGORY_RANDOM,
                List.of(), List.of(new PinDefinition(RESULT_PIN, PinType.VECTOR3)),
                List.of(new NodeSetting(DIRECTION_X_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(DIRECTION_Y_SETTING, SettingKind.NUMBER, 1.0f),
                        new NodeSetting(DIRECTION_Z_SETTING, SettingKind.NUMBER, 0.0f),
                        new NodeSetting(ANGLE_SETTING, SettingKind.NUMBER, 25.0f),
                        new NodeSetting(SPEED_SETTING, SettingKind.NUMBER, 2.5f))));
    }

    private static NodeDefinition node(String typeKey, String displayName, String category,
                                       List<PinDefinition> inputs, List<PinDefinition> outputs,
                                       List<NodeSetting> settings) {
        return new NodeDefinition(typeKey, displayName, category, false, false,
                inputs, outputs, settings, context -> {
                });
    }
}
