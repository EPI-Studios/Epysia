package fr.epistudio.epysia.physics.api;

public final class CollisionLayers {

    public static final int DEFAULT_LAYER_COUNT = 16;
    private static final int ALL_BITS = 0xFFFF;

    private final int[] masks;

    private CollisionLayers(int[] masks) {
        this.masks = masks;
    }

    public static CollisionLayers allColliding() {
        int[] masks = new int[DEFAULT_LAYER_COUNT];
        int all = (1 << DEFAULT_LAYER_COUNT) - 1;
        for (int index = 0; index < DEFAULT_LAYER_COUNT; index++) {
            masks[index] = all;
        }
        return new CollisionLayers(masks);
    }

    public static CollisionLayers from(int[] matrix) {
        return new CollisionLayers(matrix.clone());
    }

    public int layerCount() {
        return masks.length;
    }

    public int groupFor(int layer) {
        return 1 << clampLayer(layer);
    }

    public int maskFor(int layer) {
        if (layer < 0 || layer >= masks.length) {
            return ALL_BITS;
        }
        return masks[layer];
    }

    private int clampLayer(int layer) {
        if (layer < 0) {
            return 0;
        }
        if (layer >= masks.length) {
            return masks.length - 1;
        }
        return layer;
    }
}
