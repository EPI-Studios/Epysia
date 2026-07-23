package fr.epistudio.epysia.editor.gizmo;

import imgui.ImDrawList;

public final class GridOverlay2D {

    private static final float MINIMUM_LINE_SPACING_PIXELS = 8.0f;
    private static final float MINOR_THICKNESS = 1.0f;
    private static final float AXIS_THICKNESS = 2.0f;
    private static final int COLOR_MINOR = 0x24FFFFFF;
    private static final int COLOR_VERTICAL_AXIS = 0x8050B050;
    private static final int COLOR_GROUND = 0xB03C96E6;

    private GridOverlay2D() {
    }

    public static void draw(ImDrawList drawList, float imageX, float imageY, float width, float height,
                            float cameraX, float cameraY, float orthographicSize) {
        float pixelsPerUnit = height / (2.0f * orthographicSize);
        float step = spacingStep(pixelsPerUnit);
        drawVerticalLines(drawList, imageX, imageY, width, height, cameraX, pixelsPerUnit, step);
        drawHorizontalLines(drawList, imageX, imageY, width, height, cameraY, pixelsPerUnit, step);
    }

    private static float spacingStep(float pixelsPerUnit) {
        float step = 1.0f;
        while (step * pixelsPerUnit < MINIMUM_LINE_SPACING_PIXELS) {
            step *= 10.0f;
        }
        return step;
    }

    private static void drawVerticalLines(ImDrawList drawList, float imageX, float imageY,
                                          float width, float height, float cameraX,
                                          float pixelsPerUnit, float step) {
        float worldLeft = cameraX - width / (2.0f * pixelsPerUnit);
        float worldRight = cameraX + width / (2.0f * pixelsPerUnit);
        float start = (float) Math.floor(worldLeft / step) * step;
        for (float x = start; x <= worldRight; x += step) {
            float screenX = imageX + width * 0.5f + (x - cameraX) * pixelsPerUnit;
            boolean axis = Math.abs(x) < step * 0.5f;
            drawList.addLine(screenX, imageY, screenX, imageY + height,
                    axis ? COLOR_VERTICAL_AXIS : COLOR_MINOR,
                    axis ? AXIS_THICKNESS : MINOR_THICKNESS);
        }
    }

    private static void drawHorizontalLines(ImDrawList drawList, float imageX, float imageY,
                                            float width, float height, float cameraY,
                                            float pixelsPerUnit, float step) {
        float worldBottom = cameraY - height / (2.0f * pixelsPerUnit);
        float worldTop = cameraY + height / (2.0f * pixelsPerUnit);
        float start = (float) Math.floor(worldBottom / step) * step;
        for (float y = start; y <= worldTop; y += step) {
            float screenY = imageY + height * 0.5f - (y - cameraY) * pixelsPerUnit;
            boolean ground = Math.abs(y) < step * 0.5f;
            drawList.addLine(imageX, screenY, imageX + width, screenY,
                    ground ? COLOR_GROUND : COLOR_MINOR,
                    ground ? AXIS_THICKNESS : MINOR_THICKNESS);
        }
    }
}
