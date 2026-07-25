package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

final class LightStorage {

    public static final int MAX_LIGHTS = 1024;

    private static final float LIGHT_TYPE_DIRECTIONAL = 0.0f;
    private static final float LIGHT_TYPE_POINT = 1.0f;
    private static final float LIGHT_TYPE_SPOT = 2.0f;
    private static final int BYTE_SIZE = MAX_LIGHTS * MeshShaderBindings.LIGHT_BYTES;

    private final Vector3f scratchDirection = new Vector3f();
    private final Vector3f scratchPosition = new Vector3f();
    private final ByteBuffer scratch = BufferUtils.createByteBuffer(BYTE_SIZE);
    private RenderBackend backend;
    private BufferHandle handle;

    void initialize(RenderBackend backend) {
        this.backend = backend;
        handle = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, BufferUtils.createByteBuffer(BYTE_SIZE)));
    }

    BufferHandle handle() {
        return handle;
    }

    long byteSize() {
        return BYTE_SIZE;
    }

    void update(List<Light> lights, SpotShadowAtlas spotShadows, PointShadowAtlas pointShadows) {
        int count = Math.min(lights.size(), MAX_LIGHTS);
        scratch.clear();
        for (int i = 0; i < count; i++) {
            writeLight(lights.get(i), spotShadows, pointShadows);
        }
        scratch.flip();
        backend.writeBuffer(handle, scratch, 0L);
    }

    private void writeLight(Light light, SpotShadowAtlas spotShadows, PointShadowAtlas pointShadows) {
        if (light instanceof DirectionalLight directional) {
            directional.direction(scratchDirection);
            scratch.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(LIGHT_TYPE_DIRECTIONAL);
            putVector(scratchDirection, 0.0f);
        } else if (light instanceof PointLight point) {
            point.position(scratchPosition);
            putVector(scratchPosition, LIGHT_TYPE_POINT);
            float pointLayer = point.castShadows() ? pointShadows.indexFor(point) : -1.0f;
            scratch.putFloat(pointLayer).putFloat(0.0f).putFloat(0.0f).putFloat(point.range());
        } else if (light instanceof SpotLight spot) {
            spot.position(scratchPosition);
            spot.direction(scratchDirection);
            putVector(scratchPosition, LIGHT_TYPE_SPOT);
            putVector(scratchDirection, spot.range());
        } else {
            return;
        }
        Vector3f color = light.color();
        scratch.putFloat(color.x).putFloat(color.y).putFloat(color.z).putFloat(light.intensity());
        writeCones(light, spotShadows);
    }

    private void putVector(Vector3f value, float w) {
        scratch.putFloat(value.x).putFloat(value.y).putFloat(value.z).putFloat(w);
    }

    private void writeCones(Light light, SpotShadowAtlas spotShadows) {
        if (light instanceof SpotLight spot) {
            float layer = light.castShadows() ? spotShadows.layerFor(spot) : -1.0f;
            scratch.putFloat(spot.innerConeCosine()).putFloat(spot.outerConeCosine()).putFloat(layer)
                    .putFloat(light.sourceRadius());
        } else {
            scratch.putFloat(0.0f).putFloat(0.0f).putFloat(-1.0f).putFloat(light.sourceRadius());
        }
    }

    void shutdown() {
        if (handle != null && backend != null) {
            backend.destroy(handle);
            handle = null;
        }
    }
}
