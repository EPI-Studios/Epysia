package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphValues;
import fr.epistudio.epysia.vfx.lut.VfxLutPack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class VfxExpressionEmitter {

    private final GraphAsset asset;
    private final VfxStage stage;
    private final boolean shapeLibrary;
    private final boolean noiseLibrary;
    private final VfxMathEmitter math = new VfxMathEmitter(this);
    private final Set<Integer> visiting = new HashSet<>();

    VfxExpressionEmitter(GraphAsset asset, VfxStage stage, boolean shapeLibrary, boolean noiseLibrary) {
        this.asset = asset;
        this.stage = stage;
        this.shapeLibrary = shapeLibrary;
        this.noiseLibrary = noiseLibrary;
    }

    VfxStage stage() {
        return stage;
    }

    String pinExpression(GraphNode target, String pin, VfxExpression fallback) {
        return inputOf(target, pin, fallback).asComponents(fallback.components());
    }

    VfxExpression inputOf(GraphNode target, String pin, VfxExpression fallback) {
        Optional<GraphEdge> edge = asset.edgeInto(target.id(), pin);
        if (edge.isPresent()) {
            GraphNode source = asset.findNode(edge.get().fromNode()).orElseThrow(() ->
                    new EpysiaException("VFX graph edge references a missing node."));
            return emit(source, edge.get().fromPin());
        }
        Object value = target.values().get(pin);
        return value == null ? fallback : literal(value, fallback.components());
    }

    private static VfxExpression literal(Object value, int components) {
        if (components == 4) {
            Vector4f vector = GraphValues.asVector4(value);
            return VfxExpression.vector4("vec4(%s, %s, %s, %s)".formatted(text(vector.x), text(vector.y),
                    text(vector.z), text(vector.w)));
        }
        if (components == 3) {
            Vector3f vector = GraphValues.asVector(value);
            return VfxExpression.vector3("vec3(%s, %s, %s)".formatted(text(vector.x), text(vector.y),
                    text(vector.z)));
        }
        return VfxExpression.literal(GraphValues.asFloat(value));
    }

    VfxExpression emit(GraphNode source, String outputPin) {
        if (!visiting.add(source.id())) {
            throw new EpysiaException("VFX graph contains a cycle through node " + source.id() + ".");
        }
        try {
            return emitByType(source, outputPin);
        } finally {
            visiting.remove(source.id());
        }
    }

    private VfxExpression emitByType(GraphNode source, String outputPin) {
        return switch (source.typeKey()) {
            case VfxNodes.PARTICLE_AGE -> VfxExpression.scalar(updateVariable("age"));
            case VfxNodes.PARTICLE_AGE_NORMALIZED -> VfxExpression.scalar(updateVariable("ageNormalized"));
            case VfxNodes.PARTICLE_POSITION -> VfxExpression.vector3(updateVariable("particle.positionAge.xyz"));
            case VfxNodes.PARTICLE_VELOCITY -> VfxExpression.vector3(updateVariable("particle.velocityLifetime.xyz"));
            case VfxNodes.PARTICLE_SEED -> VfxExpression.scalar(computeVariable("particleSeed"));
            case VfxNodes.EMITTER_POSITION -> VfxExpression.vector3(computeVariable("effect.emitterPositionDelta.xyz"));
            case VfxNodes.EFFECT_TIME_NORMALIZED -> VfxExpression.scalar(computeVariable("effectNormalizedTime()"));
            case VfxNodes.DELTA_TIME -> VfxExpression.scalar(computeVariable("effect.emitterPositionDelta.w"));
            case VfxNodes.RANDOM_RANGE -> emitRandomRange(source);
            case VfxNodes.RANDOM_RANGE_VECTOR -> emitRandomRangeVector(source);
            case VfxNodes.CONE_DIRECTION -> emitConeDirection(source);
            case VfxNodes.CONSTANT -> emitConstant(source);
            case VfxNodes.CURVE -> emitCurve(source);
            case VfxNodes.GRADIENT -> emitGradient(source);
            case VfxNodes.SHAPE -> emitShape(source, outputPin);
            case VfxNodes.NOISE -> emitNoise(source);
            default -> math.emit(source, outputPin);
        };
    }

    private String updateVariable(String variable) {
        if (stage != VfxStage.PARTICLE_UPDATE) {
            throw new EpysiaException("Node reading " + variable + " is only available in the update stage.");
        }
        return variable;
    }

    private String computeVariable(String variable) {
        if (stage == VfxStage.RENDER) {
            throw new EpysiaException("Node reading " + variable + " is not available in the render stage.");
        }
        return variable;
    }

    private String randomKey() {
        return switch (stage) {
            case PARTICLE_SPAWN -> "spawnKey";
            case PARTICLE_UPDATE -> "uint(particleSeed * 16777215.0)";
            case SYSTEM_SPAWN, RENDER -> throw new EpysiaException(
                    "Random nodes are only available in the particle spawn and update stages.");
        };
    }

    private VfxExpression emitRandomRange(GraphNode source) {
        float minimum = settingFloat(source, VfxNodes.MINIMUM_SETTING, 0.0f);
        float maximum = settingFloat(source, VfxNodes.MAXIMUM_SETTING, 1.0f);
        return VfxExpression.scalar("randomRange(%s, %s, %s, %du)".formatted(
                text(minimum), text(maximum), randomKey(), source.id()));
    }

    private VfxExpression emitRandomRangeVector(GraphNode source) {
        String key = randomKey();
        return VfxExpression.vector3("vec3(%s, %s, %s)".formatted(
                randomComponent(source, VfxNodes.MINIMUM_X_SETTING, VfxNodes.MAXIMUM_X_SETTING, key, 1),
                randomComponent(source, VfxNodes.MINIMUM_Y_SETTING, VfxNodes.MAXIMUM_Y_SETTING, key, 2),
                randomComponent(source, VfxNodes.MINIMUM_Z_SETTING, VfxNodes.MAXIMUM_Z_SETTING, key, 3)));
    }

    private String randomComponent(GraphNode source, String minimumKey, String maximumKey,
                                   String key, int salt) {
        return "randomRange(%s, %s, %s, %du)".formatted(
                text(settingFloat(source, minimumKey, 0.0f)),
                text(settingFloat(source, maximumKey, 1.0f)),
                key, source.id() * 3 + salt);
    }

    private VfxExpression emitConeDirection(GraphNode source) {
        String key = randomKey();
        float speed = settingFloat(source, VfxNodes.SPEED_SETTING, 1.0f);
        return VfxExpression.vector3("coneDirection(normalize(vec3(%s, %s, %s)), %s, %s) * %s".formatted(
                text(settingFloat(source, VfxNodes.DIRECTION_X_SETTING, 0.0f)),
                text(settingFloat(source, VfxNodes.DIRECTION_Y_SETTING, 1.0f)),
                text(settingFloat(source, VfxNodes.DIRECTION_Z_SETTING, 0.0f)),
                text(settingFloat(source, VfxNodes.ANGLE_SETTING, 25.0f)), key, text(speed)));
    }

    private VfxExpression emitConstant(GraphNode source) {
        int components = GraphValues.asInt(source.values().getOrDefault(VfxNodes.COMPONENTS_SETTING, 1));
        float x = settingFloat(source, VfxNodes.VALUE_X_SETTING, 0.0f);
        float y = settingFloat(source, VfxNodes.VALUE_Y_SETTING, 0.0f);
        float z = settingFloat(source, VfxNodes.VALUE_Z_SETTING, 0.0f);
        float w = settingFloat(source, VfxNodes.VALUE_W_SETTING, 1.0f);
        if (components >= 4) {
            return VfxExpression.vector4("vec4(%s, %s, %s, %s)".formatted(text(x), text(y), text(z), text(w)));
        }
        if (components == 3) {
            return VfxExpression.vector3("vec3(%s, %s, %s)".formatted(text(x), text(y), text(z)));
        }
        return VfxExpression.literal(x);
    }

    private VfxExpression emitCurve(GraphNode source) {
        String time = inputOf(source, VfxNodes.TIME_PIN, defaultTime()).asFloat();
        float minimum = settingFloat(source, VfxNodes.MINIMUM_SETTING, 0.0f);
        float maximum = settingFloat(source, VfxNodes.MAXIMUM_SETTING, 1.0f);
        int index = VfxLutPack.curveIndexOf(asset, source.id(), VfxNodes.CURVE_SETTING);
        String progress = index == VfxLutPack.MISSING_INDEX
                ? "clamp(%s, 0.0, 1.0)".formatted(time)
                : "sampleCurve(%d, %s)".formatted(index, time);
        return VfxExpression.scalar("mix(%s, %s, %s)".formatted(text(minimum), text(maximum), progress));
    }

    private VfxExpression emitGradient(GraphNode source) {
        String time = inputOf(source, VfxNodes.TIME_PIN, defaultTime()).asFloat();
        int index = VfxLutPack.gradientIndexOf(asset, source.id(), VfxNodes.GRADIENT_SETTING);
        if (index == VfxLutPack.MISSING_INDEX) {
            return VfxExpression.vector4("vec4(1.0, 1.0, 1.0, 1.0)");
        }
        return VfxExpression.vector4("sampleGradient(%d, %s)".formatted(index, time));
    }

    private VfxExpression defaultTime() {
        if (stage == VfxStage.PARTICLE_UPDATE) {
            return VfxExpression.scalar("ageNormalized");
        }
        return VfxExpression.scalar(computeVariable("effectNormalizedTime()"));
    }

    private VfxExpression emitShape(GraphNode source, String outputPin) {
        if (!shapeLibrary) {
            throw new EpysiaException("Shape nodes need the shape library source passed to VfxGraphCompiler.");
        }
        String field = VfxNodes.DIRECTION_PIN.equals(outputPin) ? ".direction" : ".position";
        return VfxExpression.vector3(shapeCall(source) + field);
    }

    private String shapeCall(GraphNode source) {
        String key = randomKey();
        String radius = text(settingFloat(source, VfxNodes.RADIUS_SETTING, 1.0f));
        String thickness = text(settingFloat(source, VfxNodes.RADIUS_THICKNESS_SETTING, 1.0f));
        String arc = text(settingFloat(source, VfxNodes.ARC_SETTING, 360.0f));
        String angle = text(settingFloat(source, VfxNodes.ANGLE_SETTING, 25.0f));
        return switch (shapeMode(source)) {
            case VfxNodes.SHAPE_SPHERE -> "shapeSphere(%s, %s, %s)".formatted(radius, thickness, key);
            case VfxNodes.SHAPE_HEMISPHERE -> "shapeHemisphere(%s, %s, %s)".formatted(radius, thickness, key);
            case VfxNodes.SHAPE_BOX -> "shapeBox(%s, %s, %s)".formatted(halfExtents(source), thickness, key);
            case VfxNodes.SHAPE_CIRCLE -> "shapeCircle(%s, %s, %s, %s)".formatted(radius, thickness, arc, key);
            case VfxNodes.SHAPE_CYLINDER -> "shapeCylinder(%s, %s, %s, %s, %s)".formatted(radius, thickness,
                    text(settingFloat(source, VfxNodes.HEIGHT_SETTING, 1.0f)), arc, key);
            case VfxNodes.SHAPE_DOT -> "shapeDot(%s)".formatted(key);
            case VfxNodes.SHAPE_EDGE -> "shapeEdge(%s, %s)".formatted(
                    text(settingFloat(source, VfxNodes.EDGE_LENGTH_SETTING, 1.0f)), key);
            default -> "shapeCone(%s, %s, %s, %s, %s)".formatted(radius, thickness, arc, angle, key);
        };
    }

    private static String shapeMode(GraphNode source) {
        String mode = GraphValues.asString(source.values().getOrDefault(
                VfxNodes.SHAPE_SETTING, VfxNodes.SHAPE_CONE));
        return mode.isBlank() ? VfxNodes.SHAPE_CONE : mode;
    }

    private static String halfExtents(GraphNode source) {
        return "vec3(%s, %s, %s)".formatted(
                text(settingFloat(source, VfxNodes.HALF_EXTENTS_X_SETTING, 0.5f)),
                text(settingFloat(source, VfxNodes.HALF_EXTENTS_Y_SETTING, 0.5f)),
                text(settingFloat(source, VfxNodes.HALF_EXTENTS_Z_SETTING, 0.5f)));
    }

    private VfxExpression emitNoise(GraphNode source) {
        if (!noiseLibrary) {
            throw new EpysiaException("Noise nodes need the noise library source passed to VfxGraphCompiler.");
        }
        String point = "(%s) * %s".formatted(scrolledNoisePoint(source),
                text(settingFloat(source, VfxNodes.FREQUENCY_SETTING, 1.0f)));
        String strength = text(settingFloat(source, VfxNodes.STRENGTH_SETTING, 1.0f));
        String mode = GraphValues.asString(source.values().getOrDefault(
                VfxNodes.MODE_SETTING, VfxNodes.NOISE_PERLIN));
        if (VfxNodes.NOISE_CURL.equals(mode)) {
            return VfxExpression.vector3("(curlNoise(%s) * %s)".formatted(point, strength));
        }
        if (VfxNodes.NOISE_FBM.equals(mode)) {
            int octaves = GraphValues.asInt(source.values().getOrDefault(VfxNodes.OCTAVES_SETTING, 4));
            return VfxExpression.scalar("(fbm3(%s, %d) * %s)".formatted(point, Math.max(octaves, 1), strength));
        }
        return VfxExpression.scalar("(perlin3(%s) * %s)".formatted(point, strength));
    }

    private String scrolledNoisePoint(GraphNode source) {
        String position = inputOf(source, VfxNodes.POSITION_PIN, defaultNoisePosition()).asVector3();
        float scrollX = settingFloat(source, VfxNodes.SCROLL_SPEED_X_SETTING, 0.0f);
        float scrollY = settingFloat(source, VfxNodes.SCROLL_SPEED_Y_SETTING, 0.0f);
        float scrollZ = settingFloat(source, VfxNodes.SCROLL_SPEED_Z_SETTING, 0.0f);
        if (scrollX == 0.0f && scrollY == 0.0f && scrollZ == 0.0f) {
            return position;
        }
        return "%s + vec3(%s, %s, %s) * %s".formatted(position, text(scrollX), text(scrollY),
                text(scrollZ), computeVariable("effect.effectClock.y"));
    }

    private VfxExpression defaultNoisePosition() {
        if (stage == VfxStage.PARTICLE_UPDATE) {
            return VfxExpression.vector3("particle.positionAge.xyz");
        }
        return VfxExpression.vector3(computeVariable("effect.emitterPositionDelta.xyz"));
    }

    static float settingFloat(GraphNode node, String setting, float fallback) {
        return GraphValues.asFloat(node.values().getOrDefault(setting, fallback));
    }

    static String text(float value) {
        return VfxExpression.floatText(value);
    }
}
