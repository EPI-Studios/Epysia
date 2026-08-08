package fr.epistudio.epysia.ui;

public final class UiNineSlice {
    private UiNineSlice() {
    }

    public static void paint(UiPainter painter, UiRect rect, String texturePath, UiColor tint,
                             float[] uvBounds, float[] borders) {
        float[] columns = spans(rect.x(), rect.width(), borders[0], borders[1]);
        float[] rows = spans(rect.y(), rect.height(), borders[2], borders[3]);
        float[] uvColumns = uvSpans(uvBounds[0], uvBounds[2], borders[0], borders[1], rect.width());
        float[] uvRows = uvSpans(uvBounds[1], uvBounds[3], borders[2], borders[3], rect.height());
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                paintPatch(painter, texturePath, tint, columns, rows, uvColumns, uvRows, column, row);
            }
        }
    }

    private static void paintPatch(UiPainter painter, String texturePath, UiColor tint,
                                   float[] columns, float[] rows, float[] uvColumns, float[] uvRows,
                                   int column, int row) {
        float width = columns[column + 1] - columns[column];
        float height = rows[row + 1] - rows[row];
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        painter.drawImage(new UiRect(columns[column], rows[row], width, height), texturePath,
                uvColumns[column], uvRows[row], uvColumns[column + 1], uvRows[row + 1], tint);
    }

    private static float[] spans(float start, float size, float first, float second) {
        float clampedFirst = Math.min(first, size * 0.5f);
        float clampedSecond = Math.min(second, size * 0.5f);
        return new float[]{start, start + clampedFirst, start + size - clampedSecond, start + size};
    }

    private static float[] uvSpans(float start, float end, float first, float second, float size) {
        float range = end - start;
        float clampedFirst = Math.min(first, size * 0.5f);
        float clampedSecond = Math.min(second, size * 0.5f);
        float firstRatio = size <= 0.0f ? 0.0f : clampedFirst / size;
        float secondRatio = size <= 0.0f ? 0.0f : clampedSecond / size;
        return new float[]{start, start + range * firstRatio, end - range * secondRatio, end};
    }
}
