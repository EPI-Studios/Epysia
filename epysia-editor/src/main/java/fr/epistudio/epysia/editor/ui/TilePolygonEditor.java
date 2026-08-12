package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.assets.epytilemap.TileCollisionShape;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TilePolygonEditor {

    private static final String CANVAS_IDENTIFIER = "##tile-polygon-editor";
    private static final float MINIMUM_SIZE = 48.0f;
    private static final float VERTEX_RADIUS = 3.5f;
    private static final float VERTEX_ACTIVE_BONUS = 1.5f;
    private static final float VERTEX_HIT_RADIUS = 7.0f;
    private static final float EDGE_HIT_DISTANCE = 5.0f;
    private static final float OUTLINE_THICKNESS = 1.6f;
    private static final float GRID_THICKNESS = 1.0f;
    private static final int MINIMUM_POINTS = 3;
    private static final int GRID_DIVISIONS = 4;
    private static final int MAXIMUM_GRID_LINES = 32;
    private static final int NONE = -1;
    private static final int COLOR_OUTLINE = 0xE0FFCC00;
    private static final int COLOR_FILL = 0x40FFCC00;
    private static final int COLOR_VERTEX = 0xFFFFFFFF;
    private static final int COLOR_VERTEX_ACTIVE = 0xFF3399FF;
    private static final int COLOR_GRID = 0x3AFFFFFF;
    private static final int COLOR_BORDER = 0x66FFFFFF;

    private int activeSnapDivisions;
    private int dragShapeIndex = NONE;
    private int dragPointIndex = NONE;

    public TilePolygonEditor() {
    }

    public boolean render(TileData data, int previewTextureId,
                          float minU, float minV, float maxU, float maxV, float size) {
        return render(data, previewTextureId, minU, minV, maxU, maxV, size, 0);
    }

    public boolean render(TileData data, int previewTextureId,
                          float minU, float minV, float maxU, float maxV, float size, int snapDivisions) {
        activeSnapDivisions = Math.max(0, snapDivisions);
        float extent = Math.max(EditorScale.of(MINIMUM_SIZE), size);
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        ImGui.image(previewTextureId, extent, extent, minU, minV, maxU, maxV);
        ImGui.setCursorScreenPos(originX, originY);
        ImGui.invisibleButton(CANVAS_IDENTIFIER, extent, extent);
        CellCanvas canvas = new CellCanvas(originX, originY, extent);
        boolean changed = handleInput(canvas, data);
        draw(canvas, data);
        return changed;
    }

    private boolean handleInput(CellCanvas canvas, TileData data) {
        boolean hovered = ImGui.isItemHovered();
        boolean changed = ImGui.isItemActivated() && beginDrag(canvas, data);
        if (ImGui.isItemActive()) {
            changed |= applyDrag(canvas, data);
        }
        if (ImGui.isItemDeactivated()) {
            clearDrag();
        }
        return changed | handleRemove(canvas, data, hovered);
    }

    private boolean beginDrag(CellCanvas canvas, TileData data) {
        Optional<VertexReference> vertex = vertexAt(canvas, data);
        if (vertex.isPresent()) {
            dragShapeIndex = vertex.get().shapeIndex();
            dragPointIndex = vertex.get().pointIndex();
            return false;
        }
        return insertVertex(canvas, data);
    }

    private boolean insertVertex(CellCanvas canvas, TileData data) {
        Optional<EdgeHit> located = edgeAt(canvas, data);
        if (located.isEmpty()) {
            return false;
        }
        EdgeHit edge = located.get();
        TileCollisionShape shape = data.collisionShapes().get(edge.shapeIndex());
        List<Vector2f> points = new ArrayList<>(shape.points());
        points.add(edge.insertIndex(), new Vector2f(edge.unitX(), edge.unitY()));
        data.replaceCollisionShape(edge.shapeIndex(), reshape(shape, points));
        dragShapeIndex = edge.shapeIndex();
        dragPointIndex = edge.insertIndex();
        return true;
    }

    private boolean applyDrag(CellCanvas canvas, TileData data) {
        Optional<TileCollisionShape> dragged = draggedShape(data);
        if (dragged.isEmpty()) {
            return false;
        }
        TileCollisionShape shape = dragged.get();
        Vector2f target = pointerUnit(canvas);
        Vector2f current = shape.points().get(dragPointIndex);
        if (current.x == target.x && current.y == target.y) {
            return false;
        }
        List<Vector2f> points = new ArrayList<>(shape.points());
        points.set(dragPointIndex, target);
        data.replaceCollisionShape(dragShapeIndex, reshape(shape, points));
        return true;
    }

    private boolean handleRemove(CellCanvas canvas, TileData data, boolean hovered) {
        if (!hovered || !ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            return false;
        }
        Optional<VertexReference> vertex = vertexAt(canvas, data);
        if (vertex.isEmpty()) {
            return false;
        }
        TileCollisionShape shape = data.collisionShapes().get(vertex.get().shapeIndex());
        if (shape.points().size() <= MINIMUM_POINTS) {
            return false;
        }
        List<Vector2f> points = new ArrayList<>(shape.points());
        points.remove(vertex.get().pointIndex());
        data.replaceCollisionShape(vertex.get().shapeIndex(), reshape(shape, points));
        clearDrag();
        return true;
    }

    private Optional<TileCollisionShape> draggedShape(TileData data) {
        List<TileCollisionShape> shapes = data.collisionShapes();
        if (dragShapeIndex < 0 || dragShapeIndex >= shapes.size()) {
            return Optional.empty();
        }
        TileCollisionShape shape = shapes.get(dragShapeIndex);
        boolean addressable = dragPointIndex >= 0 && dragPointIndex < shape.points().size();
        return addressable ? Optional.of(shape) : Optional.empty();
    }

    private void clearDrag() {
        dragShapeIndex = NONE;
        dragPointIndex = NONE;
    }

    private static TileCollisionShape reshape(TileCollisionShape shape, List<Vector2f> points) {
        return new TileCollisionShape(points, shape.oneWay(), shape.oneWayMargin());
    }

    private int gridDivisions() {
        if (activeSnapDivisions <= 0) {
            return GRID_DIVISIONS;
        }
        return Math.min(activeSnapDivisions, MAXIMUM_GRID_LINES);
    }

    private Vector2f pointerUnit(CellCanvas canvas) {
        float unitX = Math.clamp(canvas.unitX(ImGui.getMousePosX()), 0.0f, 1.0f);
        float unitY = Math.clamp(canvas.unitY(ImGui.getMousePosY()), 0.0f, 1.0f);
        if (activeSnapDivisions <= 0 || ImGui.getIO().getKeyShift()) {
            return new Vector2f(unitX, unitY);
        }
        return new Vector2f(snapped(unitX, activeSnapDivisions), snapped(unitY, activeSnapDivisions));
    }

    private static float snapped(float value, int divisions) {
        float step = 1.0f / divisions;
        return Math.clamp(Math.round(value / step) * step, 0.0f, 1.0f);
    }

    private static Optional<VertexReference> vertexAt(CellCanvas canvas, TileData data) {
        List<TileCollisionShape> shapes = data.collisionShapes();
        for (int shapeIndex = 0; shapeIndex < shapes.size(); shapeIndex++) {
            List<Vector2f> points = shapes.get(shapeIndex).points();
            for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                Vector2f point = points.get(pointIndex);
                if (withinRadius(canvas.screenX(point.x), canvas.screenY(point.y), EditorScale.of(VERTEX_HIT_RADIUS))) {
                    return Optional.of(new VertexReference(shapeIndex, pointIndex));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean withinRadius(float screenX, float screenY, float radius) {
        float deltaX = ImGui.getMousePosX() - screenX;
        float deltaY = ImGui.getMousePosY() - screenY;
        return deltaX * deltaX + deltaY * deltaY <= radius * radius;
    }

    private static Optional<EdgeHit> edgeAt(CellCanvas canvas, TileData data) {
        List<TileCollisionShape> shapes = data.collisionShapes();
        Optional<EdgeHit> closest = Optional.empty();
        for (int shapeIndex = 0; shapeIndex < shapes.size(); shapeIndex++) {
            Optional<EdgeHit> candidate = edgeOfShape(canvas, shapes.get(shapeIndex).points(), shapeIndex);
            closest = nearer(closest, candidate);
        }
        return closest;
    }

    private static Optional<EdgeHit> edgeOfShape(CellCanvas canvas, List<Vector2f> points, int shapeIndex) {
        Optional<EdgeHit> closest = Optional.empty();
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            Vector2f start = points.get(pointIndex);
            Vector2f end = points.get((pointIndex + 1) % points.size());
            closest = nearer(closest, projectOntoEdge(canvas, start, end, shapeIndex, pointIndex + 1));
        }
        return closest;
    }

    private static Optional<EdgeHit> nearer(Optional<EdgeHit> current, Optional<EdgeHit> candidate) {
        if (candidate.isEmpty()) {
            return current;
        }
        if (current.isEmpty() || candidate.get().distance() < current.get().distance()) {
            return candidate;
        }
        return current;
    }

    private static Optional<EdgeHit> projectOntoEdge(CellCanvas canvas, Vector2f start, Vector2f end,
                                                     int shapeIndex, int insertIndex) {
        float startX = canvas.screenX(start.x);
        float startY = canvas.screenY(start.y);
        float edgeX = canvas.screenX(end.x) - startX;
        float edgeY = canvas.screenY(end.y) - startY;
        float lengthSquared = edgeX * edgeX + edgeY * edgeY;
        if (lengthSquared <= 0.0f) {
            return Optional.empty();
        }
        float projection = (ImGui.getMousePosX() - startX) * edgeX + (ImGui.getMousePosY() - startY) * edgeY;
        float alongEdge = Math.clamp(projection / lengthSquared, 0.0f, 1.0f);
        float closestX = startX + alongEdge * edgeX;
        float closestY = startY + alongEdge * edgeY;
        float distance = (float) Math.hypot(ImGui.getMousePosX() - closestX, ImGui.getMousePosY() - closestY);
        if (distance > EditorScale.of(EDGE_HIT_DISTANCE)) {
            return Optional.empty();
        }
        return Optional.of(new EdgeHit(shapeIndex, insertIndex,
                canvas.unitX(closestX), canvas.unitY(closestY), distance));
    }

    private void draw(CellCanvas canvas, TileData data) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawGrid(drawList, canvas);
        List<TileCollisionShape> shapes = data.collisionShapes();
        for (int shapeIndex = 0; shapeIndex < shapes.size(); shapeIndex++) {
            drawShape(drawList, canvas, shapes.get(shapeIndex), shapeIndex);
        }
    }

    private void drawGrid(ImDrawList drawList, CellCanvas canvas) {
        float maxX = canvas.originX() + canvas.size();
        float maxY = canvas.originY() + canvas.size();
        int divisions = gridDivisions();
        for (int division = 1; division < divisions; division++) {
            float offset = division / (float) divisions * canvas.size();
            drawList.addLine(canvas.originX() + offset, canvas.originY(),
                    canvas.originX() + offset, maxY, COLOR_GRID, EditorScale.of(GRID_THICKNESS));
            drawList.addLine(canvas.originX(), canvas.originY() + offset,
                    maxX, canvas.originY() + offset, COLOR_GRID, EditorScale.of(GRID_THICKNESS));
        }
        drawList.addRect(canvas.originX(), canvas.originY(), maxX, maxY, COLOR_BORDER);
    }

    private void drawShape(ImDrawList drawList, CellCanvas canvas, TileCollisionShape shape, int shapeIndex) {
        List<Vector2f> points = shape.points();
        drawInterior(drawList, canvas, points);
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            Vector2f start = points.get(pointIndex);
            Vector2f end = points.get((pointIndex + 1) % points.size());
            drawList.addLine(canvas.screenX(start.x), canvas.screenY(start.y),
                    canvas.screenX(end.x), canvas.screenY(end.y), COLOR_OUTLINE, EditorScale.of(OUTLINE_THICKNESS));
        }
        drawVertices(drawList, canvas, points, shapeIndex);
    }

    private static void drawInterior(ImDrawList drawList, CellCanvas canvas, List<Vector2f> points) {
        Vector2f anchor = points.get(0);
        for (int index = 1; index + 1 < points.size(); index++) {
            Vector2f second = points.get(index);
            Vector2f third = points.get(index + 1);
            drawList.addTriangleFilled(canvas.screenX(anchor.x), canvas.screenY(anchor.y),
                    canvas.screenX(second.x), canvas.screenY(second.y),
                    canvas.screenX(third.x), canvas.screenY(third.y), COLOR_FILL);
        }
    }

    private void drawVertices(ImDrawList drawList, CellCanvas canvas, List<Vector2f> points, int shapeIndex) {
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            Vector2f point = points.get(pointIndex);
            boolean active = shapeIndex == dragShapeIndex && pointIndex == dragPointIndex;
            float radius = active ? EditorScale.of(VERTEX_RADIUS) + EditorScale.of(VERTEX_ACTIVE_BONUS) : EditorScale.of(VERTEX_RADIUS);
            drawList.addCircleFilled(canvas.screenX(point.x), canvas.screenY(point.y), radius,
                    active ? COLOR_VERTEX_ACTIVE : COLOR_VERTEX);
        }
    }

    private record VertexReference(int shapeIndex, int pointIndex) {
    }

    private record EdgeHit(int shapeIndex, int insertIndex, float unitX, float unitY, float distance) {
    }

    private record CellCanvas(float originX, float originY, float size) {

        float screenX(float unitX) {
            return originX + unitX * size;
        }

        float screenY(float unitY) {
            return originY + (1.0f - unitY) * size;
        }

        float unitX(float screenX) {
            return (screenX - originX) / size;
        }

        float unitY(float screenY) {
            return 1.0f - (screenY - originY) / size;
        }
    }
}
