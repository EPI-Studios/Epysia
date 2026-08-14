package fr.epistudio.epysia.physics.box3d;

import com.meekdev.box3d.Vec3;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

record Box3dQueryProxy(List<Vec3> points, float radius) {

    private static final int HULL_MAX_POINTS = 64;
    private static final int VERTEX_STRIDE = 3;

    static Box3dQueryProxy of(ShapeDescriptor shape, Quaternionfc rotation) {
        return switch (shape) {
            case ShapeDescriptor.Box box -> new Box3dQueryProxy(boxCorners(box, rotation), 0.0f);
            case ShapeDescriptor.Sphere sphere -> new Box3dQueryProxy(List.of(Vec3.zero), sphere.radius());
            case ShapeDescriptor.Capsule capsule -> capsuleProxy(capsule, rotation);
            case ShapeDescriptor.ConvexHull hull -> hullProxy(hull.vertices(), rotation);
            case ShapeDescriptor.TriangleMesh mesh ->
                    new Box3dQueryProxy(List.of(Vec3.zero), Box3dShapeAttacher.boundingRadius(mesh));
        };
    }

    private static List<Vec3> boxCorners(ShapeDescriptor.Box box, Quaternionfc rotation) {
        List<Vec3> corners = new ArrayList<>(8);
        for (int sign = 0; sign < 8; sign++) {
            corners.add(rotated(rotation,
                    box.halfExtents().x() * signOf(sign, 0),
                    box.halfExtents().y() * signOf(sign, 1),
                    box.halfExtents().z() * signOf(sign, 2)));
        }
        return corners;
    }

    private static float signOf(int corner, int axis) {
        return (corner >> axis & 1) == 0 ? -1.0f : 1.0f;
    }

    private static Box3dQueryProxy capsuleProxy(ShapeDescriptor.Capsule capsule, Quaternionfc rotation) {
        return new Box3dQueryProxy(List.of(
                rotated(rotation, 0.0f, -capsule.halfHeight(), 0.0f),
                rotated(rotation, 0.0f, capsule.halfHeight(), 0.0f)), capsule.radius());
    }

    private static Box3dQueryProxy hullProxy(float[] vertices, Quaternionfc rotation) {
        int available = vertices.length / VERTEX_STRIDE;
        if (available == 0) {
            return new Box3dQueryProxy(List.of(Vec3.zero), 0.0f);
        }
        int step = Math.max(1, available / HULL_MAX_POINTS);
        List<Vec3> points = new ArrayList<>();
        for (int vertex = 0; vertex < available; vertex += step) {
            int base = vertex * VERTEX_STRIDE;
            points.add(rotated(rotation, vertices[base], vertices[base + 1], vertices[base + 2]));
        }
        return new Box3dQueryProxy(points, 0.0f);
    }

    private static Vec3 rotated(Quaternionfc rotation, float x, float y, float z) {
        Vector3f point = rotation.transform(new Vector3f(x, y, z));
        return new Vec3(point.x, point.y, point.z);
    }
}
