package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.GraphNode;

import java.util.Arrays;
import java.util.Optional;

public final class VfxGraphCompiler {

    private final String commonSource;
    private final String shapeSource;
    private final String noiseSource;

    public VfxGraphCompiler(String commonSource) {
        this(commonSource, "", "");
    }

    public VfxGraphCompiler(String commonSource, String shapeSource, String noiseSource) {
        this.commonSource = commonSource;
        this.shapeSource = shapeSource;
        this.noiseSource = noiseSource;
    }

    public VfxCompiledSources compile(GraphAsset asset, String sourceName) {
        if (asset.kind() != GraphKind.VFX) {
            throw new EpysiaException("Graph " + sourceName + " is not a VFX graph.");
        }
        float[] spawnRateSamples = spawnRateSamples(asset);
        return new VfxCompiledSources(
                compileSpawn(asset),
                compileUpdate(asset),
                compileRender(asset),
                VfxSpawnRateEvaluator.mean(spawnRateSamples),
                spawnRateSamples);
    }

    public record VfxCompiledSources(String spawnCompute, String updateCompute,
                                     String fragmentBody, float spawnRatePerSecond,
                                     float[] spawnRateSamples) {

        public float spawnRateAt(float normalizedTime) {
            if (spawnRateSamples.length == 0) {
                return spawnRatePerSecond;
            }
            float position = Math.min(Math.max(normalizedTime, 0.0f), 1.0f) * (spawnRateSamples.length - 1);
            int lower = (int) Math.floor(position);
            int upper = Math.min(lower + 1, spawnRateSamples.length - 1);
            return spawnRateSamples[lower]
                    + (spawnRateSamples[upper] - spawnRateSamples[lower]) * (position - lower);
        }
    }

    private float[] spawnRateSamples(GraphAsset asset) {
        Optional<GraphNode> output = findOutput(asset, VfxNodes.OUTPUT_SPAWN_RATE);
        if (output.isEmpty()) {
            return constantSamples(100.0f);
        }
        return new VfxSpawnRateEvaluator(asset).samples(output.get(), VfxNodes.RATE_PIN, 100.0f);
    }

    private static float[] constantSamples(float rate) {
        float[] samples = new float[VfxSpawnRateEvaluator.SAMPLE_COUNT];
        Arrays.fill(samples, rate);
        return samples;
    }

    private String compileSpawn(GraphAsset asset) {
        VfxExpressionEmitter emitter = emitterFor(asset, VfxStage.PARTICLE_SPAWN);
        Optional<GraphNode> output = findOutput(asset, VfxNodes.OUTPUT_PARTICLE);
        String position = "vec3(0.0, 0.0, 0.0)";
        String velocity = "coneDirection(vec3(0.0, 1.0, 0.0), 25.0, spawnKey) * 2.5";
        String lifetime = "2.0";
        String color = "vec4(1.0, 1.0, 1.0, 1.0)";
        String size = "0.1";
        if (output.isPresent()) {
            GraphNode node = output.get();
            position = emitter.pinExpression(node, VfxNodes.POSITION_PIN, VfxExpression.vector3(position));
            velocity = emitter.pinExpression(node, VfxNodes.VELOCITY_PIN, VfxExpression.vector3(velocity));
            lifetime = emitter.pinExpression(node, VfxNodes.LIFETIME_PIN, VfxExpression.scalar(lifetime));
            color = emitter.pinExpression(node, VfxNodes.COLOR_PIN, VfxExpression.vector4(color));
            size = emitter.pinExpression(node, VfxNodes.SIZE_PIN, VfxExpression.scalar(size));
        }
        return spawnSource(position, velocity, lifetime, color, size);
    }

    private String spawnSource(String position, String velocity, String lifetime,
                               String color, String size) {
        return """
                #version 430 core
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
                    particles[slot].positionAge = vec4(emitterSpawnPosition(%s), 0.0);
                    particles[slot].velocityLifetime = vec4(%s, %s);
                    particles[slot].color = %s;
                    particles[slot].sizeRotation = vec4(%s, 0.0, 0.0, 0.0);
                    particles[slot].seedUser = vec4(particleSeed, 0.0, 0.0, 0.0);
                    particles[slot].userExtra = particles[slot].color;
                }
                """.formatted(preamble(), position, velocity, lifetime, color, size);
    }

    private String compileUpdate(GraphAsset asset) {
        VfxExpressionEmitter emitter = emitterFor(asset, VfxStage.PARTICLE_UPDATE);
        Optional<GraphNode> output = findOutput(asset, VfxNodes.OUTPUT_UPDATE);
        String velocity = "particle.velocityLifetime.xyz + vec3(0.0, -4.0, 0.0) * deltaTime";
        String color = "vec4(mix(particle.userExtra.rgb * 1.3 + vec3(0.25, 0.12, 0.02), particle.userExtra.rgb * 0.35, "
                + "smoothstep(0.0, 0.85, ageNormalized)), 1.0 - ageNormalized * ageNormalized)";
        String size = "particle.sizeRotation.x";
        String kill = "0.0";
        if (output.isPresent()) {
            GraphNode node = output.get();
            velocity = emitter.pinExpression(node, VfxNodes.VELOCITY_PIN, VfxExpression.vector3(velocity));
            color = emitter.pinExpression(node, VfxNodes.COLOR_PIN, VfxExpression.vector4(color));
            size = emitter.pinExpression(node, VfxNodes.SIZE_PIN, VfxExpression.scalar(size));
            kill = emitter.pinExpression(node, VfxNodes.KILL_PIN, VfxExpression.scalar(kill));
        }
        return updateSource(velocity, color, size, kill);
    }

    private String updateSource(String velocity, String color, String size, String kill) {
        return """
                #version 430 core
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
                    particles[slot].positionAge = vec4(particle.positionAge.xyz + velocity * deltaTime
                            + simulationSpaceOffset(), age);
                    particles[slot].velocityLifetime.xyz = velocity;
                    particles[slot].color = %s;
                    particles[slot].sizeRotation.x = %s;
                    uint drawIndex = atomicAdd(instanceCount, 1u);
                    aliveIndices[drawIndex] = slot;
                }
                """.formatted(preamble(), kill, velocity, color, size);
    }

    private String compileRender(GraphAsset asset) {
        VfxExpressionEmitter emitter = emitterFor(asset, VfxStage.RENDER);
        String softEdge = "1.0";
        String intensity = "4.0";
        Optional<GraphNode> output = findOutput(asset, VfxNodes.OUTPUT_RENDER);
        if (output.isPresent()) {
            softEdge = emitter.pinExpression(output.get(), VfxNodes.SOFT_EDGE_PIN,
                    VfxExpression.scalar(softEdge));
            intensity = emitter.pinExpression(output.get(), VfxNodes.INTENSITY_PIN,
                    VfxExpression.scalar(intensity));
        }
        return """
                    float distanceFromCenter = length(particleCorner);
                    float softEdge = %s;
                    float intensity = %s;
                    float falloff = smoothstep(1.0, 1.0 - clamp(softEdge, 0.05, 1.0), distanceFromCenter);
                    float core = smoothstep(0.45, 0.0, distanceFromCenter) * 0.6;
                    vec3 hdrColor = particleColor.rgb * intensity * (falloff + core);
                    fragmentColor = vec4(hdrColor * particleColor.a, 1.0);
                """.formatted(softEdge, intensity);
    }

    private VfxExpressionEmitter emitterFor(GraphAsset asset, VfxStage stage) {
        return new VfxExpressionEmitter(asset, stage, !shapeSource.isBlank(), !noiseSource.isBlank());
    }

    private String preamble() {
        StringBuilder builder = new StringBuilder(commonSource);
        appendLibrary(builder, shapeSource);
        appendLibrary(builder, noiseSource);
        return builder.append('\n').append(helperFunctions()).toString();
    }

    private static void appendLibrary(StringBuilder builder, String source) {
        if (!source.isBlank()) {
            builder.append('\n').append(source);
        }
    }

    private static String helperFunctions() {
        return """
                vec3 emitterSpawnPosition(vec3 emitterSpacePosition) {
                    return effect.emitterPositionDelta.xyz + emitterSpacePosition;
                }

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

                float safeDenominator(float value) {
                    return abs(value) < 1e-5 ? (value < 0.0 ? -1e-5 : 1e-5) : value;
                }

                float safeDivide(float numerator, float denominator) {
                    return numerator / safeDenominator(denominator);
                }

                vec3 safeDivide(vec3 numerator, vec3 denominator) {
                    return vec3(safeDivide(numerator.x, denominator.x),
                            safeDivide(numerator.y, denominator.y),
                            safeDivide(numerator.z, denominator.z));
                }

                vec4 safeDivide(vec4 numerator, vec4 denominator) {
                    return vec4(safeDivide(numerator.xyz, denominator.xyz),
                            safeDivide(numerator.w, denominator.w));
                }
                """;
    }

    private static Optional<GraphNode> findOutput(GraphAsset asset, String typeKey) {
        return asset.nodes().stream().filter(node -> node.typeKey().equals(typeKey)).findFirst();
    }
}
