package fr.epistudio.epysia.render.backend;

public record StencilState(
        boolean enabled,
        StencilTest test,
        int reference,
        int compareMask,
        int writeMask,
        StencilOperation onStencilFail,
        StencilOperation onDepthFail,
        StencilOperation onPass
) {

    public static final int ALL_BITS = 0xFF;

    private static final StencilState DISABLED = new StencilState(false, StencilTest.ALWAYS, 0,
            ALL_BITS, ALL_BITS, StencilOperation.KEEP, StencilOperation.KEEP, StencilOperation.KEEP);

    public static StencilState disabled() {
        return DISABLED;
    }

    public static StencilState writing(int reference) {
        return new StencilState(true, StencilTest.ALWAYS, reference, ALL_BITS, ALL_BITS,
                StencilOperation.KEEP, StencilOperation.KEEP, StencilOperation.REPLACE);
    }

    public static StencilState keepingWhereEqual(int reference) {
        return new StencilState(true, StencilTest.EQUAL, reference, ALL_BITS, 0,
                StencilOperation.KEEP, StencilOperation.KEEP, StencilOperation.KEEP);
    }
}
