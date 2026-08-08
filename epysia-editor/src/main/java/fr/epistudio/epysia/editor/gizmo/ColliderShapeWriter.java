package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.components.Collider;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ColliderShapeWriter {

    private ColliderShapeWriter() {
    }

    static float[] buildLocalEdges(ShapeDescriptor shape) {
        List<Float> points = new ArrayList<>();
        switch (shape) {
            case ShapeDescriptor.TriangleMesh mesh -> collectTriangles(points, mesh.vertices(), mesh.indices());
            case ShapeDescriptor.ConvexHull hull -> collectHull(points, hull.vertices());
            default -> {
            }
        }
        float[] edges = new float[points.size()];
        for (int index = 0; index < edges.length; index++) {
            edges[index] = points.get(index);
        }
        return edges;
    }

    private static void collectTriangles(List<Float> points, float[] vertices, int[] indices) {
        if (vertices.length < 9) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        int drawn = 0;
        if (indices == null || indices.length < 3) {
            for (int base = 0; base + 8 < vertices.length && drawn < MAXIMUM_TRIANGLE_EDGES; base += 9) {
                int first = base / 3;
                drawn += collectTriangleEdges(points, vertices, seen, first, first + 1, first + 2);
            }
            return;
        }
        for (int triangle = 0; triangle + 2 < indices.length && drawn < MAXIMUM_TRIANGLE_EDGES; triangle += 3) {
            drawn += collectTriangleEdges(points, vertices, seen,
                    indices[triangle], indices[triangle + 1], indices[triangle + 2]);
        }
    }

    private static int collectTriangleEdges(List<Float> points, float[] vertices,
                                            Set<Long> seen, int first, int second, int third) {
        return collectEdge(points, vertices, seen, first, second)
                + collectEdge(points, vertices, seen, second, third)
                + collectEdge(points, vertices, seen, third, first);
    }

    private static int collectEdge(List<Float> points, float[] vertices,
                                   Set<Long> seen, int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        if ((high * 3) + 2 >= vertices.length || !seen.add(((long) low << 32) | high)) {
            return 0;
        }
        points.add(vertices[first * 3]);
        points.add(vertices[first * 3 + 1]);
        points.add(vertices[first * 3 + 2]);
        points.add(vertices[second * 3]);
        points.add(vertices[second * 3 + 1]);
        points.add(vertices[second * 3 + 2]);
        return 1;
    }

    private static void collectHull(List<Float> points, float[] vertices) {
        int count = vertices.length / 3;
        int edges = 0;
        for (int first = 0; first < count && edges < MAXIMUM_HULL_EDGES; first++) {
            for (int second = first + 1; second < count && edges < MAXIMUM_HULL_EDGES; second++) {
                points.add(vertices[first * 3]);
                points.add(vertices[first * 3 + 1]);
                points.add(vertices[first * 3 + 2]);
                points.add(vertices[second * 3]);
                points.add(vertices[second * 3 + 1]);
                points.add(vertices[second * 3 + 2]);
                edges++;
            }
        }
    }

    static void writeCachedEdges(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f world, float[] edges) {
        for (int index = 0; index + 5 < edges.length; index += 6) {
            float startX = world.m00() * edges[index] + world.m10() * edges[index + 1]
                    + world.m20() * edges[index + 2] + world.m30();
            float startY = world.m01() * edges[index] + world.m11() * edges[index + 1]
                    + world.m21() * edges[index + 2] + world.m31();
            float startZ = world.m02() * edges[index] + world.m12() * edges[index + 1]
                    + world.m22() * edges[index + 2] + world.m32();
            float endX = world.m00() * edges[index + 3] + world.m10() * edges[index + 4]
                    + world.m20() * edges[index + 5] + world.m30();
            float endY = world.m01() * edges[index + 3] + world.m11() * edges[index + 4]
                    + world.m21() * edges[index + 5] + world.m31();
            float endZ = world.m02() * edges[index + 3] + world.m12() * edges[index + 4]
                    + world.m22() * edges[index + 5] + world.m32();
            writer.edge(startX, startY, startZ, endX, endY, endZ);
        }
    }

    static void write(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f world, Collider collider,
                      ColliderEdgeCache cache) {
        Vector3fc offset = collider.offset();
        Matrix4f local = new Matrix4f(world).translate(offset.x(), offset.y(), offset.z());
        ShapeDescriptor shape = collider.shape();
        switch (shape) {
            case ShapeDescriptor.Box box -> writeBox(writer, local, box.halfExtents());
            case ShapeDescriptor.Sphere sphere -> writeSphere(writer, local, sphere.radius());
            case ShapeDescriptor.Capsule capsule -> writeCapsule(writer, local, capsule.radius(), capsule.halfHeight());
            case ShapeDescriptor.TriangleMesh mesh -> writeCachedEdges(writer, local, cache.edgesOf(mesh));
            case ShapeDescriptor.ConvexHull hull -> writeCachedEdges(writer, local, cache.edgesOf(hull));
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

    private static final int MAXIMUM_TRIANGLE_EDGES = 60000;
    private static final int MAXIMUM_HULL_EDGES = 4000;

    private static void writeTriangles(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local,
                                       float[] vertices, int[] indices) {
        if (vertices.length < 9) {
            return;
        }
        if (indices == null || indices.length < 3) {
            writeSequentialTriangles(writer, local, vertices);
            return;
        }
        Set<Long> seen = new HashSet<>();
        int drawn = 0;
        for (int triangle = 0; triangle + 2 < indices.length && drawn < MAXIMUM_TRIANGLE_EDGES; triangle += 3) {
            drawn += writeTriangleEdges(writer, local, vertices, seen,
                    indices[triangle], indices[triangle + 1], indices[triangle + 2]);
        }
        if (drawn >= MAXIMUM_TRIANGLE_EDGES) {
            writeBoundingBox(writer, local, vertices);
        }
    }

    private static void writeSequentialTriangles(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local,
                                                 float[] vertices) {
        int triangleCount = vertices.length / 9;
        Set<Long> seen = new HashSet<>();
        for (int triangle = 0; triangle < triangleCount && triangle * 3 < MAXIMUM_TRIANGLE_EDGES; triangle++) {
            int base = triangle * 3;
            writeTriangleEdges(writer, local, vertices, seen, base, base + 1, base + 2);
        }
    }

    private static int writeTriangleEdges(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local,
                                          float[] vertices, Set<Long> seen,
                                          int first, int second, int third) {
        int drawn = 0;
        drawn += writeUniqueEdge(writer, local, vertices, seen, first, second) ? 1 : 0;
        drawn += writeUniqueEdge(writer, local, vertices, seen, second, third) ? 1 : 0;
        drawn += writeUniqueEdge(writer, local, vertices, seen, third, first) ? 1 : 0;
        return drawn;
    }

    private static boolean writeUniqueEdge(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local,
                                           float[] vertices, Set<Long> seen, int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        if (!seen.add(((long) low << 32) | high)) {
            return false;
        }
        if ((high * 3) + 2 >= vertices.length) {
            return false;
        }
        writer.edge(vertexAt(local, vertices, first), vertexAt(local, vertices, second));
        return true;
    }

    private static Vector3f vertexAt(Matrix4f local, float[] vertices, int index) {
        int base = index * 3;
        return transform(local, vertices[base], vertices[base + 1], vertices[base + 2]);
    }

    private static void writeHull(ColliderWireframeOverlay.EdgeWriter writer, Matrix4f local, float[] vertices) {
        int count = vertices.length / 3;
        if (count < 2) {
            return;
        }
        int edges = 0;
        for (int first = 0; first < count && edges < MAXIMUM_HULL_EDGES; first++) {
            for (int second = first + 1; second < count && edges < MAXIMUM_HULL_EDGES; second++) {
                writer.edge(vertexAt(local, vertices, first), vertexAt(local, vertices, second));
                edges++;
            }
        }
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
