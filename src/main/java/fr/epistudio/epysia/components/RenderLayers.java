package fr.epistudio.epysia.components;

public final class RenderLayers {

    public static final int ALL = 0xFFFFFFFF;
    public static final int NONE = 0;
    public static final int DEFAULT = 1;
    public static final int COUNT = 32;

    private RenderLayers() {
    }

    public static boolean intersects(int layerMask, int cullMask) {
        return (layerMask & cullMask) != 0;
    }

    public static int withLayer(int mask, int layer) {
        return mask | bit(layer);
    }

    public static int withoutLayer(int mask, int layer) {
        return mask & ~bit(layer);
    }

    public static boolean hasLayer(int mask, int layer) {
        return (mask & bit(layer)) != 0;
    }

    private static int bit(int layer) {
        if (layer < 0 || layer >= COUNT) {
            throw new IllegalArgumentException("Render layer must be in [0, " + COUNT + "): " + layer);
        }
        return 1 << layer;
    }
}
