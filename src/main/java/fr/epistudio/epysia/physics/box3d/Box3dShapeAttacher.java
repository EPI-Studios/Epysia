package fr.epistudio.epysia.physics.box3d;

import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.box3d.B3Hull;
import com.meekdev.box3d.B3Mesh;
import com.meekdev.box3d.B3Shape;
import com.meekdev.box3d.B3ShapeConfig;
import com.meekdev.box3d.Vec3;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import org.joml.Vector3fc;

import java.util.List;

final class Box3dShapeAttacher {

    private static final int HULL_MAX_VERTICES = 64;

    private Box3dShapeAttacher() {
    }

    static B3Shape attach(B3Body body, ShapeDescriptor shape, Vector3fc offset, B3ShapeConfig config,
                          List<Runnable> keepAlive) {
        return switch (shape) {
            case ShapeDescriptor.Box box -> body.addBoxAt(toVec(offset), toVec(box.halfExtents()), config);
            case ShapeDescriptor.Sphere sphere -> body.addSphereAt(toVec(offset), sphere.radius(), config);
            case ShapeDescriptor.Capsule capsule -> attachCapsule(body, capsule, offset, config);
            case ShapeDescriptor.ConvexHull hull -> attachHull(body, hull, offset, config, keepAlive);
            case ShapeDescriptor.TriangleMesh mesh -> attachMesh(body, mesh, offset, config, keepAlive);
        };
    }

    static float boundingRadius(ShapeDescriptor shape) {
        return switch (shape) {
            case ShapeDescriptor.Box box -> box.halfExtents().length();
            case ShapeDescriptor.Sphere sphere -> sphere.radius();
            case ShapeDescriptor.Capsule capsule -> capsule.radius() + capsule.halfHeight();
            case ShapeDescriptor.ConvexHull hull -> maxVertexDistance(hull.vertices());
            case ShapeDescriptor.TriangleMesh mesh -> maxVertexDistance(mesh.vertices());
        };
    }

    static Vec3 toVec(Vector3fc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static B3Shape attachCapsule(B3Body body, ShapeDescriptor.Capsule capsule, Vector3fc offset,
                                         B3ShapeConfig config) {
        Vec3 lower = new Vec3(offset.x(), offset.y() - capsule.halfHeight(), offset.z());
        Vec3 upper = new Vec3(offset.x(), offset.y() + capsule.halfHeight(), offset.z());
        return body.addCapsule(lower, upper, capsule.radius(), config);
    }

    private static B3Shape attachHull(B3Body body, ShapeDescriptor.ConvexHull hull, Vector3fc offset,
                                      B3ShapeConfig config, List<Runnable> keepAlive) {
        B3Hull baked = B3Hull.bake(translated(hull.vertices(), offset), HULL_MAX_VERTICES);
        keepAlive.add(baked::close);
        return body.addHull(baked, config);
    }

    private static B3Shape attachMesh(B3Body body, ShapeDescriptor.TriangleMesh mesh, Vector3fc offset,
                                      B3ShapeConfig config, List<Runnable> keepAlive) {
        if (body.type() == B3BodyType.DYNAMIC) {
            throw new EpysiaException("Triangle mesh shapes cannot back a dynamic body.");
        }
        B3Mesh baked = B3Mesh.bake(translated(mesh.vertices(), offset), mesh.indices());
        keepAlive.add(baked::close);
        return body.addMesh(baked, config);
    }

    private static float[] translated(float[] vertices, Vector3fc offset) {
        if (offset.x() == 0.0f && offset.y() == 0.0f && offset.z() == 0.0f) {
            return vertices;
        }
        float[] moved = new float[vertices.length];
        for (int index = 0; index < vertices.length; index += 3) {
            moved[index] = vertices[index] + offset.x();
            moved[index + 1] = vertices[index + 1] + offset.y();
            moved[index + 2] = vertices[index + 2] + offset.z();
        }
        return moved;
    }

    private static float maxVertexDistance(float[] vertices) {
        float maxSquared = 0.0f;
        for (int index = 0; index < vertices.length; index += 3) {
            float squared = vertices[index] * vertices[index]
                    + vertices[index + 1] * vertices[index + 1]
                    + vertices[index + 2] * vertices[index + 2];
            maxSquared = Math.max(maxSquared, squared);
        }
        return (float) Math.sqrt(maxSquared);
    }
}
