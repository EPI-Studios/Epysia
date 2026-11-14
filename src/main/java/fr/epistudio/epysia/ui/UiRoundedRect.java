package fr.epistudio.epysia.ui;

public final class UiRoundedRect {

    private static final int MINIMUM_SEGMENTS = 2;
    private static final int MAXIMUM_SEGMENTS = 24;
    private static final float PIXELS_PER_SEGMENT = 3.0f;

    private UiRoundedRect() {
    }

    public static int segmentsFor(float radiusInPixels) {
        int wanted = (int) Math.ceil(radiusInPixels / PIXELS_PER_SEGMENT);
        return Math.clamp(wanted, MINIMUM_SEGMENTS, MAXIMUM_SEGMENTS);
    }

    public static float clampRadius(UiRect rect, float radius) {
        float largest = Math.min(rect.width(), rect.height()) * 0.5f;
        return Math.clamp(radius, 0.0f, Math.max(largest, 0.0f));
    }

    public static UiRect inset(UiRect rect, float amount) {
        float width = Math.max(rect.width() - amount * 2.0f, 0.0f);
        float height = Math.max(rect.height() - amount * 2.0f, 0.0f);
        return new UiRect(rect.x() + amount, rect.y() + amount, width, height);
    }

    public static float[] path(UiRect rect, float radius, int segments) {
        float[] points = new float[4 * (segments + 1) * 2];
        float left = rect.x() + radius;
        float top = rect.y() + radius;
        float right = rect.x() + rect.width() - radius;
        float bottom = rect.y() + rect.height() - radius;
        int written = appendArc(points, 0, left, top, radius, Math.PI, segments);
        written = appendArc(points, written, right, top, radius, Math.PI * 1.5, segments);
        written = appendArc(points, written, right, bottom, radius, 0.0, segments);
        appendArc(points, written, left, bottom, radius, Math.PI * 0.5, segments);
        return points;
    }

    private static int appendArc(float[] points, int offset, float centerX, float centerY,
                                 float radius, double startAngle, int segments) {
        int cursor = offset;
        for (int step = 0; step <= segments; step++) {
            double angle = startAngle + (Math.PI * 0.5) * step / segments;
            points[cursor] = centerX + (float) Math.cos(angle) * radius;
            points[cursor + 1] = centerY + (float) Math.sin(angle) * radius;
            cursor += 2;
        }
        return cursor;
    }
}
