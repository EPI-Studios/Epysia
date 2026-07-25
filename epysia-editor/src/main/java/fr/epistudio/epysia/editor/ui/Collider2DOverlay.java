package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.physics.api.ShapeDescriptor;
import fr.epistudio.epysia.physics.components.CharacterController2D;
import fr.epistudio.epysia.physics.components.Collider2D;
import fr.epistudio.epysia.physics.components.TilemapCollider2D;
import imgui.ImDrawList;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public final class Collider2DOverlay {

    private static final int COLOR_COLLIDER = 0xFF44DD44;
    private static final int COLOR_CONTROLLER = 0xFFFF9944;
    private static final float LINE_THICKNESS = 1.5f;
    private static final int CIRCLE_SEGMENTS = 24;

    private Collider2DOverlay() {
    }

    public static void draw(GameObject gameObject, Transform2D transform, ImDrawList drawList,
                            TilemapCollisionOverlay.LocalProjection projection) {
        for (IComponent component : gameObject.components()) {
            if (component instanceof Collider2D collider && !(collider instanceof TilemapCollider2D)) {
                drawCollider(collider, transform, drawList, projection);
            }
        }
        gameObject.getComponent(CharacterController2D.class)
                .ifPresent(controller -> drawController(controller, transform, drawList, projection));
    }

    private static void drawCollider(Collider2D collider, Transform2D transform, ImDrawList drawList,
                                     TilemapCollisionOverlay.LocalProjection projection) {
        for (Collider2D.ShapePlacement placement : collider.shapePlacements()) {
            drawShape(placement.shape(), placement.offset(), COLOR_COLLIDER, transform, drawList, projection);
        }
    }

    private static void drawController(CharacterController2D controller, Transform2D transform,
                                       ImDrawList drawList, TilemapCollisionOverlay.LocalProjection projection) {
        drawShape(controller.shape(), controller.capsuleOffset(), COLOR_CONTROLLER,
                transform, drawList, projection);
    }

    private static void drawShape(ShapeDescriptor shape, Vector2f offset, int color, Transform2D transform,
                                  ImDrawList drawList, TilemapCollisionOverlay.LocalProjection projection) {
        switch (shape) {
            case ShapeDescriptor.Box box -> drawRectangle(drawList, projection, transform, offset,
                    box.halfExtents().x(), box.halfExtents().y(), color);
            case ShapeDescriptor.Sphere sphere -> drawEllipse(drawList, projection, transform, offset,
                    sphere.radius(), sphere.radius(), color);
            case ShapeDescriptor.Capsule capsule -> drawRectangle(drawList, projection, transform, offset,
                    capsule.radius(), capsule.halfHeight() + capsule.radius(), color);
            case ShapeDescriptor.ConvexHull hull -> drawHull(drawList, projection, transform, offset, hull, color);
            default -> { }
        }
    }

    private static void drawRectangle(ImDrawList drawList, TilemapCollisionOverlay.LocalProjection projection,
                                      Transform2D transform, Vector2f offset,
                                      float halfWidth, float halfHeight, int color) {
        Vector2f cornerA = projection.project(transform, offset.x - halfWidth, offset.y - halfHeight);
        Vector2f cornerB = projection.project(transform, offset.x + halfWidth, offset.y - halfHeight);
        Vector2f cornerC = projection.project(transform, offset.x + halfWidth, offset.y + halfHeight);
        Vector2f cornerD = projection.project(transform, offset.x - halfWidth, offset.y + halfHeight);
        drawList.addQuad(cornerA.x, cornerA.y, cornerB.x, cornerB.y,
                cornerC.x, cornerC.y, cornerD.x, cornerD.y, color, LINE_THICKNESS);
    }

    private static void drawEllipse(ImDrawList drawList, TilemapCollisionOverlay.LocalProjection projection,
                                    Transform2D transform, Vector2f offset,
                                    float radiusX, float radiusY, int color) {
        Vector2f previous = projection.project(transform, offset.x + radiusX, offset.y);
        for (int segment = 1; segment <= CIRCLE_SEGMENTS; segment++) {
            double angle = 2.0 * Math.PI * segment / CIRCLE_SEGMENTS;
            Vector2f current = projection.project(transform,
                    offset.x + radiusX * (float) Math.cos(angle), offset.y + radiusY * (float) Math.sin(angle));
            drawList.addLine(previous.x, previous.y, current.x, current.y, color, LINE_THICKNESS);
            previous = current;
        }
    }

    private static void drawHull(ImDrawList drawList, TilemapCollisionOverlay.LocalProjection projection,
                                 Transform2D transform, Vector2f offset,
                                 ShapeDescriptor.ConvexHull hull, int color) {
        List<Vector2f> points = frontFace(hull, offset);
        for (int index = 0; index < points.size(); index++) {
            Vector2f start = projection.project(transform, points.get(index).x, points.get(index).y);
            Vector2f next = points.get((index + 1) % points.size());
            Vector2f end = projection.project(transform, next.x, next.y);
            drawList.addLine(start.x, start.y, end.x, end.y, color, LINE_THICKNESS);
        }
    }

    private static List<Vector2f> frontFace(ShapeDescriptor.ConvexHull hull, Vector2f offset) {
        float[] vertices = hull.vertices();
        List<Vector2f> points = new ArrayList<>();
        for (int index = 0; index + 5 < vertices.length; index += 6) {
            points.add(new Vector2f(offset.x + vertices[index], offset.y + vertices[index + 1]));
        }
        return points;
    }
}
