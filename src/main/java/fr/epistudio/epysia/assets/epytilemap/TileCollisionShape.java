package fr.epistudio.epysia.assets.epytilemap;

import org.joml.Vector2f;

import java.util.List;

public record TileCollisionShape(List<Vector2f> points, boolean oneWay, float oneWayMargin) {

    public TileCollisionShape {
        points = List.copyOf(points);
    }

    public static TileCollisionShape fullCell() {
        return new TileCollisionShape(List.of(
                new Vector2f(0.0f, 0.0f),
                new Vector2f(1.0f, 0.0f),
                new Vector2f(1.0f, 1.0f),
                new Vector2f(0.0f, 1.0f)), false, 0.0f);
    }

    public static TileCollisionShape slope(boolean risingToTheRight) {
        List<Vector2f> points = risingToTheRight
                ? List.of(new Vector2f(0.0f, 0.0f), new Vector2f(1.0f, 0.0f), new Vector2f(1.0f, 1.0f))
                : List.of(new Vector2f(0.0f, 0.0f), new Vector2f(1.0f, 0.0f), new Vector2f(0.0f, 1.0f));
        return new TileCollisionShape(points, false, 0.0f);
    }

    public static TileCollisionShape platform(float heightFraction) {
        float bottom = Math.clamp(1.0f - heightFraction, 0.0f, 1.0f);
        return new TileCollisionShape(List.of(
                new Vector2f(0.0f, bottom),
                new Vector2f(1.0f, bottom),
                new Vector2f(1.0f, 1.0f),
                new Vector2f(0.0f, 1.0f)), true, 0.0f);
    }

    public boolean valid() {
        return points.size() >= 3;
    }
}
