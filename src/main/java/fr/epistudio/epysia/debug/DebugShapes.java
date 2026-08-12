package fr.epistudio.epysia.debug;

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class DebugShapes {

    private static final int SPHERE_SEGMENTS = 24;
    private static final int CAPSULE_SEGMENTS = 16;
    private static final float ARROW_HEAD_FRACTION = 0.15f;
    private static final float ARROW_HEAD_SPREAD = 0.35f;

    private DebugShapes() {
    }

    static void cross(DebugDraw target, Vector3fc center, float size, int color, float seconds) {
        float half = size * 0.5f;
        target.line(center.x() - half, center.y(), center.z(),
                center.x() + half, center.y(), center.z(), color, seconds);
        target.line(center.x(), center.y() - half, center.z(),
                center.x(), center.y() + half, center.z(), color, seconds);
        target.line(center.x(), center.y(), center.z() - half,
                center.x(), center.y(), center.z() + half, color, seconds);
    }

    static void arrow(DebugDraw target, Vector3fc origin, Vector3fc direction,
                      int color, float seconds) {
        Vector3f tip = new Vector3f(origin).add(direction);
        target.line(origin.x(), origin.y(), origin.z(), tip.x(), tip.y(), tip.z(), color, seconds);
        Vector3f back = new Vector3f(direction).mul(-ARROW_HEAD_FRACTION);
        Vector3f sideways = perpendicularTo(direction).mul(direction.length() * ARROW_HEAD_SPREAD
                * ARROW_HEAD_FRACTION);
        appendHead(target, tip, back, sideways, color, seconds);
        appendHead(target, tip, back, sideways.negate(), color, seconds);
    }

    private static void appendHead(DebugDraw target, Vector3fc tip, Vector3fc back,
                                   Vector3fc sideways, int color, float seconds) {
        target.line(tip.x(), tip.y(), tip.z(),
                tip.x() + back.x() + sideways.x(),
                tip.y() + back.y() + sideways.y(),
                tip.z() + back.z() + sideways.z(), color, seconds);
    }

    private static Vector3f perpendicularTo(Vector3fc direction) {
        Vector3f reference = Math.abs(direction.y()) > 0.9f
                ? new Vector3f(1.0f, 0.0f, 0.0f)
                : new Vector3f(0.0f, 1.0f, 0.0f);
        return reference.cross(direction).normalize();
    }

    static void axisAlignedBox(DebugDraw target, Vector3fc center, Vector3fc halfExtents,
                               int color, float seconds) {
        Vector3f[] corners = new Vector3f[8];
        for (int index = 0; index < corners.length; index++) {
            corners[index] = new Vector3f(
                    center.x() + signOf(index, 1) * halfExtents.x(),
                    center.y() + signOf(index, 2) * halfExtents.y(),
                    center.z() + signOf(index, 4) * halfExtents.z());
        }
        connectBoxCorners(target, corners, color, seconds);
    }

    static void orientedBox(DebugDraw target, Matrix4fc transform, Vector3fc halfExtents,
                            int color, float seconds) {
        Vector3f[] corners = new Vector3f[8];
        for (int index = 0; index < corners.length; index++) {
            corners[index] = transform.transformPosition(new Vector3f(
                    signOf(index, 1) * halfExtents.x(),
                    signOf(index, 2) * halfExtents.y(),
                    signOf(index, 4) * halfExtents.z()));
        }
        connectBoxCorners(target, corners, color, seconds);
    }

    private static float signOf(int index, int bit) {
        return (index & bit) == 0 ? -1.0f : 1.0f;
    }

    private static void connectBoxCorners(DebugDraw target, Vector3f[] corners,
                                          int color, float seconds) {
        for (int index = 0; index < corners.length; index++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((index & bit) == 0) {
                    target.line(corners[index], corners[index | bit], color, seconds);
                }
            }
        }
    }

    static void sphere(DebugDraw target, Vector3fc center, float radius, int color, float seconds) {
        circle(target, center, radius, 0, 1, color, seconds);
        circle(target, center, radius, 1, 2, color, seconds);
        circle(target, center, radius, 0, 2, color, seconds);
    }

    private static void circle(DebugDraw target, Vector3fc center, float radius,
                               int firstAxis, int secondAxis, int color, float seconds) {
        Vector3f previous = pointOnCircle(center, radius, firstAxis, secondAxis, 0);
        for (int step = 1; step <= SPHERE_SEGMENTS; step++) {
            Vector3f current = pointOnCircle(center, radius, firstAxis, secondAxis, step);
            target.line(previous, current, color, seconds);
            previous = current;
        }
    }

    private static Vector3f pointOnCircle(Vector3fc center, float radius,
                                          int firstAxis, int secondAxis, int step) {
        double angle = 2.0 * Math.PI * step / SPHERE_SEGMENTS;
        Vector3f point = new Vector3f(center);
        point.setComponent(firstAxis, center.get(firstAxis) + radius * (float) Math.cos(angle));
        point.setComponent(secondAxis, center.get(secondAxis) + radius * (float) Math.sin(angle));
        return point;
    }

    static void capsule(DebugDraw target, Vector3fc start, Vector3fc end, float radius,
                        int color, float seconds) {
        sphere(target, start, radius, color, seconds);
        sphere(target, end, radius, color, seconds);
        Vector3f axis = new Vector3f(end).sub(start);
        Vector3f sideways = perpendicularTo(axis).mul(radius);
        Vector3f other = new Vector3f(axis).normalize().cross(sideways);
        connectCapsuleSides(target, start, end, sideways, other, color, seconds);
    }

    private static void connectCapsuleSides(DebugDraw target, Vector3fc start, Vector3fc end,
                                            Vector3fc sideways, Vector3fc other,
                                            int color, float seconds) {
        for (int step = 0; step < CAPSULE_SEGMENTS; step++) {
            double angle = 2.0 * Math.PI * step / CAPSULE_SEGMENTS;
            float cosine = (float) Math.cos(angle);
            float sine = (float) Math.sin(angle);
            float offsetX = sideways.x() * cosine + other.x() * sine;
            float offsetY = sideways.y() * cosine + other.y() * sine;
            float offsetZ = sideways.z() * cosine + other.z() * sine;
            target.line(start.x() + offsetX, start.y() + offsetY, start.z() + offsetZ,
                    end.x() + offsetX, end.y() + offsetY, end.z() + offsetZ, color, seconds);
        }
    }
}
