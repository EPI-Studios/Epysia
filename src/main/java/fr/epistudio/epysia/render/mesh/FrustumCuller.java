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
        scratchMin.set(Float.POSITIVE_INFINITY);
        scratchMax.set(Float.NEGATIVE_INFINITY);
        for (int corner = 0; corner < 8; corner++) {
            float x = (corner & 1) == 0 ? localBounds.minX() : localBounds.maxX();
            float y = (corner & 2) == 0 ? localBounds.minY() : localBounds.maxY();
            float z = (corner & 4) == 0 ? localBounds.minZ() : localBounds.maxZ();
            scratchCorner.set(x, y, z);
            modelMatrix.transformPosition(scratchCorner);
            scratchMin.min(scratchCorner);
            scratchMax.max(scratchCorner);
        }
        return !frustum.testAab(scratchMin, scratchMax);
    }
}
