package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.components.JointComponent;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.scene.Scene;
import imgui.ImDrawList;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Optional;

public final class PhysicsDebugOverlay {

    public interface WorldProjection {
        Optional<float[]> toScreen(Vector3fc worldPoint);
    }

    private static final int COLOR_CENTER_OF_MASS = 0xFF33CCFF;
    private static final int COLOR_ASLEEP = 0xFF8080FF;
    private static final int COLOR_VELOCITY = 0xFF33FF66;
    private static final int COLOR_ANGULAR_VELOCITY = 0xFF33AAFF;
    private static final int COLOR_JOINT = 0xFF33DDDD;
    private static final float CROSS_HALF_SIZE = 0.15f;
    private static final float VELOCITY_SCALE = 0.15f;
    private static final float LINE_THICKNESS = 1.6f;
    private static final float ANCHOR_RADIUS = 4.0f;

    public static class Options {
        public boolean centerOfMass = true;
        public boolean velocities = true;
        public boolean sleepState = true;
        public boolean joints = true;
    }

    private PhysicsDebugOverlay() {
    }

    public static void draw(Scene scene, ImDrawList drawList, WorldProjection projection, Options options) {
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(RigidBodyComponent.class)
                    .ifPresent(body -> drawBody(body, drawList, projection, options));
            for (var component : gameObject.components()) {
                if (component instanceof JointComponent joint && options.joints) {
                    drawJoint(gameObject, joint, drawList, projection);
                }
            }
        }
    }

    private static void drawBody(RigidBodyComponent body, ImDrawList drawList,
                                 WorldProjection projection, Options options) {
        Optional<Vector3fc> center = body.worldCenterOfMass();
        if (center.isEmpty()) {
            return;
        }
        Vector3f origin = new Vector3f(center.get());
        if (options.centerOfMass) {
            int color = options.sleepState && !body.isAwake() ? COLOR_ASLEEP : COLOR_CENTER_OF_MASS;
            drawCross(origin, color, drawList, projection);
        }
        if (!options.velocities) {
            return;
        }
        body.velocity().ifPresent(velocity ->
                drawVector(origin, velocity, COLOR_VELOCITY, drawList, projection));
        body.angularVelocity().ifPresent(velocity ->
                drawVector(origin, velocity, COLOR_ANGULAR_VELOCITY, drawList, projection));
    }

    private static void drawCross(Vector3f origin, int color, ImDrawList drawList, WorldProjection projection) {
        segment(origin, new Vector3f(CROSS_HALF_SIZE, 0.0f, 0.0f), color, drawList, projection);
        segment(origin, new Vector3f(-CROSS_HALF_SIZE, 0.0f, 0.0f), color, drawList, projection);
        segment(origin, new Vector3f(0.0f, CROSS_HALF_SIZE, 0.0f), color, drawList, projection);
        segment(origin, new Vector3f(0.0f, -CROSS_HALF_SIZE, 0.0f), color, drawList, projection);
        segment(origin, new Vector3f(0.0f, 0.0f, CROSS_HALF_SIZE), color, drawList, projection);
        segment(origin, new Vector3f(0.0f, 0.0f, -CROSS_HALF_SIZE), color, drawList, projection);
    }

    private static void drawVector(Vector3f origin, Vector3fc value, int color,
                                   ImDrawList drawList, WorldProjection projection) {
        Vector3f scaled = new Vector3f(value).mul(VELOCITY_SCALE);
        if (scaled.lengthSquared() < 1.0e-6f) {
            return;
        }
        segment(origin, scaled, color, drawList, projection);
    }

    private static void drawJoint(GameObject owner, JointComponent joint, ImDrawList drawList,
                                  WorldProjection projection) {
        Optional<Transform3D> transform = owner.getComponent(Transform3D.class);
        if (transform.isEmpty()) {
            return;
        }
        Vector3f anchor = transform.get().worldMatrix().transformPosition(new Vector3f(joint.anchor()));
        projection.toScreen(anchor).ifPresent(point ->
                drawList.addCircleFilled(point[0], point[1], ANCHOR_RADIUS, COLOR_JOINT));
        joint.connectedBody().flatMap(other -> other.getComponent(Transform3D.class))
                .ifPresent(otherTransform -> link(anchor, otherTransform, drawList, projection));
    }

    private static void link(Vector3f anchor, Transform3D otherTransform, ImDrawList drawList,
                             WorldProjection projection) {
        Vector3f target = otherTransform.worldMatrix().transformPosition(new Vector3f());
        line(anchor, target, COLOR_JOINT, drawList, projection);
    }

    private static void segment(Vector3f origin, Vector3f offset, int color,
                                ImDrawList drawList, WorldProjection projection) {
        line(origin, new Vector3f(origin).add(offset), color, drawList, projection);
    }

    private static void line(Vector3f from, Vector3f to, int color,
                             ImDrawList drawList, WorldProjection projection) {
        Optional<float[]> start = projection.toScreen(from);
        Optional<float[]> end = projection.toScreen(to);
        if (start.isEmpty() || end.isEmpty()) {
            return;
        }
        drawList.addLine(start.get()[0], start.get()[1], end.get()[0], end.get()[1], color, LINE_THICKNESS);
    }
}
