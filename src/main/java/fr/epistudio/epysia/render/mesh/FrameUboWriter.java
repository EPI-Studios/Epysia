package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

final class FrameUboWriter {

    private static final int LIGHT_TYPE_DIRECTIONAL = 0;
    private static final int LIGHT_TYPE_POINT = 1;
    private static final int LIGHT_TYPE_SPOT = 2;
    private static final int CASCADE_MATRICES_OFFSET = 64;
    private static final int CASCADE_SPLITS_OFFSET = 320;
    private static final int CASCADE_TEXEL_SIZES_OFFSET = 336;
    private static final int AMBIENT_OFFSET = 352;

    private final ByteBuffer scratch = BufferUtils.createByteBuffer(MeshShaderBindings.FRAME_UBO_SIZE);
    private final Vector3f scratchLightDirection = new Vector3f();
    private final Vector3f scratchLightPosition = new Vector3f();
    private final Vector3f scratchCameraPosition = new Vector3f();
    private final Vector3f whiteAmbient = new Vector3f(1.0f, 1.0f, 1.0f);
    private final Matrix4f scratchIdentity = new Matrix4f();
    private RenderBackend backend;
    private BufferHandle handle;

    void initialize(RenderBackend backend) {
        this.backend = backend;
        ByteBuffer initial = BufferUtils.createByteBuffer(MeshShaderBindings.FRAME_UBO_SIZE);
        handle = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, initial));
    }

    BufferHandle handle() {
        return handle;
    }

    void write(Camera3D camera, Optional<DirectionalLight> primary, List<Light> lights,
               float timeSeconds, float ambientIntensity, CascadedShadowMaps shadows, float alpha) {
        scratch.clear();
        camera.viewProjection(alpha).get(0, scratch);
        writeCascades(shadows);
        writeAmbientAndCamera(camera, primary, timeSeconds, ambientIntensity, alpha);
        int shadowIndex = primary.isPresent() ? 0 : -1;
        scratch.putInt(lights.size()).putInt(shadowIndex).putInt(shadows.activeCascadeCount()).putInt(0);
        for (int i = 0; i < lights.size(); i++) {
            writeLight(lights.get(i));
        }
        for (int i = lights.size(); i < MeshShaderBindings.MAX_LIGHTS; i++) {
            writeBlankLight();
        }
        scratch.flip();
        backend.writeBuffer(handle, scratch, 0L);
    }

    private void writeCascades(CascadedShadowMaps shadows) {
        int activeCount = shadows.activeCascadeCount();
        for (int cascade = 0; cascade < MeshShaderBindings.MAX_CASCADES; cascade++) {
            Matrix4f matrix = cascade < activeCount ? shadows.cascadeMatrix(cascade) : scratchIdentity.identity();
            matrix.get(CASCADE_MATRICES_OFFSET + cascade * 64, scratch);
        }
        scratch.position(CASCADE_SPLITS_OFFSET);
        for (int cascade = 0; cascade < MeshShaderBindings.MAX_CASCADES; cascade++) {
            scratch.putFloat(cascade < activeCount ? shadows.cascadeSplit(cascade) : 0.0f);
        }
        for (int cascade = 0; cascade < MeshShaderBindings.MAX_CASCADES; cascade++) {
            scratch.putFloat(cascade < activeCount ? shadows.cascadeTexelSize(cascade) : 0.0f);
        }
    }

    private void writeAmbientAndCamera(Camera3D camera, Optional<DirectionalLight> primary,
                                       float timeSeconds, float ambientIntensity, float alpha) {
        Vector3f ambient = primary.isPresent() ? primary.get().ambient() : whiteAmbient;
        scratch.position(AMBIENT_OFFSET);
        scratch.putFloat(ambient.x).putFloat(ambient.y).putFloat(ambient.z).putFloat(ambientIntensity);
        camera.position(scratchCameraPosition, alpha);
        scratch.putFloat(scratchCameraPosition.x).putFloat(scratchCameraPosition.y)
                .putFloat(scratchCameraPosition.z).putFloat(timeSeconds);
    }

    private void writeLight(Light light) {
        if (light instanceof DirectionalLight directional) {
            directional.direction(scratchLightDirection);
            scratch.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(LIGHT_TYPE_DIRECTIONAL);
            scratch.putFloat(scratchLightDirection.x).putFloat(scratchLightDirection.y).putFloat(scratchLightDirection.z).putFloat(0.0f);
        } else if (light instanceof PointLight point) {
            point.position(scratchLightPosition);
            scratch.putFloat(scratchLightPosition.x).putFloat(scratchLightPosition.y).putFloat(scratchLightPosition.z).putFloat(LIGHT_TYPE_POINT);
            scratch.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(point.range());
        } else if (light instanceof SpotLight spot) {
            spot.position(scratchLightPosition);
            spot.direction(scratchLightDirection);
            scratch.putFloat(scratchLightPosition.x).putFloat(scratchLightPosition.y).putFloat(scratchLightPosition.z).putFloat(LIGHT_TYPE_SPOT);
            scratch.putFloat(scratchLightDirection.x).putFloat(scratchLightDirection.y).putFloat(scratchLightDirection.z).putFloat(spot.range());
        } else {
            writeBlankLight();
            return;
        }
        Vector3f color = light.color();
        scratch.putFloat(color.x).putFloat(color.y).putFloat(color.z).putFloat(light.intensity());
        if (light instanceof SpotLight spot) {
            scratch.putFloat(spot.innerConeCosine()).putFloat(spot.outerConeCosine()).putFloat(0.0f).putFloat(0.0f);
        } else {
            scratch.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        }
    }

    private void writeBlankLight() {
        for (int i = 0; i < MeshShaderBindings.LIGHT_BYTES / Float.BYTES; i++) {
            scratch.putFloat(0.0f);
        }
    }

    void shutdown() {
        if (handle != null && backend != null) {
            backend.destroy(handle);
            handle = null;
        }
    }
}
