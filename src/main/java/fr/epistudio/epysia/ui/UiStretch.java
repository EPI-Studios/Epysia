package fr.epistudio.epysia.ui;

public final class UiStretch {
    public record Placement(UiRect rect, float uvMinX, float uvMinY, float uvMaxX, float uvMaxY) {
    }

    private UiStretch() {
    }

    public static Placement apply(UiStretchMode mode, UiRect rect, UiImageSize size,
                                  float uvMinX, float uvMinY, float uvMaxX, float uvMaxY) {
        if (!size.known() || mode == UiStretchMode.SCALE) {
            return new Placement(rect, uvMinX, uvMinY, uvMaxX, uvMaxY);
        }
        return switch (mode) {
            case TILE -> tiled(rect, size, uvMinX, uvMinY, uvMaxX, uvMaxY);
            case KEEP -> placed(new UiRect(rect.x(), rect.y(), size.width(), size.height()),
                    uvMinX, uvMinY, uvMaxX, uvMaxY);
            case KEEP_CENTERED -> placed(centered(rect, size.width(), size.height()),
                    uvMinX, uvMinY, uvMaxX, uvMaxY);
            case KEEP_ASPECT -> placed(fitted(rect, size, false), uvMinX, uvMinY, uvMaxX, uvMaxY);
            case KEEP_ASPECT_CENTERED -> placed(fitted(rect, size, true), uvMinX, uvMinY, uvMaxX, uvMaxY);
            case KEEP_ASPECT_COVERED -> covered(rect, size, uvMinX, uvMinY, uvMaxX, uvMaxY);
            case SCALE -> placed(rect, uvMinX, uvMinY, uvMaxX, uvMaxY);
        };
    }

    private static Placement placed(UiRect rect, float uvMinX, float uvMinY, float uvMaxX, float uvMaxY) {
        return new Placement(rect, uvMinX, uvMinY, uvMaxX, uvMaxY);
    }

    private static UiRect centered(UiRect rect, float width, float height) {
        return new UiRect(rect.x() + (rect.width() - width) * 0.5f,
                rect.y() + (rect.height() - height) * 0.5f, width, height);
    }

    private static UiRect fitted(UiRect rect, UiImageSize size, boolean center) {
        float width = size.width() * rect.height() / size.height();
        float height = rect.height();
        if (width > rect.width()) {
            width = rect.width();
            height = size.height() * width / size.width();
        }
        return center ? centered(rect, width, height) : new UiRect(rect.x(), rect.y(), width, height);
    }

    private static Placement covered(UiRect rect, UiImageSize size,
                                     float uvMinX, float uvMinY, float uvMaxX, float uvMaxY) {
        float scale = Math.max(rect.width() / size.width(), rect.height() / size.height());
        float visibleWidth = rect.width() / scale / size.width();
        float visibleHeight = rect.height() / scale / size.height();
        float centerU = (uvMinX + uvMaxX) * 0.5f;
        float centerV = (uvMinY + uvMaxY) * 0.5f;
        float halfU = (uvMaxX - uvMinX) * visibleWidth * 0.5f;
        float halfV = (uvMaxY - uvMinY) * visibleHeight * 0.5f;
        return new Placement(rect, centerU - halfU, centerV - halfV, centerU + halfU, centerV + halfV);
    }

    private static Placement tiled(UiRect rect, UiImageSize size,
                                   float uvMinX, float uvMinY, float uvMaxX, float uvMaxY) {
        float repeatsX = rect.width() / size.width();
        float repeatsY = rect.height() / size.height();
        return new Placement(rect, uvMinX, uvMinY,
                uvMinX + (uvMaxX - uvMinX) * repeatsX, uvMinY + (uvMaxY - uvMinY) * repeatsY);
    }
}
