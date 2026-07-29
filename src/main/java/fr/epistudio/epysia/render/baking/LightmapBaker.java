package fr.epistudio.epysia.render.baking;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.texture.Texture2D;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImageWrite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LightmapBaker implements LightBaker {

    private static final int ATLAS_SIZE = Integer.getInteger("epysia.lightmap.atlasSize", 512);
    private static final float SHADOW_BIAS = 0.02f;
    private static final float MAXIMUM_SHADOW_DISTANCE = 10000.0f;
    private static final String OUTPUT_NAME = "lightmap.png";
    public static final float RGBM_RANGE = 8.0f;

    private Optional<BakeContext> context = Optional.empty();
    private Optional<Path> writtenFile = Optional.empty();

    @Override
    public LightBakeOutput output() {
        return LightBakeOutput.LIGHTMAP;
    }

    @Override
    public void start(BakeRequest request) {
        cancel();
        writtenFile = Optional.empty();
        context = Optional.of(new BakeContext(request));
    }

    @Override
    public BakeProgress step() {
        if (context.isEmpty()) {
            return BakeProgress.idle();
        }
        BakeContext running = context.get();
        running.bakeNextTarget();
        if (!running.completed()) {
            return BakeProgress.running(running.completedTargets(), running.totalTargets());
        }
        writtenFile = running.publish();
        context = Optional.empty();
        return BakeProgress.done(running.totalTargets());
    }

    @Override
    public void cancel() {
        context = Optional.empty();
    }

    @Override
    public Optional<Path> result() {
        return writtenFile;
    }

    private record Target(MeshRenderer renderer, LightmapGeometry geometry, int tileX, int tileY, int tileSize) {
    }

    private record SunLight(Vector3f toLight, Vector3f colour, Vector3f ambient) {
    }

    private static final class BakeContext {

        private final RenderBackend backend;
        private final Path outputDirectory;
        private final List<Target> targets = new ArrayList<>();
        private final List<SunLight> suns = new ArrayList<>();
        private final LightmapOccluders occluders = new LightmapOccluders();
        private final float[] atlas = new float[ATLAS_SIZE * ATLAS_SIZE * 3];
        private int nextTarget;

        private BakeContext(BakeRequest request) {
            EpysiaEngine engine = request.engine();
            this.backend = engine.renderBackend();
            this.outputDirectory = request.outputDirectory();
            collectTargets(engine.scene());
            collectSuns(engine.scene());
            targets.forEach(target -> occluders.add(target.geometry()));
        }

        private void collectTargets(Scene scene) {
            List<MeshRenderer> candidates = new ArrayList<>();
            for (MeshRenderer renderer : scene.componentsOf(MeshRenderer.class)) {
                UploadedMesh mesh = renderer.meshOrNull();
                if (mesh != null && mesh.lightmapped() && renderer.ownerOrNull() != null) {
                    candidates.add(renderer);
                }
            }
            int perRow = Math.max(1, (int) Math.ceil(Math.sqrt(candidates.size())));
            int tileSize = Math.max(1, ATLAS_SIZE / perRow);
            for (int index = 0; index < candidates.size(); index++) {
                appendTarget(candidates.get(index), index % perRow, index / perRow, tileSize);
            }
        }

        private void appendTarget(MeshRenderer renderer, int tileX, int tileY, int tileSize) {
            GameObject owner = renderer.ownerOrNull();
            Transform3D transform = owner.transform3DOrNull();
            if (transform == null) {
                return;
            }
            Matrix4f world = new Matrix4f(transform.worldMatrix());
            LightmapGeometry.read(backend, renderer.meshOrNull(), world)
                    .ifPresent(geometry -> targets.add(new Target(renderer, geometry, tileX, tileY, tileSize)));
        }

        private void collectSuns(Scene scene) {
            for (DirectionalLight light : scene.componentsOf(DirectionalLight.class)) {
                Vector3f toLight = light.direction(new Vector3f()).negate().normalize();
                Vector3f colour = new Vector3f(light.color()).mul(light.intensity());
                suns.add(new SunLight(toLight, colour, new Vector3f(light.ambient())));
            }
        }

        private boolean completed() {
            return nextTarget >= targets.size();
        }

        private int completedTargets() {
            return nextTarget;
        }

        private int totalTargets() {
            return targets.size();
        }

        private void bakeNextTarget() {
            if (completed()) {
                return;
            }
            Target target = targets.get(nextTarget);
            nextTarget++;
            int[] indices = target.geometry().indices();
            for (int index = 0; index + 2 < indices.length; index += 3) {
                rasterizeTriangle(target, index);
            }
            applyTileToMaterials(target);
        }

        private void rasterizeTriangle(Target target, int firstIndex) {
            LightmapGeometry geometry = target.geometry();
            int[] indices = geometry.indices();
            int cornerA = indices[firstIndex];
            int cornerB = indices[firstIndex + 1];
            int cornerC = indices[firstIndex + 2];
            float[] uvs = geometry.lightmapUvs();
            float minimumU = Math.min(uvs[cornerA * 2], Math.min(uvs[cornerB * 2], uvs[cornerC * 2]));
            float maximumU = Math.max(uvs[cornerA * 2], Math.max(uvs[cornerB * 2], uvs[cornerC * 2]));
            float minimumV = Math.min(uvs[cornerA * 2 + 1], Math.min(uvs[cornerB * 2 + 1], uvs[cornerC * 2 + 1]));
            float maximumV = Math.max(uvs[cornerA * 2 + 1], Math.max(uvs[cornerB * 2 + 1], uvs[cornerC * 2 + 1]));
            shadeTexelRange(target, cornerA, cornerB, cornerC,
                    texelBound(minimumU, target.tileSize()), texelBound(maximumU, target.tileSize()),
                    texelBound(minimumV, target.tileSize()), texelBound(maximumV, target.tileSize()));
        }

        private static int texelBound(float coordinate, int tileSize) {
            return Math.clamp(Math.round(coordinate * tileSize), 0, tileSize - 1);
        }

        private void shadeTexelRange(Target target, int cornerA, int cornerB, int cornerC,
                                     int fromX, int toX, int fromY, int toY) {
            for (int texelY = fromY; texelY <= toY; texelY++) {
                for (int texelX = fromX; texelX <= toX; texelX++) {
                    shadeTexel(target, cornerA, cornerB, cornerC, texelX, texelY);
                }
            }
        }

        private void shadeTexel(Target target, int cornerA, int cornerB, int cornerC, int texelX, int texelY) {
            float[] uvs = target.geometry().lightmapUvs();
            float sampleU = (texelX + 0.5f) / target.tileSize();
            float sampleV = (texelY + 0.5f) / target.tileSize();
            Vector3f weights = barycentric(uvs, cornerA, cornerB, cornerC, sampleU, sampleV);
            if (weights == null) {
                return;
            }
            Vector3f position = interpolate(target.geometry().positions(), cornerA, cornerB, cornerC, weights);
            Vector3f normal = interpolate(target.geometry().normals(), cornerA, cornerB, cornerC, weights).normalize();
            writeTexel(target, texelX, texelY, shade(position, normal));
        }

        private static Vector3f barycentric(float[] uvs, int cornerA, int cornerB, int cornerC,
                                            float sampleU, float sampleV) {
            float ax = uvs[cornerA * 2];
            float ay = uvs[cornerA * 2 + 1];
            float v0x = uvs[cornerB * 2] - ax;
            float v0y = uvs[cornerB * 2 + 1] - ay;
            float v1x = uvs[cornerC * 2] - ax;
            float v1y = uvs[cornerC * 2 + 1] - ay;
            float determinant = v0x * v1y - v1x * v0y;
            if (Math.abs(determinant) < 1.0e-12f) {
                return null;
            }
            float weightB = ((sampleU - ax) * v1y - v1x * (sampleV - ay)) / determinant;
            float weightC = (v0x * (sampleV - ay) - (sampleU - ax) * v0y) / determinant;
            if (weightB < -0.001f || weightC < -0.001f || weightB + weightC > 1.001f) {
                return null;
            }
            return new Vector3f(1.0f - weightB - weightC, weightB, weightC);
        }

        private static Vector3f interpolate(float[] source, int cornerA, int cornerB, int cornerC, Vector3f weights) {
            return new Vector3f(
                    source[cornerA * 3] * weights.x + source[cornerB * 3] * weights.y + source[cornerC * 3] * weights.z,
                    source[cornerA * 3 + 1] * weights.x + source[cornerB * 3 + 1] * weights.y
                            + source[cornerC * 3 + 1] * weights.z,
                    source[cornerA * 3 + 2] * weights.x + source[cornerB * 3 + 2] * weights.y
                            + source[cornerC * 3 + 2] * weights.z);
        }

        private Vector3f shade(Vector3f position, Vector3f normal) {
            Vector3f accumulated = new Vector3f();
            Vector3f origin = new Vector3f(normal).mul(SHADOW_BIAS).add(position);
            for (SunLight sun : suns) {
                accumulated.add(sun.ambient());
                float lambert = Math.max(normal.dot(sun.toLight()), 0.0f);
                if (lambert <= 0.0f || occluders.occluded(origin, sun.toLight(), MAXIMUM_SHADOW_DISTANCE)) {
                    continue;
                }
                accumulated.add(new Vector3f(sun.colour()).mul(lambert));
            }
            return accumulated;
        }

        private void writeTexel(Target target, int texelX, int texelY, Vector3f colour) {
            int atlasX = target.tileX() * target.tileSize() + texelX;
            int atlasY = target.tileY() * target.tileSize() + texelY;
            if (atlasX >= ATLAS_SIZE || atlasY >= ATLAS_SIZE) {
                return;
            }
            int base = (atlasY * ATLAS_SIZE + atlasX) * 3;
            atlas[base] = colour.x;
            atlas[base + 1] = colour.y;
            atlas[base + 2] = colour.z;
        }

        private void applyTileToMaterials(Target target) {
            float scale = (float) target.tileSize() / ATLAS_SIZE;
            float offsetU = (float) (target.tileX() * target.tileSize()) / ATLAS_SIZE;
            float offsetV = (float) (target.tileY() * target.tileSize()) / ATLAS_SIZE;
            for (Material material : target.renderer().materials()) {
                if (material instanceof LitMaterial lit) {
                    lit.setLightmapScaleOffset(scale, scale, offsetU, offsetV);
                }
            }
        }

        private Optional<Path> publish() {
            ByteBuffer pixels = encodeAtlas();
            assignTexture(Texture2D.fromPixels(backend, ATLAS_SIZE, ATLAS_SIZE, pixels));
            return writePng(pixels);
        }

        private ByteBuffer encodeAtlas() {
            ByteBuffer pixels = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE * 4);
            for (int texel = 0; texel < ATLAS_SIZE * ATLAS_SIZE; texel++) {
                encodeRgbm(pixels, atlas[texel * 3], atlas[texel * 3 + 1], atlas[texel * 3 + 2]);
            }
            pixels.flip();
            return pixels;
        }

        private static void encodeRgbm(ByteBuffer pixels, float red, float green, float blue) {
            float brightest = Math.max(red, Math.max(green, blue)) / RGBM_RANGE;
            float multiplier = Math.clamp(brightest, 1.0f / 255.0f, 1.0f);
            int quantised = Math.clamp((int) Math.ceil(multiplier * 255.0f), 1, 255);
            float scale = RGBM_RANGE * quantised / 255.0f;
            pixels.put(toByte(red / scale));
            pixels.put(toByte(green / scale));
            pixels.put(toByte(blue / scale));
            pixels.put((byte) quantised);
        }

        private static byte toByte(float value) {
            return (byte) Math.clamp(Math.round(value * 255.0f), 0, 255);
        }

        private void assignTexture(fr.epistudio.epysia.render.backend.TextureHandle texture) {
            for (Target target : targets) {
                for (Material material : target.renderer().materials()) {
                    if (material instanceof LitMaterial lit) {
                        lit.setLightmap(texture).setLightmapRgbmRange(RGBM_RANGE);
                    }
                }
            }
        }

        private Optional<Path> writePng(ByteBuffer pixels) {
            Path file = outputDirectory.resolve(OUTPUT_NAME);
            try {
                Files.createDirectories(outputDirectory);
            } catch (IOException failure) {
                throw new EpysiaException("Cannot create lightmap output directory: " + failure.getMessage());
            }
            pixels.rewind();
            if (!STBImageWrite.stbi_write_png(file.toString(), ATLAS_SIZE, ATLAS_SIZE, 4, pixels, ATLAS_SIZE * 4)) {
                throw new EpysiaException("Failed to write lightmap atlas to " + file);
            }
            return Optional.of(file);
        }
    }
}
