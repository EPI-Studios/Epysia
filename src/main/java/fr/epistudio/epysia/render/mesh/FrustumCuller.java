package fr.epistudio.epysia.render.mesh;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class FrustumCuller {

    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Vector3f scratchMin = new Vector3f();
    private final Vector3f scratchMax = new Vector3f();
    private final Vector3f scratchCorner = new Vector3f();

    void setProjection(Matrix4f viewProjection) {
        frustum.set(viewProjection);
    }

    boolean isCulled(Aabb localBounds, Matrix4f modelMatrix) {
        if (localBounds == null) {
            return false;
        }
        computeWorldBounds(localBounds, modelMatrix, scratchMin, scratchMax);
        return !frustum.testAab(scratchMin, scratchMax);
    }

    boolean isCulled(Vector3f worldMin, Vector3f worldMax) {
        return !frustum.testAab(worldMin, worldMax);
    }

    void computeWorldBounds(Aabb localBounds, Matrix4f modelMatrix,
                            Vector3f outMin, Vector3f outMax) {
        if (localBounds == null) {
            outMin.set(Float.NEGATIVE_INFINITY);
            outMax.set(Float.POSITIVE_INFINITY);
            return;
        }
        outMin.set(Float.POSITIVE_INFINITY);
        outMax.set(Float.NEGATIVE_INFINITY);
        Vector3f corner = scratchCorner;
        for (int index = 0; index < 8; index++) {
            float x = (index & 1) == 0 ? localBounds.minX() : localBounds.maxX();
            float y = (index & 2) == 0 ? localBounds.minY() : localBounds.maxY();
            float z = (index & 4) == 0 ? localBounds.minZ() : localBounds.maxZ();
            corner.set(x, y, z);
            modelMatrix.transformPosition(corner);
            outMin.min(corner);
            outMax.max(corner);
        }
    }
}
