package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.components.Collider;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class ColliderShapeWriter {

    private ColliderShapeWriter() {
    }

    static void write(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f world, Collider collider) {
        Vector3fc offset = collider.offset();
        Matrix4f local = new Matrix4f(world).translate(offset.x(), offset.y(), offset.z());
        ShapeDescriptor shape = collider.shape();
        switch (shape) {
            case ShapeDescriptor.Box box -> writeBox(writer, local, box.halfExtents());
            case ShapeDescriptor.Sphere sphere -> writeSphere(writer, local, sphere.radius());
            case ShapeDescriptor.Capsule capsule -> writeCapsule(writer, local, capsule.radius(), capsule.halfHeight());
            case ShapeDescriptor.TriangleMesh mesh -> writeBoundingBox(writer, local, mesh.vertices());
            case ShapeDescriptor.ConvexHull hull -> writeBoundingBox(writer, local, hull.vertices());
        }
    }

    private static void writeBox(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local, Vector3fc halfExtents) {
        writeBox(writer, local, halfExtents.x(), halfExtents.y(), halfExtents.z());
    }

    private static void writeBox(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local,
                                 float hx, float hy, float hz) {
        Vector3f[] corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            float sx = (i & 1) == 0 ? -hx : hx;
            float sy = (i & 2) == 0 ? -hy : hy;
            float sz = (i & 4) == 0 ? -hz : hz;
            corners[i] = transform(local, sx, sy, sz);
        }
        int[][] edges = {
                {0, 1}, {1, 3}, {3, 2}, {2, 0},
                {4, 5}, {5, 7}, {7, 6}, {6, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            writer.edge(corners[edge[0]], corners[edge[1]]);
        }
    }

    private static void writeSphere(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local, float radius) {
        int segments = writer.sphereSegments();
        writeRing(writer, local, radius, segments, Axis.XY);
        writeRing(writer, local, radius, segments, Axis.XZ);
        writeRing(writer, local, radius, segments, Axis.YZ);
    }

    private static void writeCapsule(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local,
                                     float radius, float halfHeight) {
        int segments = writer.sphereSegments();
        writeRing(writer, local.translate(0, halfHeight, 0, new Matrix4f()), radius, segments, Axis.XZ);
        writeRing(writer, local.translate(0, -halfHeight, 0, new Matrix4f()), radius, segments, Axis.XZ);
        Vector3f topX = transform(local, radius, halfHeight, 0);
        Vector3f bottomX = transform(local, radius, -halfHeight, 0);
        Vector3f topZ = transform(local, 0, halfHeight, radius);
        Vector3f bottomZ = transform(local, 0, -halfHeight, radius);
        writer.edge(topX, bottomX);
        writer.edge(transform(local, -radius, halfHeight, 0), transform(local, -radius, -halfHeight, 0));
        writer.edge(topZ, bottomZ);
        writer.edge(transform(local, 0, halfHeight, -radius), transform(local, 0, -halfHeight, -radius));
    }

    private static void writeRing(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local,
                                  float radius, int segments, Axis axis) {
        Vector3f previous = ringPoint(local, radius, 0, axis);
        for (int i = 1; i <= segments; i++) {
            float angle = (float) (i * (Math.PI * 2.0) / segments);
            Vector3f current = ringPoint(local, radius, angle, axis);
            writer.edge(previous, current);
            previous = current;
        }
    }

    private static Vector3f ringPoint(Matrix4f local, float radius, float angle, Axis axis) {
        float cos = (float) Math.cos(angle) * radius;
        float sin = (float) Math.sin(angle) * radius;
        return switch (axis) {
            case XY -> transform(local, cos, sin, 0);
            case XZ -> transform(local, cos, 0, sin);
            case YZ -> transform(local, 0, cos, sin);
        };
    }

    private static void writeBoundingBox(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local, float[] vertices) {
        if (vertices.length < 3) {
            return;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            maxX = Math.max(maxX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            maxY = Math.max(maxY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        Matrix4f centered = new Matrix4f(local).translate(
                (minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f);
        writeBox(writer, centered, (maxX - minX) * 0.5f, (maxY - minY) * 0.5f, (maxZ - minZ) * 0.5f);
    }

    private static Vector3f transform(Matrix4f matrix, float x, float y, float z) {
        return matrix.transformPosition(new Vector3f(x, y, z));
    }

    private enum Axis {
        XY,
        XZ,
        YZ
    }
}
