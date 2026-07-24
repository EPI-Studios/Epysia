package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.SpriteRenderer;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SmartSnap {

    public record Guide(boolean vertical, float worldCoordinate, float spanStart, float spanEnd) {
    }

    public record Result(Vector2f correction, List<Guide> guides) {
    }

    private record Rect(float minX, float minY, float maxX, float maxY) {

        float[] verticalAnchors() {
            return new float[]{minX, (minX + maxX) * 0.5f, maxX};
        }

        float[] horizontalAnchors() {
            return new float[]{minY, (minY + maxY) * 0.5f, maxY};
        }
    }

    private SmartSnap() {
    }

    public static Result align(Scene scene, GameObject dragged, Transform2D transform, float tolerance) {
        Optional<Rect> movingRect = rectOf(dragged, transform);
        if (movingRect.isEmpty()) {
            return new Result(new Vector2f(), List.of());
        }
        List<Rect> targets = candidateRects(scene, dragged);
        List<Guide> guides = new ArrayList<>();
        float correctionX = bestOffset(movingRect.get().verticalAnchors(), targets, true, tolerance, guides,
                movingRect.get());
        float correctionY = bestOffset(movingRect.get().horizontalAnchors(), targets, false, tolerance, guides,
                movingRect.get());
        return new Result(new Vector2f(correctionX, correctionY), guides);
    }

    private static List<Rect> candidateRects(Scene scene, GameObject dragged) {
        List<Rect> rects = new ArrayList<>();
        for (GameObject gameObject : scene.gameObjects()) {
            if (gameObject == dragged) {
                continue;
            }
            Transform2D transform = gameObject.getComponentOrNull(Transform2D.class);
            if (transform != null) {
                rectOf(gameObject, transform).ifPresent(rects::add);
            }
        }
        return rects;
    }

    private static float bestOffset(float[] anchors, List<Rect> targets, boolean vertical, float tolerance,
                                    List<Guide> guides, Rect moving) {
        float bestDistance = tolerance;
        float bestOffset = 0.0f;
        float bestCoordinate = 0.0f;
        for (Rect target : targets) {
            for (float targetAnchor : vertical ? target.verticalAnchors() : target.horizontalAnchors()) {
                for (float anchor : anchors) {
                    float distance = Math.abs(targetAnchor - anchor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestOffset = targetAnchor - anchor;
                        bestCoordinate = targetAnchor;
                    }
                }
            }
        }
        if (bestOffset != 0.0f) {
            guides.add(guideFor(vertical, bestCoordinate, moving));
        }
        return bestOffset;
    }

    private static Guide guideFor(boolean vertical, float coordinate, Rect moving) {
        return vertical
                ? new Guide(true, coordinate, moving.minY(), moving.maxY())
                : new Guide(false, coordinate, moving.minX(), moving.maxX());
    }

    private static Optional<Rect> rectOf(GameObject gameObject, Transform2D transform) {
        Vector2f position = transform.position();
        Optional<Vector2f> halfExtents = halfExtentsOf(gameObject, transform);
        return halfExtents.map(extents -> new Rect(position.x - extents.x, position.y - extents.y,
                position.x + extents.x, position.y + extents.y));
    }

    private static Optional<Vector2f> halfExtentsOf(GameObject gameObject, Transform2D transform) {
        SpriteRenderer sprite = gameObject.getComponentOrNull(SpriteRenderer.class);
        if (sprite != null) {
            return Optional.of(new Vector2f(0.5f * Math.abs(transform.scale().x),
                    0.5f * Math.abs(transform.scale().y)));
        }
        TilemapRenderer tilemap = gameObject.getComponentOrNull(TilemapRenderer.class);
        if (tilemap != null) {
            return tilemap.tilemapValue().map(map -> new Vector2f(
                    map.width() * map.cellWidth() * 0.5f, map.height() * map.cellHeight() * 0.5f));
        }
        return Optional.of(new Vector2f());
    }
}
