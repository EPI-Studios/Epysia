package fr.epistudio.epysia.render.sprite;

public final class SpriteSortKeys {

    public static final long KIND_TILEMAP = 0L;
    public static final long KIND_SPRITE = 1L;

    private static final long LAYER_BIAS = 32768L;
    private static final long HALF_WORD_MASK = 0xFFFFL;
    private static final long KIND_MASK = 0x1L;
    private static final long SEQUENCE_MASK = 0x7FFFFFFFL;
    private static final int LAYER_SHIFT = 48;
    private static final int ORDER_SHIFT = 32;
    private static final int KIND_SHIFT = 31;

    private SpriteSortKeys() {
    }

    public static long compose(int sortingLayer, int orderInLayer, long kind, long sequence) {
        long layerBits = (sortingLayer + LAYER_BIAS) & HALF_WORD_MASK;
        long orderBits = (orderInLayer + LAYER_BIAS) & HALF_WORD_MASK;
        return (layerBits << LAYER_SHIFT) | (orderBits << ORDER_SHIFT)
                | ((kind & KIND_MASK) << KIND_SHIFT) | (sequence & SEQUENCE_MASK);
    }
}
