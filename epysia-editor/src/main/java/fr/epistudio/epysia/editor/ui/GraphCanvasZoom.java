package fr.epistudio.epysia.editor.ui;

final class GraphCanvasZoom {

    static final float MINIMUM_FACTOR = 0.3f;
    static final float MAXIMUM_FACTOR = 2.5f;
    static final float DEFAULT_FACTOR = 1.0f;
    static final float FIT_MARGIN = 40.0f;

    private static final float NOTCH_RATIO = 1.12f;
    private static final float MINIMUM_SPAN = 1.0f;
    private static final float DEFAULT_TOLERANCE = 0.001f;

    private float factor = DEFAULT_FACTOR;

    float factor() {
        return factor;
    }

    int percentage() {
        return Math.round(factor * 100.0f);
    }

    boolean atDefault() {
        return Math.abs(factor - DEFAULT_FACTOR) < DEFAULT_TOLERANCE;
    }

    void reset() {
        factor = DEFAULT_FACTOR;
    }

    void setFactor(float value) {
        factor = clampFactor(value);
    }

    void stepBy(float notches) {
        setFactor(factor * (float) Math.pow(NOTCH_RATIO, notches));
    }

    float scaled(float logical) {
        return logical * factor;
    }

    float unscaled(float scaledValue) {
        return scaledValue / factor;
    }

    static float clampFactor(float value) {
        return Math.clamp(value, MINIMUM_FACTOR, MAXIMUM_FACTOR);
    }

    static float logicalFromScreen(float screen, float origin, float panning, float factor) {
        return (screen - origin - panning) / factor;
    }

    static float screenFromLogical(float logical, float origin, float panning, float factor) {
        return origin + panning + logical * factor;
    }

    static float anchoredPanning(float cursor, float origin, float panning,
                                 float previousFactor, float newFactor) {
        float logical = logicalFromScreen(cursor, origin, panning, previousFactor);
        return cursor - origin - logical * newFactor;
    }

    static float fitFactor(float logicalSpanX, float logicalSpanY,
                           float viewportWidth, float viewportHeight) {
        float horizontal = usableExtent(viewportWidth) / Math.max(MINIMUM_SPAN, logicalSpanX);
        float vertical = usableExtent(viewportHeight) / Math.max(MINIMUM_SPAN, logicalSpanY);
        return clampFactor(Math.min(horizontal, vertical));
    }

    static float centeringPanning(float logicalCenter, float viewportExtent, float factor) {
        return viewportExtent * 0.5f - logicalCenter * factor;
    }

    private static float usableExtent(float viewportExtent) {
        return Math.max(MINIMUM_SPAN, viewportExtent - FIT_MARGIN * 2.0f);
    }
}
