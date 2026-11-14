package fr.epistudio.epysia.ui;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiWorldPlacementTest {

    private static final float TOLERANCE = 1.0e-2f;

    @Test
    void aCursorOverACanvasPixelFindsThatPixel() {
        Matrix4f viewProjection = new Matrix4f().perspective((float) Math.toRadians(60.0), 16.0f / 9.0f, 0.1f, 100.0f)
                .lookAt(2.0f, 1.5f, 6.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        Matrix4f canvasToWorld = new Matrix4f().translate(0.0f, 1.0f, 0.0f).rotateY(0.4f)
                .scale(0.01f, -0.01f, 1.0f).translate(-200.0f, -100.0f, 0.0f);
        Vector4f clip = new Matrix4f(viewProjection).mul(canvasToWorld).transform(new Vector4f(310.0f, 42.0f, 0.0f, 1.0f));

        Vector2f found = UiWorldPlacement.canvasPointUnderCursor(canvasToWorld, viewProjection,
                clip.x() / clip.w(), clip.y() / clip.w()).orElseThrow();

        assertEquals(310.0f, found.x(), TOLERANCE);
        assertEquals(42.0f, found.y(), TOLERANCE);
    }

    @Test
    void aCursorParallelToTheCanvasMisses() {
        Matrix4f viewProjection = new Matrix4f().perspective(1.0f, 1.0f, 0.1f, 100.0f)
                .lookAt(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        Matrix4f canvasToWorld = new Matrix4f().translate(0.0f, -5.0f, 0.0f).rotateX((float) Math.toRadians(90.0));

        assertTrue(UiWorldPlacement.canvasPointUnderCursor(canvasToWorld, viewProjection, 0.0f, 0.0f).isEmpty());
    }
}
