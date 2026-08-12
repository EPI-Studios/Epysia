package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
import imgui.ImDrawList;
import org.joml.Vector2f;

import java.util.List;

public final class TileMarkerPainter {

    private static final float SOLID_INSET = 2.0f;
    private static final float SOLID_THICKNESS = 2.0f;
    private static final float SHAPE_THICKNESS = 1.4f;
    private static final float CORNER_INSET = 3.0f;
    private static final float TERRAIN_RADIUS = 3.0f;
    private static final float SCENE_RADIUS = 3.5f;
    private static final float NO_ROUNDING = 0.0f;
    private static final int NO_CORNER_FLAGS = 0;
    private static final int MINIMUM_POINTS = 3;
    private static final int COLOR_SOLID = 0xE03355FF;
    private static final int COLOR_SHAPE = 0xE0FFCC00;
    private static final int COLOR_TERRAIN = 0xE066DD44;
    private static final int COLOR_SCENE = 0xE0EE44FF;

    private TileMarkerPainter() {
    }

    public static void drawSolidMarker(ImDrawList drawList,
                                       float cellMinX, float cellMinY, float cellMaxX, float cellMaxY) {
        drawList.addRect(cellMinX + EditorScale.of(SOLID_INSET), cellMinY + EditorScale.of(SOLID_INSET),
                cellMaxX - EditorScale.of(SOLID_INSET), cellMaxY - EditorScale.of(SOLID_INSET),
                COLOR_SOLID, NO_ROUNDING, NO_CORNER_FLAGS, EditorScale.of(SOLID_THICKNESS));
    }

    public static void drawCollisionShapes(ImDrawList drawList, List<TileCollisionShape> shapes,
                                           float cellMinX, float cellMinY, float cellMaxX, float cellMaxY) {
        float width = cellMaxX - cellMinX;
        float height = cellMaxY - cellMinY;
        for (TileCollisionShape shape : shapes) {
            drawOutline(drawList, shape.points(), cellMinX, cellMaxY, width, height);
        }
    }

    public static void drawTerrainDot(ImDrawList drawList,
                                      float cellMinX, float cellMinY, float cellMaxX, float cellMaxY) {
        float centerX = cellMaxX - EditorScale.of(CORNER_INSET) - EditorScale.of(TERRAIN_RADIUS);
        float centerY = cellMinY + EditorScale.of(CORNER_INSET) + EditorScale.of(TERRAIN_RADIUS);
        drawList.addCircleFilled(centerX, centerY, EditorScale.of(TERRAIN_RADIUS), COLOR_TERRAIN);
    }

    public static void drawSceneMarker(ImDrawList drawList,
                                       float cellMinX, float cellMinY, float cellMaxX, float cellMaxY) {
        float centerX = cellMaxX - EditorScale.of(CORNER_INSET) - EditorScale.of(SCENE_RADIUS);
        float centerY = cellMaxY - EditorScale.of(CORNER_INSET) - EditorScale.of(SCENE_RADIUS);
        drawList.addQuadFilled(centerX, centerY - EditorScale.of(SCENE_RADIUS), centerX + EditorScale.of(SCENE_RADIUS), centerY,
                centerX, centerY + EditorScale.of(SCENE_RADIUS), centerX - EditorScale.of(SCENE_RADIUS), centerY, COLOR_SCENE);
    }

    private static void drawOutline(ImDrawList drawList, List<Vector2f> points,
                                    float cellMinX, float cellMaxY, float width, float height) {
        if (points.size() < MINIMUM_POINTS) {
            return;
        }
        for (int index = 0; index < points.size(); index++) {
            Vector2f start = points.get(index);
            Vector2f end = points.get((index + 1) % points.size());
            drawList.addLine(cellMinX + start.x * width, cellMaxY - start.y * height,
                    cellMinX + end.x * width, cellMaxY - end.y * height, COLOR_SHAPE, EditorScale.of(SHAPE_THICKNESS));
        }
    }
}
