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
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

final class ClusterLightCuller {

    private static final int GX = MeshShaderBindings.CLUSTER_X;
    private static final int GY = MeshShaderBindings.CLUSTER_Y;
    private static final int GZ = MeshShaderBindings.CLUSTER_Z;
    private static final int CLUSTERS = MeshShaderBindings.CLUSTER_COUNT;
    private static final int MAX_PER = MeshShaderBindings.MAX_LIGHTS_PER_CLUSTER;

    private final Vector3f[] clusterMin = createVectors();
    private final Vector3f[] clusterMax = createVectors();
    private final int[] counts = new int[CLUSTERS];
    private final int[] indices = new int[CLUSTERS * MAX_PER];
    private final ByteBuffer countScratch = BufferUtils.createByteBuffer(CLUSTERS * Integer.BYTES);
    private final ByteBuffer indexScratch = BufferUtils.createByteBuffer(CLUSTERS * MAX_PER * Integer.BYTES);
    private final Vector3f scratchViewCenter = new Vector3f();
    private final Vector3f scratchWorld = new Vector3f();
    private final Matrix4f scratchView = new Matrix4f();

    private final IntBuffer countView = countScratch.asIntBuffer();
    private final IntBuffer indexView = indexScratch.asIntBuffer();
    private int highestTouchedCluster = -1;
    private int previousHighestCluster = CLUSTERS - 1;
    private float sliceScale;
    private float sliceBias;
    private float boundsProjectionX = Float.NaN;
    private float boundsProjectionY = Float.NaN;
    private float boundsNearPlane = Float.NaN;
    private float boundsFarPlane = Float.NaN;
    private RenderBackend backend;
    private BufferHandle countBuffer;
    private BufferHandle indexBuffer;

    void initialize(RenderBackend backend) {
        this.backend = backend;
        countBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(CLUSTERS * Integer.BYTES)));
        indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(CLUSTERS * MAX_PER * Integer.BYTES)));
    }

    BufferHandle countBuffer() {
        return countBuffer;
    }

    BufferHandle indexBuffer() {
        return indexBuffer;
    }

    long countByteSize() {
        return (long) CLUSTERS * Integer.BYTES;
    }

    long indexByteSize() {
        return (long) CLUSTERS * MAX_PER * Integer.BYTES;
    }

    float sliceScale() {
        return sliceScale;
    }

    float sliceBias() {
        return sliceBias;
    }

    void cull(Camera3D camera, List<Light> lights, float alpha) {
        float zNear = camera.nearPlane();
        float zFar = camera.farPlane();
        Matrix4f projection = camera.projection();
        sliceScale = (float) (GZ / Math.log(zFar / zNear));
        sliceBias = (float) (GZ * Math.log(zNear) / Math.log(zFar / zNear));
        computeClusterBounds(projection.m00(), projection.m11(), zNear, zFar);
        scratchView.set(camera.view(alpha));
        Arrays.fill(counts, 0, previousHighestCluster + 1, 0);
        previousHighestCluster = highestTouchedCluster;
        highestTouchedCluster = -1;
        int count = Math.min(lights.size(), LightStorage.MAX_LIGHTS);
        for (int i = 0; i < count; i++) {
            assignLight(i, lights.get(i));
        }
        upload();
    }

    private void computeClusterBounds(float p00, float p11, float zNear, float zFar) {
        if (boundsProjectionX == p00 && boundsProjectionY == p11
                && boundsNearPlane == zNear && boundsFarPlane == zFar) {
            return;
        }
        boundsProjectionX = p00;
        boundsProjectionY = p11;
        boundsNearPlane = zNear;
        boundsFarPlane = zFar;
        double ratio = zFar / zNear;
        for (int z = 0; z < GZ; z++) {
            float depthNear = (float) (zNear * Math.pow(ratio, (double) z / GZ));
            float depthFar = (float) (zNear * Math.pow(ratio, (double) (z + 1) / GZ));
            for (int y = 0; y < GY; y++) {
                for (int x = 0; x < GX; x++) {
                    computeAabb(x + y * GX + z * GX * GY, x, y, depthNear, depthFar, p00, p11);
                }
            }
        }
    }

    private void computeAabb(int cluster, int x, int y, float depthNear, float depthFar, float p00, float p11) {
        float xMin = 2.0f * x / GX - 1.0f;
        float xMax = 2.0f * (x + 1) / GX - 1.0f;
        float yMin = 2.0f * y / GY - 1.0f;
        float yMax = 2.0f * (y + 1) / GY - 1.0f;
        Vector3f min = clusterMin[cluster].set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        Vector3f max = clusterMax[cluster].set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        accumulateSlice(min, max, xMin, xMax, yMin, yMax, depthNear, p00, p11);
        accumulateSlice(min, max, xMin, xMax, yMin, yMax, depthFar, p00, p11);
    }

    private static void accumulateSlice(Vector3f min, Vector3f max, float xMin, float xMax,
            float yMin, float yMax, float depth, float p00, float p11) {
        accumulate(min, max, xMin * depth / p00, yMin * depth / p11, -depth);
        accumulate(min, max, xMax * depth / p00, yMin * depth / p11, -depth);
        accumulate(min, max, xMin * depth / p00, yMax * depth / p11, -depth);
        accumulate(min, max, xMax * depth / p00, yMax * depth / p11, -depth);
    }

    private static void accumulate(Vector3f min, Vector3f max, float x, float y, float z) {
        min.set(Math.min(min.x, x), Math.min(min.y, y), Math.min(min.z, z));
        max.set(Math.max(max.x, x), Math.max(max.y, y), Math.max(max.z, z));
    }

    private void assignLight(int lightIndex, Light light) {
        if (light instanceof DirectionalLight) {
            return;
        }
        float radius = lightRadius(light);
        if (radius <= 0.0f) {
            return;
        }
        lightViewPosition(light);
        int firstSlice = sliceOfDepth(-scratchViewCenter.z - radius);
        int lastSlice = sliceOfDepth(-scratchViewCenter.z + radius);
        if (lastSlice < 0 || firstSlice >= GZ) {
            return;
        }
        int sliceStart = Math.max(firstSlice, 0) * GX * GY;
        int sliceEnd = (Math.min(lastSlice, GZ - 1) + 1) * GX * GY;
        for (int cluster = sliceStart; cluster < sliceEnd; cluster++) {
            if (sphereIntersectsAabb(radius, clusterMin[cluster], clusterMax[cluster])) {
                addToCluster(cluster, lightIndex);
            }
        }
    }

    private int sliceOfDepth(float viewDepth) {
        if (viewDepth <= 0.0f) {
            return -1;
        }
        return (int) Math.floor(Math.log(viewDepth) * sliceScale - sliceBias);
    }

    private void lightViewPosition(Light light) {
        if (light instanceof PointLight point) {
            point.position(scratchWorld);
        } else if (light instanceof SpotLight spot) {
            spot.position(scratchWorld);
        }
        scratchView.transformPosition(scratchWorld, scratchViewCenter);
    }

    private static float lightRadius(Light light) {
        if (light instanceof PointLight point) {
            return point.range();
        }
        if (light instanceof SpotLight spot) {
            return spot.range();
        }
        return 0.0f;
    }

    private boolean sphereIntersectsAabb(float radius, Vector3f min, Vector3f max) {
        float closestX = Math.max(min.x, Math.min(scratchViewCenter.x, max.x));
        float closestY = Math.max(min.y, Math.min(scratchViewCenter.y, max.y));
        float closestZ = Math.max(min.z, Math.min(scratchViewCenter.z, max.z));
        float dx = scratchViewCenter.x - closestX;
        float dy = scratchViewCenter.y - closestY;
        float dz = scratchViewCenter.z - closestZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private void addToCluster(int cluster, int lightIndex) {
        if (counts[cluster] < MAX_PER) {
            indices[cluster * MAX_PER + counts[cluster]] = lightIndex;
            counts[cluster]++;
            highestTouchedCluster = Math.max(highestTouchedCluster, cluster);
        }
    }

    private void upload() {
        int dirtyClusters = Math.max(highestTouchedCluster, previousHighestCluster) + 1;
        if (dirtyClusters <= 0) {
            return;
        }
        countView.clear();
        countView.put(counts, 0, dirtyClusters);
        countScratch.clear();
        countScratch.limit(dirtyClusters * Integer.BYTES);
        backend.writeBuffer(countBuffer, countScratch, 0L);
        int usedIndices = (highestTouchedCluster + 1) * MAX_PER;
        if (usedIndices <= 0) {
            return;
        }
        indexView.clear();
        indexView.put(indices, 0, usedIndices);
        indexScratch.clear();
        indexScratch.limit(usedIndices * Integer.BYTES);
        backend.writeBuffer(indexBuffer, indexScratch, 0L);
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        if (countBuffer != null) {
            backend.destroy(countBuffer);
        }
        if (indexBuffer != null) {
            backend.destroy(indexBuffer);
        }
        countBuffer = null;
        indexBuffer = null;
    }

    private static Vector3f[] createVectors() {
        Vector3f[] result = new Vector3f[CLUSTERS];
        for (int i = 0; i < result.length; i++) {
            result[i] = new Vector3f();
        }
        return result;
    }
}
