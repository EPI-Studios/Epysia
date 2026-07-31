package fr.epistudio.epysia.render.baking;

import org.joml.Vector2f;
import org.joml.Vector3f;

public final class HemiOctahedralGrid {

    private static final float POLE_THRESHOLD = 0.999f;

    private HemiOctahedralGrid() {
    }

    public static Vector3f directionAt(int column, int row, int gridSize) {
        float divisor = Math.max(1, gridSize - 1);
        return decode(column / divisor, row / divisor);
    }

    public static Vector3f decode(float frameU, float frameV) {
        float horizontal = frameU * 2.0f - 1.0f;
        float vertical = frameV * 2.0f - 1.0f;
        float x = (horizontal - vertical) * 0.5f;
        float z = (horizontal + vertical) * 0.5f;
        float y = 1.0f - Math.abs(x) - Math.abs(z);
        return new Vector3f(x, y, z).normalize();
    }

    public static Vector2f encode(Vector3f direction) {
        Vector3f folded = new Vector3f(direction).normalize();
        float scale = Math.abs(folded.x) + Math.abs(folded.y) + Math.abs(folded.z);
        folded.div(scale);
        return new Vector2f((folded.x + folded.z) * 0.5f + 0.5f, (folded.z - folded.x) * 0.5f + 0.5f);
    }

    public static Vector3f referenceUp(Vector3f direction) {
        return Math.abs(direction.y) > POLE_THRESHOLD ? new Vector3f(0.0f, 0.0f, 1.0f) : new Vector3f(0.0f, 1.0f, 0.0f);
    }
}
