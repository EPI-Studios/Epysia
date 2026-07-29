package fr.epistudio.epysia.render.sprite;

import fr.epistudio.epysia.components.GlobalLight2D;
import fr.epistudio.epysia.components.Light2D;
import fr.epistudio.epysia.components.PointLight2D;
import fr.epistudio.epysia.components.SpotLight2D;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public final class Light2dStorage {

    public static final int MAX_LIGHTS = 256;
    public static final int LIGHT_BYTES = 80;

    private static final int TYPE_POINT = 0;
    private static final int TYPE_SPOT = 1;
    private static final int TYPE_GLOBAL = 2;
    private static final int HEADER_BYTES = 16;
    private static final int BYTE_SIZE = HEADER_BYTES + MAX_LIGHTS * LIGHT_BYTES;

    private final ByteBuffer scratch = BufferUtils.createByteBuffer(BYTE_SIZE);
    private final Vector3f scratchDirection = new Vector3f();
    private RenderBackend backend;
    private BufferHandle handle;
    private int lightCount;

    public void initialize(RenderBackend backend) {
        this.backend = backend;
        handle = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(BYTE_SIZE)));
    }

    public BufferHandle handle() {
        return handle;
    }

    public long byteSize() {
        return BYTE_SIZE;
    }

    public int lightCount() {
        return lightCount;
    }

    public void update(Scene scene) {
        scratch.clear();
        scratch.putInt(0).putInt(0).putInt(0).putInt(0);
        lightCount = 0;
        for (GlobalLight2D light : scene.componentsOf(GlobalLight2D.class)) {
            writeGlobal(light);
        }
        for (PointLight2D light : scene.componentsOf(PointLight2D.class)) {
            writePoint(light);
        }
        for (SpotLight2D light : scene.componentsOf(SpotLight2D.class)) {
            writeSpot(light);
        }
        scratch.putInt(0, lightCount);
        scratch.flip();
        backend.writeBuffer(handle, scratch, 0L);
    }

    private void writeGlobal(GlobalLight2D light) {
        if (!accepts(light)) {
            return;
        }
        light.direction(scratchDirection);
        putVector4(0.0f, 0.0f, 0.0f, 0.0f);
        putColor(light);
        putVector4(scratchDirection.x, scratchDirection.y, scratchDirection.z, 0.0f);
        putVector4(light.ambient(), 0.0f, 0.0f, 0.0f);
        putTypeAndLayers(TYPE_GLOBAL, light.lightLayers());
    }

    private void writePoint(PointLight2D light) {
        Vector2f position = light.position().orElse(null);
        if (!accepts(light) || position == null) {
            return;
        }
        putVector4(position.x, position.y, light.height(), light.range());
        putColor(light);
        putVector4(0.0f, 0.0f, 0.0f, 0.0f);
        putVector4(light.innerRadius(), 0.0f, 0.0f, 0.0f);
        putTypeAndLayers(TYPE_POINT, light.lightLayers());
    }

    private void writeSpot(SpotLight2D light) {
        Vector2f position = light.position().orElse(null);
        Vector2f direction = light.direction().orElse(null);
        if (!accepts(light) || position == null || direction == null) {
            return;
        }
        putVector4(position.x, position.y, light.height(), light.range());
        putColor(light);
        putVector4(direction.x, direction.y,
                coneCosine(light.outerAngleDegrees()), coneCosine(light.innerAngleDegrees()));
        putVector4(0.0f, 0.0f, 0.0f, 0.0f);
        putTypeAndLayers(TYPE_SPOT, light.lightLayers());
    }

    private static float coneCosine(float angleDegrees) {
        return (float) Math.cos(Math.toRadians(Math.max(0.0f, angleDegrees) * 0.5));
    }

    private boolean accepts(Light2D light) {
        return light.enabled() && lightCount < MAX_LIGHTS;
    }

    private void putColor(Light2D light) {
        Vector3f color = light.color();
        putVector4(color.x, color.y, color.z, light.intensity());
    }

    private void putVector4(float x, float y, float z, float w) {
        scratch.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }

    private void putTypeAndLayers(int type, int layers) {
        scratch.putInt(type).putInt(layers).putInt(0).putInt(0);
        lightCount++;
    }

    public void shutdown() {
        if (handle != null && backend != null) {
            backend.destroy(handle);
            handle = null;
        }
    }
}
