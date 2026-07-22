package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphEdge;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphValues;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Locale;
import java.util.Optional;

public final class VfxGraphCompiler {

    private final String commonSource;

    public VfxGraphCompiler(String commonSource) {
        this.commonSource = commonSource;
    }

    public VfxCompiledSources compile(GraphAsset asset, String sourceName) {
        if (asset.kind() != GraphKind.VFX) {
            throw new EpysiaException("Graph " + sourceName + " is not a VFX graph.");
        }
        return new VfxCompiledSources(
                compileSpawn(asset),
                compileUpdate(asset),
                compileRender(asset),
                spawnRate(asset));
    }

    public record VfxCompiledSources(String spawnCompute, String updateCompute,
                                     String fragmentBody, float spawnRatePerSecond) {
    }

    private float spawnRate(GraphAsset asset) {
        Optional<GraphNode> output = findOutput(asset, VfxNodes.OUTPUT_SPAWN_RATE);
        if (output.isEmpty()) {
            return 100.0f;
        }
        Optional<GraphEdge> wired = asset.edgeInto(output.get().id(), VfxNodes.RATE_PIN);
        if (wired.isEmpty()) {
            return pinLiteral(output.get(), VfxNodes.RATE_PIN, 100.0f);
        }
        throw new EpysiaException("Spawn Rate accepts only a literal value in this milestone.");
    }

    private String compileSpawn(GraphAsset asset) {
        Optional<GraphNode> output = findOutput(asset, VfxNodes.OUTPUT_PARTICLE);
        String position = "effect.emitterPositionDelta.xyz";
        String velocity = "coneDirection(vec3(0.0, 1.0, 0.0), 25.0, spawnKey) * 2.5";
        String lifetime = "2.0";
        String color = "vec4(1.0, 1.0, 1.0, 1.0)";
        String size = "0.1";
        if (output.isPresent()) {
            GraphNode node = output.get();
            position = expressionFor(asset, node, VfxNodes.POSITION_PIN, position, VfxStage.PARTICLE_SPAWN);
            velocity = expressionFor(asset, node, VfxNodes.VELOCITY_PIN, velocity, VfxStage.PARTICLE_SPAWN);
            lifetime = expressionFor(asset, node, VfxNodes.LIFETIME_PIN, lifetime, VfxStage.PARTICLE_SPAWN);
            color = expressionFor(asset, node, VfxNodes.COLOR_PIN, color, VfxStage.PARTICLE_SPAWN);
            size = expressionFor(asset, node, VfxNodes.SIZE_PIN, size, VfxStage.PARTICLE_SPAWN);
        }
        return """
                #version 430 core
                %s
                %s
                layout(local_size_x = 64) in;

                void main() {
                    uint invocation = gl_GlobalInvocationID.x;
                    if (invocation >= effect.spawnSeedPool.x) {
                        return;
                    }
                    int previousTop = atomicAdd(freeTop, -1);
                    if (previousTop <= 0) {
                        atomicAdd(freeTop, 1);
                        return;
                    }
                    uint slot = freeEntries[previousTop - 1];
                    uint spawnKey = effect.spawnSeedPool.y * 9781u + effect.spawnSeedPool.z + invocation;
                    float particleSeed = hashFloat(spawnKey);
                    particles[slot].positionAge = vec4(%s, 0.0);
                    particles[slot].velocityLifetime = vec4(%s, %s);
                    particles[slot].color = %s;
                    particles[slot].sizeRotation = vec4(%s, 0.0, 0.0, 0.0);
                    particles[slot].seedUser = vec4(particleSeed, 0.0, 0.0, 0.0);
                    particles[slot].userExtra = vec4(0.0);
                }
                """.formatted(commonSource, helperFunctions(), position, velocity, lifetime, color, size);
    }

    private String compileUpdate(GraphAsset asset) {
        Optional<GraphNode> output = findOutput(asset, VfxNodes.OUTPUT_UPDATE);
        String velocity = "particle.velocityLifetime.xyz + vec3(0.0, -4.0, 0.0) * deltaTime";
        String color = "vec4(particle.color.rgb, 1.0 - ageNormalized)";
        String size = "particle.sizeRotation.x";
        String kill = "0.0";
        if (output.isPresent()) {
            GraphNode node = output.get();
            velocity = expressionFor(asset, node, VfxNodes.VELOCITY_PIN, velocity, VfxStage.PARTICLE_UPDATE);
            color = expressionFor(asset, node, VfxNodes.COLOR_PIN, color, VfxStage.PARTICLE_UPDATE);
            size = expressionFor(asset, node, VfxNodes.SIZE_PIN, size, VfxStage.PARTICLE_UPDATE);
            kill = expressionFor(asset, node, VfxNodes.KILL_PIN, kill, VfxStage.PARTICLE_UPDATE);
        }
        return """
                #version 430 core
                %s
                %s
                layout(local_size_x = 64) in;

                void main() {
                    uint slot = gl_GlobalInvocationID.x;
                    if (slot >= effect.spawnSeedPool.w) {
                        return;
                    }
                    Particle particle = particles[slot];
                    if (particle.velocityLifetime.w <= 0.0) {
                        return;
                    }
                    float deltaTime = effect.emitterPositionDelta.w;
                    float particleSeed = particle.seedUser.x;
                    float age = particle.positionAge.w + deltaTime;
                    float ageNormalized = age / particle.velocityLifetime.w;
                    if (age >= particle.velocityLifetime.w || (%s) > 0.5) {
                        particles[slot].velocityLifetime.w = 0.0;
                        int previousTop = atomicAdd(freeTop, 1);
                        freeEntries[previousTop] = slot;
                        return;
                    }
                    vec3 velocity = %s;
                    particles[slot].positionAge = vec4(particle.positionAge.xyz + velocity * deltaTime, age);
                    particles[slot].velocityLifetime.xyz = velocity;
                    particles[slot].color = %s;
                    particles[slot].sizeRotation.x = %s;
                    uint drawIndex = atomicAdd(instanceCount, 1u);
                    aliveIndices[drawIndex] = slot;
                }
                """.formatted(commonSource, helperFunctions(), kill, velocity, color, size);
    }

    private String compileRender(GraphAsset asset) {
        String softEdge = "1.0";
        Optional<GraphNode> output = findOutput(asset, VfxNodes.OUTPUT_RENDER);
        if (output.isPresent()) {
            softEdge = expressionFor(asset, output.get(), VfxNodes.SOFT_EDGE_PIN, softEdge, VfxStage.RENDER);
        }
        return """
                    float distanceFromCenter = length(particleCorner);
                    float softEdge = %s;
                    float falloff = clamp(1.0 - distanceFromCenter, 0.0, 1.0);
                    falloff = pow(falloff, max(softEdge * 2.0, 0.25));
                    fragmentColor = vec4(particleColor.rgb * particleColor.a * falloff, 1.0);
                """.formatted(softEdge);
    }

    private static String helperFunctions() {
        return """
                vec3 coneDirection(vec3 axis, float angleDegrees, uint spawnKey) {
                    float angle = hashFloat(spawnKey * 5u + 11u) * 6.2831853;
                    float spread = hashFloat(spawnKey * 5u + 13u) * radians(angleDegrees);
                    vec3 reference = abs(axis.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
                    vec3 tangent = normalize(cross(reference, axis));
                    vec3 bitangent = cross(axis, tangent);
                    vec3 tilted = normalize(axis * cos(spread) + (tangent * cos(angle) + bitangent * sin(angle)) * sin(spread));
                    return tilted;
                }

                float randomRange(float minimum, float maximum, uint spawnKey, uint salt) {
                    return minimum + hashFloat(spawnKey * 7u + salt) * (maximum - minimum);
                }
                """;
    }

    private String expressionFor(GraphAsset asset, GraphNode outputNode, String pin,
                                 String fallback, VfxStage stage) {
        Optional<GraphEdge> edge = asset.edgeInto(outputNode.id(), pin);
        if (edge.isEmpty()) {
            return literalOrFallback(outputNode, pin, fallback);
        }
        GraphNode source = asset.findNode(edge.get().fromNode()).orElseThrow(() ->
                new EpysiaException("VFX graph edge references a missing node."));
        return emitSource(source, stage);
    }

    private static String literalOrFallback(GraphNode outputNode, String pin, String fallback) {
        Object value = outputNode.values().get(pin);
        if (value == null) {
            return fallback;
        }
        if (fallback.startsWith("vec4")) {
            Vector4f vector = GraphValues.asVector4(value);
            return "vec4(%s, %s, %s, %s)".formatted(floatText(vector.x), floatText(vector.y),
                    floatText(vector.z), floatText(vector.w));
        }
        if (fallback.startsWith("vec3") || fallback.contains(".xyz") || fallback.startsWith("coneDirection")
                || fallback.startsWith("particle.velocityLifetime.xyz")) {
            Vector3f vector = GraphValues.asVector(value);
            return "vec3(%s, %s, %s)".formatted(floatText(vector.x), floatText(vector.y), floatText(vector.z));
        }
        return floatText(GraphValues.asFloat(value));
    }

    private String emitSource(GraphNode source, VfxStage stage) {
        return switch (source.typeKey()) {
            case VfxNodes.PARTICLE_AGE -> requireUpdateStage(stage, "age");
            case VfxNodes.PARTICLE_AGE_NORMALIZED -> requireUpdateStage(stage, "ageNormalized");
            case VfxNodes.PARTICLE_SEED -> "particleSeed";
            case VfxNodes.EMITTER_POSITION -> "effect.emitterPositionDelta.xyz";
            case VfxNodes.DELTA_TIME -> "effect.emitterPositionDelta.w";
            case VfxNodes.RANDOM_RANGE -> emitRandomRange(source, stage);
            case VfxNodes.CONE_DIRECTION -> emitConeDirection(source, stage);
            default -> throw new EpysiaException(
                    "VFX graphs do not support node " + source.typeKey() + " in this milestone.");
        };
    }

    private static String requireUpdateStage(VfxStage stage, String variable) {
        if (stage != VfxStage.PARTICLE_UPDATE) {
            throw new EpysiaException("Particle age is only available in the update stage.");
        }
        return variable;
    }

    private String emitRandomRange(GraphNode source, VfxStage stage) {
        requireSpawnStage(stage, "Random Range");
        float minimum = settingFloat(source, VfxNodes.MINIMUM_SETTING, 0.0f);
        float maximum = settingFloat(source, VfxNodes.MAXIMUM_SETTING, 1.0f);
        return "randomRange(%s, %s, spawnKey, %su)".formatted(
                floatText(minimum), floatText(maximum), source.id());
    }

    private String emitConeDirection(GraphNode source, VfxStage stage) {
        requireSpawnStage(stage, "Cone Direction");
        float directionX = settingFloat(source, VfxNodes.DIRECTION_X_SETTING, 0.0f);
        float directionY = settingFloat(source, VfxNodes.DIRECTION_Y_SETTING, 1.0f);
        float directionZ = settingFloat(source, VfxNodes.DIRECTION_Z_SETTING, 0.0f);
        float angle = settingFloat(source, VfxNodes.ANGLE_SETTING, 25.0f);
        float speed = settingFloat(source, VfxNodes.SPEED_SETTING, 1.0f);
        return "coneDirection(normalize(vec3(%s, %s, %s)), %s, spawnKey) * %s".formatted(
                floatText(directionX), floatText(directionY), floatText(directionZ),
                floatText(angle), floatText(speed));
    }

    private static void requireSpawnStage(VfxStage stage, String nodeName) {
        if (stage != VfxStage.PARTICLE_SPAWN) {
            throw new EpysiaException(nodeName + " is only available in the particle spawn stage.");
        }
    }

    private static float settingFloat(GraphNode node, String setting, float fallback) {
        return GraphValues.asFloat(node.values().getOrDefault(setting, fallback));
    }

    private static float pinLiteral(GraphNode node, String pin, float fallback) {
        return GraphValues.asFloat(node.values().getOrDefault(pin, fallback));
    }

    private static String floatText(float value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static Optional<GraphNode> findOutput(GraphAsset asset, String typeKey) {
        return asset.nodes().stream().filter(node -> node.typeKey().equals(typeKey)).findFirst();
    }
}
