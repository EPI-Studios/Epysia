package fr.epistudio.epysia.render.sprite;

public final class SpriteSortKeys {

    private static final long LAYER_BIAS = 32768L;
    private static final long HALF_WORD_MASK = 0xFFFFL;
    private static final long SEQUENCE_MASK = 0xFFFFFFFFL;
    private static final int LAYER_SHIFT = 48;
    private static final int ORDER_SHIFT = 32;

    private SpriteSortKeys() {
    }

    public static long compose(int sortingLayer, int orderInLayer, long sequence) {
        long layerBits = (sortingLayer + LAYER_BIAS) & HALF_WORD_MASK;
        long orderBits = (orderInLayer + LAYER_BIAS) & HALF_WORD_MASK;
        return (layerBits << LAYER_SHIFT) | (orderBits << ORDER_SHIFT) | (sequence & SEQUENCE_MASK);
    }
}
