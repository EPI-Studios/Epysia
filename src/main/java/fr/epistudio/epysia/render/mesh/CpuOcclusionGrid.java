package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureHandle;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

final class CpuOcclusionGrid {

    private static final int TARGET_WIDTH = 64;

    private final Matrix4f viewProjection = new Matrix4f();
    private final Vector4f scratchCorner = new Vector4f();

    private FloatBuffer readback = BufferUtils.createFloatBuffer(0);
    private float[] depths = new float[0];
    private int width;
    private int height;
    private int level;
    private boolean ready;

    void refresh(RenderBackend backend, DepthPyramid pyramid, Matrix4f cameraViewProjection) {
        ready = false;
        TextureHandle texture = pyramid.texture();
        if (texture == null) {
            return;
        }
        selectLevel(backend, texture, pyramid.levels());
        if (width <= 0 || height <= 0) {
            return;
        }
        ensureCapacity();
        backend.readTextureLevel(texture, level, readback);
        readback.get(depths, 0, width * height);
        readback.clear();
        viewProjection.set(cameraViewProjection);
        ready = true;
    }

    private void selectLevel(RenderBackend backend, TextureHandle texture, int levels) {
        int baseWidth = backend.textureWidth(texture);
        int baseHeight = backend.textureHeight(texture);
        level = 0;
        while (level < levels - 1 && (baseWidth >> level) > TARGET_WIDTH) {
            level++;
        }
        width = Math.max(1, baseWidth >> level);
        height = Math.max(1, baseHeight >> level);
    }

    private void ensureCapacity() {
        int texels = width * height;
        if (depths.length < texels) {
            depths = new float[texels];
            readback = BufferUtils.createFloatBuffer(texels);
        }
    }

    boolean isReady() {
        return ready;
    }

    boolean isOccluded(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (!ready) {
            return false;
        }
        float screenMinX = Float.POSITIVE_INFINITY;
        float screenMinY = Float.POSITIVE_INFINITY;
        float screenMaxX = Float.NEGATIVE_INFINITY;
        float screenMaxY = Float.NEGATIVE_INFINITY;
        float nearest = Float.POSITIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            scratchCorner.set((corner & 1) == 0 ? minX : maxX, (corner & 2) == 0 ? minY : maxY,
                    (corner & 4) == 0 ? minZ : maxZ, 1.0f);
            viewProjection.transform(scratchCorner);
            if (scratchCorner.w <= 0.0f) {
                return false;
            }
            float inverseW = 1.0f / scratchCorner.w;
            screenMinX = Math.min(screenMinX, scratchCorner.x * inverseW);
            screenMaxX = Math.max(screenMaxX, scratchCorner.x * inverseW);
            screenMinY = Math.min(screenMinY, scratchCorner.y * inverseW);
            screenMaxY = Math.max(screenMaxY, scratchCorner.y * inverseW);
            nearest = Math.min(nearest, scratchCorner.z * inverseW);
        }
        return occludedBy(screenMinX, screenMinY, screenMaxX, screenMaxY, nearest * 0.5f + 0.5f);
    }

    private boolean occludedBy(float screenMinX, float screenMinY, float screenMaxX, float screenMaxY,
                               float nearest) {
        int fromX = texelOf(screenMinX, width);
        int toX = texelOf(screenMaxX, width);
        int fromY = texelOf(screenMinY, height);
        int toY = texelOf(screenMaxY, height);
        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                float farthest = depths[y * width + x];
                if (farthest <= 0.0f || nearest <= farthest) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int texelOf(float normalisedDeviceCoordinate, int extent) {
        int texel = (int) ((normalisedDeviceCoordinate * 0.5f + 0.5f) * extent);
        return Math.max(0, Math.min(extent - 1, texel));
    }
}
