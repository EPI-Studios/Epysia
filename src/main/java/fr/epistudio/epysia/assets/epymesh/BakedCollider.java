package fr.epistudio.epysia.assets.epymesh;

public record BakedCollider(
        float[] triangleVertices,
        int[] triangleIndices,
        float[] convexVertices
) {

    public static BakedCollider triangleMesh(float[] triangleVertices, int[] triangleIndices) {
        return new BakedCollider(triangleVertices, triangleIndices, new float[0]);
    }

    public static BakedCollider convexHull(float[] convexVertices) {
        return new BakedCollider(new float[0], new int[0], convexVertices);
    }

    public boolean hasConvex() {
        return convexVertices.length > 0;
    }

    public boolean hasTriangleMesh() {
        return triangleVertices.length > 0;
    }
}
