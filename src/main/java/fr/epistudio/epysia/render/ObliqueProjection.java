package fr.epistudio.epysia.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class ObliqueProjection {

    private static final float MINIMUM_PLANE_DISTANCE = 1.0e-6f;

    private ObliqueProjection() {
    }

    public static Vector4f planeFrom(Vector3fc pointOnPlane, Vector3fc planeNormal, Matrix4fc view) {
        Vector4f viewSpaceNormal = new Vector4f(planeNormal, 0.0f).mul(new Matrix4f(view).invert().transpose());
        Vector4f viewSpacePoint = new Vector4f(pointOnPlane, 1.0f).mul(view);
        Vector3fc normal = normalized(viewSpaceNormal);
        float offset = -(normal.x() * viewSpacePoint.x + normal.y() * viewSpacePoint.y
                + normal.z() * viewSpacePoint.z);
        return facingCamera(new Vector4f(normal.x(), normal.y(), normal.z(), offset));
    }

    public static Vector4f facingCamera(Vector4f viewSpacePlane) {
        return viewSpacePlane.w < 0.0f ? viewSpacePlane.negate() : viewSpacePlane;
    }

    private static Vector3fc normalized(Vector4f viewSpaceNormal) {
        return new org.joml.Vector3f(viewSpaceNormal.x, viewSpaceNormal.y, viewSpaceNormal.z).normalize();
    }

    public static Matrix4f withObliqueNearPlane(Matrix4fc projection, Vector4f viewSpacePlane,
                                                Matrix4f destination) {
        destination.set(projection);
        float normalLengthSquared = viewSpacePlane.x * viewSpacePlane.x
                + viewSpacePlane.y * viewSpacePlane.y + viewSpacePlane.z * viewSpacePlane.z;
        if (normalLengthSquared < MINIMUM_PLANE_DISTANCE) {
            return destination;
        }
        Vector4f corner = oppositeFrustumCorner(destination, viewSpacePlane);
        Vector4f scaled = new Vector4f(viewSpacePlane).mul(2.0f / viewSpacePlane.dot(corner));
        destination.m02(scaled.x);
        destination.m12(scaled.y);
        destination.m22(scaled.z + 1.0f);
        destination.m32(scaled.w);
        return destination;
    }

    private static Vector4f oppositeFrustumCorner(Matrix4f projection, Vector4f viewSpacePlane) {
        return new Vector4f(
                (-Math.signum(viewSpacePlane.x) + projection.m20()) / projection.m00(),
                (-Math.signum(viewSpacePlane.y) + projection.m21()) / projection.m11(),
                -1.0f,
                (1.0f + projection.m22()) / projection.m32());
    }
}
