package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.assets.epyprobes.BakedProbes;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.Light;
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

    private static final int CASCADE_MATRICES_OFFSET = 64;
    private static final int CASCADE_SPLITS_OFFSET = 320;
    private static final int CASCADE_TEXEL_SIZES_OFFSET = 336;
    private static final int AMBIENT_OFFSET = 352;

    private final ByteBuffer scratch = BufferUtils.createByteBuffer(MeshShaderBindings.FRAME_UBO_SIZE);
    private final Vector3f scratchCameraPosition = new Vector3f();
    private final Vector3f scratchProbeVector = new Vector3f();
    private final Matrix4f scratchInverseViewProjection = new Matrix4f();
    private final Vector3f whiteAmbient = new Vector3f(1.0f, 1.0f, 1.0f);
    private final Matrix4f scratchIdentity = new Matrix4f();
    private RenderBackend backend;
    private BufferHandle handle;

    void initialize(RenderBackend backend) {
        this.backend = backend;
        ByteBuffer initial = BufferUtils.createByteBuffer(MeshShaderBindings.FRAME_UBO_SIZE);
        handle = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, initial, true));
    }

    public BufferHandle handle() {
        return handle;
    }

    private static int directionalCountOf(List<Light> lights) {
        int count = 0;
        for (int index = 0; index < lights.size(); index++) {
            if (!(lights.get(index) instanceof DirectionalLight)) {
                return count;
            }
            count++;
        }
        return count;
    }

    void write(Camera3D camera, Optional<DirectionalLight> primary, List<Light> lights,
               float timeSeconds, float ambientIntensity, CascadedShadowMaps shadows,
               SpotShadowAtlas spotShadows, PointShadowAtlas pointShadows,
               ClusterLightCuller clusters, boolean clusteringEnabled, float alpha,
               Optional<BakedProbes> probes) {
        scratch.clear();
        camera.viewProjection(alpha).get(0, scratch);
        writeCascades(shadows);
        writeAmbientAndCamera(camera, primary, timeSeconds, ambientIntensity, alpha);
        int shadowIndex = primary.isPresent() ? 0 : -1;
        scratch.putInt(lights.size()).putInt(shadowIndex).putInt(shadows.activeCascadeCount())
                .putInt(directionalCountOf(lights));
        writeSpotShadows(spotShadows);
        writePointShadows(pointShadows);
        writeClusterParams(camera, clusters, clusteringEnabled);
        writeProbeGrid(probes);
        writeInverseViewProjection(camera, alpha);
        scratch.position(MeshShaderBindings.FRAME_UBO_SIZE);
        scratch.flip();
        backend.writeBuffer(handle, scratch, 0L);
    }

    private void writeInverseViewProjection(Camera3D camera, float alpha) {
        scratch.position(MeshShaderBindings.INVERSE_VIEW_PROJECTION_OFFSET);
        camera.viewProjection(alpha).invert(scratchInverseViewProjection).get(
                MeshShaderBindings.INVERSE_VIEW_PROJECTION_OFFSET, scratch);
    }

    private void writeProbeGrid(Optional<BakedProbes> probes) {
        scratch.position(MeshShaderBindings.PROBE_GRID_OFFSET);
        if (probes.isEmpty()) {
            for (int component = 0; component < 12; component++) {
                scratch.putFloat(0.0f);
            }
            return;
        }
        BakedProbes baked = probes.get();
        baked.gridOrigin(scratchProbeVector);
        scratch.putFloat(scratchProbeVector.x).putFloat(scratchProbeVector.y)
                .putFloat(scratchProbeVector.z).putFloat(0.0f);
        baked.gridSpacing(scratchProbeVector);
        scratch.putFloat(scratchProbeVector.x).putFloat(scratchProbeVector.y)
                .putFloat(scratchProbeVector.z).putFloat(0.0f);
        scratch.putInt(baked.resolutionX()).putInt(baked.resolutionY())
                .putInt(baked.resolutionZ()).putInt(0);
    }

    private void writeClusterParams(Camera3D camera, ClusterLightCuller clusters, boolean enabled) {
        scratch.position(MeshShaderBindings.CLUSTER_GRID_OFFSET);
        scratch.putInt(MeshShaderBindings.CLUSTER_X).putInt(MeshShaderBindings.CLUSTER_Y)
                .putInt(MeshShaderBindings.CLUSTER_Z).putInt(enabled ? 1 : 0);
        scratch.putFloat(camera.nearPlane()).putFloat(camera.farPlane()).putFloat(0.0f).putFloat(0.0f);
        scratch.putFloat(clusters.sliceScale()).putFloat(clusters.sliceBias())
                .putFloat(MeshShaderBindings.MAX_LIGHTS_PER_CLUSTER).putFloat(0.0f);
    }

    private void writeSpotShadows(SpotShadowAtlas spotShadows) {
        int count = spotShadows.activeCount();
        scratch.putInt(count).putInt(0).putInt(0).putInt(0);
        for (int layer = 0; layer < MeshShaderBindings.MAX_SHADOW_SPOTS; layer++) {
            Matrix4f matrix = layer < count ? spotShadows.matrix(layer) : scratchIdentity.identity();
            matrix.get(MeshShaderBindings.SPOT_SHADOW_MATRICES_OFFSET + layer * 64, scratch);
        }
    }

    private void writePointShadows(PointShadowAtlas pointShadows) {
        int totalLayers = MeshShaderBindings.MAX_SHADOW_POINTS * MeshShaderBindings.POINT_SHADOW_FACES;
        int activeLayers = pointShadows.activeCount() * MeshShaderBindings.POINT_SHADOW_FACES;
        scratch.position(MeshShaderBindings.POINT_SHADOW_COUNT_OFFSET);
        scratch.putInt(pointShadows.activeCount()).putInt(0).putInt(0).putInt(0);
        for (int layer = 0; layer < totalLayers; layer++) {
            Matrix4f matrix = layer < activeLayers ? pointShadows.matrix(layer) : scratchIdentity.identity();
            matrix.get(MeshShaderBindings.POINT_SHADOW_MATRICES_OFFSET + layer * 64, scratch);
        }
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

    void shutdown() {
        if (handle != null && backend != null) {
            backend.destroy(handle);
            handle = null;
        }
    }
}
