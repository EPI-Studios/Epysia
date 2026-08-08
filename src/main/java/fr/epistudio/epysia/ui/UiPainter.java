package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.render.backend.TextureHandle;

public interface UiPainter {
    void fillRect(UiRect rect, UiColor color);

    void drawImage(UiRect rect, String texturePath, float uvMinX, float uvMinY,
                   float uvMaxX, float uvMaxY, UiColor tint);

    void drawTexture(UiRect rect, TextureHandle texture, float uvMinX, float uvMinY,
                     float uvMaxX, float uvMaxY, UiColor tint);

    void drawText(float x, float y, String text, UiFontStyle style, UiColor color);

    float measureTextWidth(String text, UiFontStyle style);

    float lineHeight(UiFontStyle style);

    UiImageSize imageSize(String texturePath);
}
