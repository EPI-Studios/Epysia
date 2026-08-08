package fr.epistudio.epysia.ui;

import java.util.ArrayList;
import java.util.List;

public final class UiTextLayout {
    public static final float LINE_SPACING = 1.2f;

    private UiTextLayout() {
    }

    public static List<String> lines(UiPainter painter, String text, UiFontStyle style,
                                     float maxWidth, UiAutowrap autowrap) {
        List<String> result = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (autowrap == UiAutowrap.OFF || maxWidth <= 0.0f) {
                result.add(paragraph);
                continue;
            }
            result.addAll(wrapParagraph(painter, paragraph, style, maxWidth, autowrap));
        }
        return result;
    }

    private static List<String> wrapParagraph(UiPainter painter, String paragraph, UiFontStyle style,
                                              float maxWidth, UiAutowrap autowrap) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces(paragraph, autowrap)) {
            String candidate = current + piece;
            if (!current.isEmpty() && painter.measureTextWidth(candidate.strip(), style) > maxWidth) {
                lines.add(current.toString().stripTrailing());
                current.setLength(0);
                current.append(piece.stripLeading());
                continue;
            }
            current.append(piece);
        }
        lines.add(current.toString().stripTrailing());
        return lines;
    }

    private static List<String> pieces(String paragraph, UiAutowrap autowrap) {
        List<String> pieces = new ArrayList<>();
        if (autowrap == UiAutowrap.ARBITRARY) {
            for (int index = 0; index < paragraph.length(); index++) {
                pieces.add(String.valueOf(paragraph.charAt(index)));
            }
            return pieces;
        }
        int start = 0;
        for (int index = 0; index < paragraph.length(); index++) {
            if (paragraph.charAt(index) == ' ') {
                pieces.add(paragraph.substring(start, index + 1));
                start = index + 1;
            }
        }
        pieces.add(paragraph.substring(start));
        return pieces;
    }

    public static void draw(UiPainter painter, UiRect rect, String text, UiFontStyle style, UiColor color,
                            UiTextAlignment horizontal, UiVerticalAlignment vertical, UiAutowrap autowrap) {
        List<String> lines = lines(painter, text, style, rect.width(), autowrap);
        float lineHeight = painter.lineHeight(style) * LINE_SPACING;
        float blockHeight = lineHeight * lines.size();
        float y = rect.y() + verticalOffset(vertical, rect.height(), blockHeight);
        for (String line : lines) {
            float width = painter.measureTextWidth(line, style);
            painter.drawText(rect.x() + horizontalOffset(horizontal, rect.width(), width), y,
                    line, style, color);
            y += lineHeight;
        }
    }

    private static float horizontalOffset(UiTextAlignment alignment, float available, float used) {
        return switch (alignment) {
            case LEFT -> 0.0f;
            case CENTER -> (available - used) * 0.5f;
            case RIGHT -> available - used;
        };
    }

    private static float verticalOffset(UiVerticalAlignment alignment, float available, float used) {
        return switch (alignment) {
            case TOP -> 0.0f;
            case CENTER -> (available - used) * 0.5f;
            case BOTTOM -> available - used;
        };
    }
}
