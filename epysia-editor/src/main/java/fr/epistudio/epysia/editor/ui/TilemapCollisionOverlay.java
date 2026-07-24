package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
import fr.epistudio.epysia.assets.epytilemap.TilemapSolidRectangles;
import fr.epistudio.epysia.components.transforms.Transform2D;
import imgui.ImDrawList;
import org.joml.Vector2f;

import java.util.List;

public final class TilemapCollisionOverlay {

    @FunctionalInterface
    public interface LocalProjection {
        Vector2f project(Transform2D transform, float localX, float localY);
    }

    private static final int COLOR_SOLID_RECTANGLE = 0xFF44DD44;
    private static final int COLOR_COLLISION_SHAPE = 0xFFFFDD00;
    private static final float LINE_THICKNESS = 1.5f;

    private TilemapCollisionOverlay() {
    }

    public static void draw(Transform2D transform, SpriteTilemap tilemap,
                            ImDrawList drawList, LocalProjection projection) {
        drawSolidRectangles(transform, tilemap, drawList, projection);
        drawCollisionShapes(transform, tilemap, drawList, projection);
    }

    private static void drawSolidRectangles(Transform2D transform, SpriteTilemap tilemap,
                                            ImDrawList drawList, LocalProjection projection) {
        for (TilemapSolidRectangles.TileRectangle rectangle : TilemapSolidRectangles.merge(tilemap)) {
            float left = rectangle.cellX() * tilemap.cellWidth();
            float right = (rectangle.cellX() + rectangle.widthCells()) * tilemap.cellWidth();
            float bottom = rectangle.cellY() * tilemap.cellHeight();
            float top = (rectangle.cellY() + rectangle.heightCells()) * tilemap.cellHeight();
            drawQuad(transform, drawList, projection, left, right, bottom, top);
        }
    }

    private static void drawQuad(Transform2D transform, ImDrawList drawList, LocalProjection projection,
                                 float left, float right, float bottom, float top) {
        Vector2f cornerA = projection.project(transform, left, bottom);
        Vector2f cornerB = projection.project(transform, right, bottom);
        Vector2f cornerC = projection.project(transform, right, top);
        Vector2f cornerD = projection.project(transform, left, top);
        drawList.addQuad(cornerA.x, cornerA.y, cornerB.x, cornerB.y,
                cornerC.x, cornerC.y, cornerD.x, cornerD.y, COLOR_SOLID_RECTANGLE, LINE_THICKNESS);
    }

    private static void drawCollisionShapes(Transform2D transform, SpriteTilemap tilemap,
                                            ImDrawList drawList, LocalProjection projection) {
        for (int cellY = 0; cellY < tilemap.height(); cellY++) {
            for (int cellX = 0; cellX < tilemap.width(); cellX++) {
                for (TileCollisionShape shape : tilemap.cellCollisionShapes(cellX, cellY)) {
                    drawShape(transform, tilemap, drawList, projection, shape, cellX, cellY);
                }
            }
        }
    }

    private static void drawShape(Transform2D transform, SpriteTilemap tilemap, ImDrawList drawList,
                                  LocalProjection projection, TileCollisionShape shape, int cellX, int cellY) {
        if (!shape.valid()) {
            return;
        }
        List<Vector2f> screenPoints = projectShape(transform, tilemap, projection, shape, cellX, cellY);
        for (int index = 0; index < screenPoints.size(); index++) {
            Vector2f start = screenPoints.get(index);
            Vector2f end = screenPoints.get((index + 1) % screenPoints.size());
            drawList.addLine(start.x, start.y, end.x, end.y, COLOR_COLLISION_SHAPE, LINE_THICKNESS);
        }
    }

    private static List<Vector2f> projectShape(Transform2D transform, SpriteTilemap tilemap,
                                               LocalProjection projection, TileCollisionShape shape,
                                               int cellX, int cellY) {
        float originX = cellX * tilemap.cellWidth();
        float originY = cellY * tilemap.cellHeight();
        return shape.points().stream()
                .map(point -> projection.project(transform,
                        originX + point.x * tilemap.cellWidth(),
                        originY + point.y * tilemap.cellHeight()))
                .toList();
    }
}
