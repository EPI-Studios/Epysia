package fr.epistudio.epysia.editor.gizmo;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImDrawList;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class NavMeshOverlay {

    public record ScreenRect(float x, float y, int width, int height) {
    }

    private static final int FILL_COLOR = EditorStyle.rgba(90, 200, 255, 60);
    private static final int EDGE_COLOR = EditorStyle.rgba(120, 220, 255, 190);
    private static final float EDGE_THICKNESS = 1.0f;
    private static final float NEAR_CLIP = 1.0e-4f;
    private static final int FLOATS_PER_TRIANGLE = 9;

    private final float[] projected = new float[2];
    private final float[] screenX = new float[3];
    private final float[] screenY = new float[3];

    public void render(float[] triangleVertices, Matrix4f viewProjection, ImDrawList drawList,
                       ScreenRect rect) {
        if (triangleVertices.length < FLOATS_PER_TRIANGLE) {
            return;
        }
        for (int offset = 0; offset + FLOATS_PER_TRIANGLE <= triangleVertices.length;
             offset += FLOATS_PER_TRIANGLE) {
            renderTriangle(triangleVertices, offset, viewProjection, drawList, rect);
        }
    }

    private void renderTriangle(float[] vertices, int offset, Matrix4f viewProjection,
                                ImDrawList drawList, ScreenRect rect) {
        for (int index = 0; index < 3; index++) {
            int base = offset + index * 3;
            if (!project(vertices[base], vertices[base + 1], vertices[base + 2], viewProjection, rect)) {
                return;
            }
            screenX[index] = projected[0];
            screenY[index] = projected[1];
        }
        drawList.addTriangleFilled(screenX[0], screenY[0], screenX[1], screenY[1],
                screenX[2], screenY[2], FILL_COLOR);
        drawList.addTriangle(screenX[0], screenY[0], screenX[1], screenY[1],
                screenX[2], screenY[2], EDGE_COLOR, EditorScale.ofAtLeastOne(EDGE_THICKNESS));
    }

    private boolean project(float worldX, float worldY, float worldZ, Matrix4f viewProjection,
                            ScreenRect rect) {
        Vector4f clip = viewProjection.transform(new Vector4f(worldX, worldY, worldZ, 1.0f));
        if (clip.w <= NEAR_CLIP) {
            return false;
        }
        projected[0] = rect.x() + (clip.x / clip.w * 0.5f + 0.5f) * rect.width();
        projected[1] = rect.y() + (0.5f - clip.y / clip.w * 0.5f) * rect.height();
        return true;
    }
}
