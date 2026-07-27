package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import imgui.ImDrawList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;

public final class LightDirectionOverlay {

    public record ScreenRect(float originX, float originY, float width, float height) {
    }

    private static final int COLOR_SPOT = 0xFF33CCFF;
    private static final int COLOR_DIRECTIONAL = 0xFF66DDFF;
    private static final int COLOR_POINT = 0x9933CCFF;
    private static final int COLOR_SOURCE = 0xFFFFDD66;
    private static final float LINE_THICKNESS = 1.4f;
    private static final float DIRECTIONAL_LENGTH = 3.0f;
    private static final int CONE_RIBS = 8;
    private static final int RING_SEGMENTS = 32;
    private static final Vector3f LOCAL_FORWARD = new Vector3f(0.0f, 0.0f, -1.0f);

    private final Matrix4f viewProjection = new Matrix4f();
    private final Quaternionf worldRotation = new Quaternionf();

    public void render(List<GameObject> gameObjects, Matrix4f cameraViewProjection,
                       ImDrawList drawList, ScreenRect rect) {
        viewProjection.set(cameraViewProjection);
        for (GameObject gameObject : gameObjects) {
            gameObject.getComponent(Transform3D.class)
                    .ifPresent(transform -> renderFor(gameObject, transform, drawList, rect));
        }
    }

    private void renderFor(GameObject gameObject, Transform3D transform, ImDrawList drawList,
                           ScreenRect rect) {
        Vector3f origin = transform.worldPosition(new Vector3f());
        Vector3f forward = transform.worldRotation(worldRotation).transform(LOCAL_FORWARD, new Vector3f());
        gameObject.getComponent(SpotLight.class).ifPresent(light ->
                drawCone(origin, forward, light.range(), outerAngleOf(light), COLOR_SPOT, drawList, rect));
        gameObject.getComponent(DirectionalLight.class).ifPresent(light ->
                drawArrow(origin, forward, drawList, rect));
        gameObject.getComponent(PointLight.class).ifPresent(light ->
                drawRangeRings(origin, light.range(), drawList, rect));
        gameObject.getComponent(Light.class).ifPresent(light ->
                drawSourceSphere(origin, light.sourceRadius(), drawList, rect));
    }

    private void drawSourceSphere(Vector3f origin, float sourceRadius, ImDrawList drawList,
                                  ScreenRect rect) {
        if (sourceRadius <= 0.0f) {
            return;
        }
        drawRing(origin, new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f), sourceRadius,
                COLOR_SOURCE, drawList, rect);
        drawRing(origin, new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), sourceRadius,
                COLOR_SOURCE, drawList, rect);
        drawRing(origin, new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), sourceRadius,
                COLOR_SOURCE, drawList, rect);
    }

    private static float outerAngleOf(SpotLight light) {
        return (float) Math.acos(Math.clamp(light.outerConeCosine(), -1.0f, 1.0f));
    }

    private void drawCone(Vector3f origin, Vector3f forward, float range, float halfAngle,
                          int color, ImDrawList drawList, ScreenRect rect) {
        Vector3f tip = new Vector3f(forward).mul(range).add(origin);
        float radius = (float) (range * Math.tan(halfAngle));
        Vector3f right = perpendicularTo(forward);
        Vector3f up = new Vector3f(forward).cross(right, new Vector3f()).normalize();
        for (int rib = 0; rib < CONE_RIBS; rib++) {
            double angle = 2.0 * Math.PI * rib / CONE_RIBS;
            drawSegment(origin, rimPoint(tip, right, up, radius, angle), color, drawList, rect);
        }
        drawRing(tip, right, up, radius, color, drawList, rect);
    }

    private void drawArrow(Vector3f origin, Vector3f forward, ImDrawList drawList, ScreenRect rect) {
        Vector3f tip = new Vector3f(forward).mul(DIRECTIONAL_LENGTH).add(origin);
        drawSegment(origin, tip, COLOR_DIRECTIONAL, drawList, rect);
        Vector3f right = perpendicularTo(forward);
        Vector3f up = new Vector3f(forward).cross(right, new Vector3f()).normalize();
        Vector3f base = new Vector3f(forward).mul(DIRECTIONAL_LENGTH * 0.8f).add(origin);
        float radius = DIRECTIONAL_LENGTH * 0.08f;
        for (int rib = 0; rib < 4; rib++) {
            double angle = 2.0 * Math.PI * rib / 4.0;
            drawSegment(tip, rimPoint(base, right, up, radius, angle), COLOR_DIRECTIONAL, drawList, rect);
        }
    }

    private void drawRangeRings(Vector3f origin, float range, ImDrawList drawList, ScreenRect rect) {
        drawRing(origin, new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f), range,
                COLOR_POINT, drawList, rect);
        drawRing(origin, new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), range,
                COLOR_POINT, drawList, rect);
        drawRing(origin, new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), range,
                COLOR_POINT, drawList, rect);
    }

    private void drawRing(Vector3f center, Vector3f right, Vector3f up, float radius, int color,
                          ImDrawList drawList, ScreenRect rect) {
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            double start = 2.0 * Math.PI * segment / RING_SEGMENTS;
            double end = 2.0 * Math.PI * (segment + 1) / RING_SEGMENTS;
            drawSegment(rimPoint(center, right, up, radius, start),
                    rimPoint(center, right, up, radius, end), color, drawList, rect);
        }
    }

    private static Vector3f rimPoint(Vector3f center, Vector3f right, Vector3f up, float radius,
                                     double angle) {
        return new Vector3f(right).mul((float) (Math.cos(angle) * radius))
                .add(new Vector3f(up).mul((float) (Math.sin(angle) * radius)))
                .add(center);
    }

    private static Vector3f perpendicularTo(Vector3f direction) {
        Vector3f reference = Math.abs(direction.y) > 0.99f
                ? new Vector3f(1.0f, 0.0f, 0.0f)
                : new Vector3f(0.0f, 1.0f, 0.0f);
        return new Vector3f(direction).cross(reference, new Vector3f()).normalize();
    }

    private void drawSegment(Vector3f start, Vector3f end, int color, ImDrawList drawList,
                             ScreenRect rect) {
        Optional<float[]> from = project(start, rect);
        Optional<float[]> to = project(end, rect);
        if (from.isEmpty() || to.isEmpty()) {
            return;
        }
        drawList.addLine(from.get()[0], from.get()[1], to.get()[0], to.get()[1], color, LINE_THICKNESS);
    }

    private Optional<float[]> project(Vector3f world, ScreenRect rect) {
        Vector4f clip = viewProjection.transform(new Vector4f(world, 1.0f));
        if (clip.w <= 0.0f) {
            return Optional.empty();
        }
        return Optional.of(new float[] {
                rect.originX() + (clip.x / clip.w * 0.5f + 0.5f) * rect.width(),
                rect.originY() + (0.5f - clip.y / clip.w * 0.5f) * rect.height()
        });
    }
}
