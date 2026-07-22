package fr.epistudio.epysia.editor.preview;

import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.GraphNode;
import fr.epistudio.epysia.graph.GraphValues;
import fr.epistudio.epysia.graph.vfx.VfxNodes;
import imgui.ImDrawList;
import imgui.ImGui;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class VfxShapeGizmo {

    private static final int COLOR_LINE = EditorStyle.rgba(130, 225, 175, 200);
    private static final float LINE_THICKNESS = 1.5f;
    private static final int CIRCLE_SEGMENTS = 48;
    private static final int CONE_RIBS = 8;
    private static final float CONE_PREVIEW_HEIGHT = 1.0f;
    private static final float DOT_CROSS_SIZE = 0.08f;
    private static final float FULL_CIRCLE_DEGREES = 360.0f;
    private static final float MINIMUM_CLIP_DEPTH = 0.0001f;

    private final GraphJsonCodec codec = new GraphJsonCodec();
    private final List<Segment> segments = new ArrayList<>();
    private final Vector4f clipStart = new Vector4f();
    private final Vector4f clipEnd = new Vector4f();
    private final Matrix4f worldViewProjection = new Matrix4f();

    private Path loadedPath = Path.of("");
    private long loadedStamp;
    private List<GraphNode> shapeNodes = List.of();

    void render(Path graphFile, Matrix4f viewProjection, Matrix4f emitterModel, PreviewRect rect) {
        reloadIfChanged(graphFile);
        if (shapeNodes.isEmpty()) {
            return;
        }
        segments.clear();
        for (GraphNode node : shapeNodes) {
            appendShape(node);
        }
        viewProjection.mul(emitterModel, worldViewProjection);
        ImDrawList drawList = ImGui.getWindowDrawList();
        for (Segment segment : segments) {
            drawSegment(drawList, segment, rect);
        }
    }

    private void reloadIfChanged(Path graphFile) {
        long stamp = fileStamp(graphFile);
        if (graphFile.equals(loadedPath) && stamp == loadedStamp) {
            return;
        }
        loadedPath = graphFile;
        loadedStamp = stamp;
        shapeNodes = readShapeNodes(graphFile);
    }

    private List<GraphNode> readShapeNodes(Path graphFile) {
        try {
            GraphAsset asset = codec.readFromFile(graphFile);
            return asset.nodesOfType(VfxNodes.SHAPE);
        } catch (IOException | RuntimeException failure) {
            return List.of();
        }
    }

    private static long fileStamp(Path graphFile) {
        try {
            return Files.getLastModifiedTime(graphFile).toMillis();
        } catch (IOException failure) {
            return 0L;
        }
    }

    private void drawSegment(ImDrawList drawList, Segment segment, PreviewRect rect) {
        worldViewProjection.transform(segment.start().x, segment.start().y, segment.start().z,
                1.0f, clipStart);
        worldViewProjection.transform(segment.end().x, segment.end().y, segment.end().z,
                1.0f, clipEnd);
        if (clipStart.w < MINIMUM_CLIP_DEPTH || clipEnd.w < MINIMUM_CLIP_DEPTH) {
            return;
        }
        drawList.addLine(rect.screenX(clipStart.x / clipStart.w), rect.screenY(clipStart.y / clipStart.w),
                rect.screenX(clipEnd.x / clipEnd.w), rect.screenY(clipEnd.y / clipEnd.w),
                COLOR_LINE, LINE_THICKNESS);
    }

    private void appendShape(GraphNode node) {
        float radius = number(node, VfxNodes.RADIUS_SETTING, 1.0f);
        float arc = number(node, VfxNodes.ARC_SETTING, FULL_CIRCLE_DEGREES);
        switch (shapeOf(node)) {
            case VfxNodes.SHAPE_SPHERE -> appendSphere(radius);
            case VfxNodes.SHAPE_HEMISPHERE -> appendHemisphere(radius);
            case VfxNodes.SHAPE_BOX -> appendBox(node);
            case VfxNodes.SHAPE_CIRCLE -> appendArc(radius, arc, 0.0f);
            case VfxNodes.SHAPE_CYLINDER -> appendCylinder(node, radius, arc);
            case VfxNodes.SHAPE_DOT -> appendDot();
            case VfxNodes.SHAPE_EDGE -> appendEdge(node);
            default -> appendCone(node, radius, arc);
        }
    }

    private static String shapeOf(GraphNode node) {
        return GraphValues.asString(node.values()
                .getOrDefault(VfxNodes.SHAPE_SETTING, VfxNodes.SHAPE_CONE));
    }

    private static float number(GraphNode node, String key, float fallback) {
        return GraphValues.asFloat(node.values().getOrDefault(key, fallback));
    }

    private void appendCone(GraphNode node, float radius, float arc) {
        float angle = number(node, VfxNodes.ANGLE_SETTING, 25.0f);
        float topRadius = radius + CONE_PREVIEW_HEIGHT * (float) Math.tan(Math.toRadians(angle));
        appendArc(radius, arc, 0.0f);
        appendArc(topRadius, arc, CONE_PREVIEW_HEIGHT);
        for (int index = 0; index < CONE_RIBS; index++) {
            float radians = (float) Math.toRadians(arc * index / (float) CONE_RIBS);
            segments.add(new Segment(
                    new Vector3f(radius * (float) Math.cos(radians), 0.0f, radius * (float) Math.sin(radians)),
                    new Vector3f(topRadius * (float) Math.cos(radians), CONE_PREVIEW_HEIGHT,
                            topRadius * (float) Math.sin(radians))));
        }
    }

    private void appendSphere(float radius) {
        appendArc(radius, FULL_CIRCLE_DEGREES, 0.0f);
        appendVerticalRing(radius, 0.0f, FULL_CIRCLE_DEGREES);
        appendVerticalRing(radius, 90.0f, FULL_CIRCLE_DEGREES);
    }

    private void appendHemisphere(float radius) {
        appendArc(radius, FULL_CIRCLE_DEGREES, 0.0f);
        appendVerticalRing(radius, 0.0f, 180.0f);
        appendVerticalRing(radius, 90.0f, 180.0f);
    }

    private void appendVerticalRing(float radius, float yawDegrees, float sweepDegrees) {
        float yaw = (float) Math.toRadians(yawDegrees);
        for (int index = 0; index < CIRCLE_SEGMENTS; index++) {
            float first = (float) Math.toRadians(sweepDegrees * index / (float) CIRCLE_SEGMENTS);
            float second = (float) Math.toRadians(sweepDegrees * (index + 1) / (float) CIRCLE_SEGMENTS);
            segments.add(new Segment(ringPoint(radius, yaw, first), ringPoint(radius, yaw, second)));
        }
    }

    private static Vector3f ringPoint(float radius, float yaw, float radians) {
        float horizontal = radius * (float) Math.cos(radians);
        return new Vector3f(horizontal * (float) Math.cos(yaw), radius * (float) Math.sin(radians),
                horizontal * (float) Math.sin(yaw));
    }

    private void appendArc(float radius, float arcDegrees, float height) {
        for (int index = 0; index < CIRCLE_SEGMENTS; index++) {
            float first = (float) Math.toRadians(arcDegrees * index / (float) CIRCLE_SEGMENTS);
            float second = (float) Math.toRadians(arcDegrees * (index + 1) / (float) CIRCLE_SEGMENTS);
            segments.add(new Segment(
                    new Vector3f(radius * (float) Math.cos(first), height, radius * (float) Math.sin(first)),
                    new Vector3f(radius * (float) Math.cos(second), height, radius * (float) Math.sin(second))));
        }
    }

    private void appendCylinder(GraphNode node, float radius, float arc) {
        float halfHeight = number(node, VfxNodes.HEIGHT_SETTING, 1.0f) * 0.5f;
        appendArc(radius, arc, -halfHeight);
        appendArc(radius, arc, halfHeight);
        for (int index = 0; index < CONE_RIBS; index++) {
            float radians = (float) Math.toRadians(arc * index / (float) CONE_RIBS);
            float x = radius * (float) Math.cos(radians);
            float z = radius * (float) Math.sin(radians);
            segments.add(new Segment(new Vector3f(x, -halfHeight, z), new Vector3f(x, halfHeight, z)));
        }
    }

    private void appendBox(GraphNode node) {
        float x = number(node, VfxNodes.HALF_EXTENTS_X_SETTING, 0.5f);
        float y = number(node, VfxNodes.HALF_EXTENTS_Y_SETTING, 0.5f);
        float z = number(node, VfxNodes.HALF_EXTENTS_Z_SETTING, 0.5f);
        appendBoxFace(x, y, z, -y);
        appendBoxFace(x, y, z, y);
        for (int corner = 0; corner < 4; corner++) {
            float cornerX = (corner == 0 || corner == 3) ? -x : x;
            float cornerZ = corner < 2 ? -z : z;
            segments.add(new Segment(new Vector3f(cornerX, -y, cornerZ), new Vector3f(cornerX, y, cornerZ)));
        }
    }

    private void appendBoxFace(float x, float y, float z, float height) {
        segments.add(new Segment(new Vector3f(-x, height, -z), new Vector3f(x, height, -z)));
        segments.add(new Segment(new Vector3f(x, height, -z), new Vector3f(x, height, z)));
        segments.add(new Segment(new Vector3f(x, height, z), new Vector3f(-x, height, z)));
        segments.add(new Segment(new Vector3f(-x, height, z), new Vector3f(-x, height, -z)));
    }

    private void appendDot() {
        segments.add(new Segment(new Vector3f(-DOT_CROSS_SIZE, 0.0f, 0.0f),
                new Vector3f(DOT_CROSS_SIZE, 0.0f, 0.0f)));
        segments.add(new Segment(new Vector3f(0.0f, -DOT_CROSS_SIZE, 0.0f),
                new Vector3f(0.0f, DOT_CROSS_SIZE, 0.0f)));
        segments.add(new Segment(new Vector3f(0.0f, 0.0f, -DOT_CROSS_SIZE),
                new Vector3f(0.0f, 0.0f, DOT_CROSS_SIZE)));
    }

    private void appendEdge(GraphNode node) {
        float half = number(node, VfxNodes.EDGE_LENGTH_SETTING, 1.0f) * 0.5f;
        segments.add(new Segment(new Vector3f(-half, 0.0f, 0.0f), new Vector3f(half, 0.0f, 0.0f)));
    }

    private record Segment(Vector3f start, Vector3f end) {
    }

    record PreviewRect(float minX, float minY, float width, float height) {

        float screenX(float normalizedX) {
            return minX + (normalizedX * 0.5f + 0.5f) * width;
        }

        float screenY(float normalizedY) {
            return minY + (0.5f - normalizedY * 0.5f) * height;
        }
    }
}
