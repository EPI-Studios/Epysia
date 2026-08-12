package fr.epistudio.epysia.navigation;

public final class NavPrimitives {

    public static final int[] BOX_INDICES = {
            0, 1, 2, 0, 2, 3, 4, 6, 5, 4, 7, 6,
            0, 4, 5, 0, 5, 1, 3, 2, 6, 3, 6, 7,
            0, 3, 7, 0, 7, 4, 1, 5, 6, 1, 6, 2};

    private NavPrimitives() {
    }

    public static float[] boxCorners(float halfWidth, float halfHeight, float halfDepth) {
        return new float[]{
                -halfWidth, -halfHeight, -halfDepth, halfWidth, -halfHeight, -halfDepth,
                halfWidth, -halfHeight, halfDepth, -halfWidth, -halfHeight, halfDepth,
                -halfWidth, halfHeight, -halfDepth, halfWidth, halfHeight, -halfDepth,
                halfWidth, halfHeight, halfDepth, -halfWidth, halfHeight, halfDepth};
    }
}
