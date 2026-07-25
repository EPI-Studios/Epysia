package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import fr.epistudio.epysia.editor.assets.SpriteOpaqueBounds;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public final class TileShapeOperations {

    private TileShapeOperations() {
    }

    public static TileCollisionShape rectangleFrom(SpriteOpaqueBounds.UnitBounds bounds) {
        return new TileCollisionShape(List.of(
                new Vector2f(bounds.minX(), bounds.minY()),
                new Vector2f(bounds.maxX(), bounds.minY()),
                new Vector2f(bounds.maxX(), bounds.maxY()),
                new Vector2f(bounds.minX(), bounds.maxY())), false, 0.0f);
    }

    public static boolean flipHorizontally(TileData data) {
        return transformShapes(data, point -> new Vector2f(1.0f - point.x, point.y));
    }

    public static boolean flipVertically(TileData data) {
        return transformShapes(data, point -> new Vector2f(point.x, 1.0f - point.y));
    }

    public static boolean rotateRight(TileData data) {
        return transformShapes(data, point -> new Vector2f(point.y, 1.0f - point.x));
    }

    public static boolean rotateLeft(TileData data) {
        return transformShapes(data, point -> new Vector2f(1.0f - point.y, point.x));
    }

    private static boolean transformShapes(TileData data, java.util.function.UnaryOperator<Vector2f> mapping) {
        List<TileCollisionShape> shapes = data.collisionShapes();
        if (shapes.isEmpty()) {
            return false;
        }
        for (int shapeIndex = 0; shapeIndex < shapes.size(); shapeIndex++) {
            data.replaceCollisionShape(shapeIndex, mapPoints(shapes.get(shapeIndex), mapping));
        }
        return true;
    }

    private static TileCollisionShape mapPoints(TileCollisionShape shape,
                                                java.util.function.UnaryOperator<Vector2f> mapping) {
        List<Vector2f> points = new ArrayList<>();
        for (Vector2f point : shape.points()) {
            points.add(mapping.apply(point));
        }
        return new TileCollisionShape(points, shape.oneWay(), shape.oneWayMargin());
    }
}
