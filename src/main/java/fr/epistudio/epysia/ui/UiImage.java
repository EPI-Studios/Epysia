package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

@EpysiaComponent(name = "Ui Image", category = "UI")
public final class UiImage extends UiElement {
    @Export(label = "Texture", assetExtensions = {".png", ".jpg", ".jpeg", ".tga", ".bmp"})
    private String texturePath = "";
    @Export(label = "Tint", color = true)
    private final Vector4f tint = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    @Export(label = "Stretch")
    private UiStretchMode stretchMode = UiStretchMode.SCALE;
    @Export(label = "Region min", min = 0.0f, max = 1.0f, step = 0.01f)
    private final org.joml.Vector2f regionMin = new org.joml.Vector2f(0.0f, 0.0f);
    @Export(label = "Region max", min = 0.0f, max = 1.0f, step = 0.01f)
    private final org.joml.Vector2f regionMax = new org.joml.Vector2f(1.0f, 1.0f);
    @Export(label = "Slice borders", step = 1.0f)
    private final Vector4f sliceBorders = new Vector4f();

    public UiImage setTexturePath(String path) {
        this.texturePath = path == null ? "" : path;
        return this;
    }

    public String texturePath() {
        return texturePath;
    }

    public UiImage setStretchMode(UiStretchMode mode) {
        this.stretchMode = mode;
        return this;
    }

    private boolean nineSliced() {
        return sliceBorders.x() > 0.0f || sliceBorders.y() > 0.0f
                || sliceBorders.z() > 0.0f || sliceBorders.w() > 0.0f;
    }

    @Override
    protected void paint(UiPainter painter) {
        if (texturePath.isEmpty()) {
            return;
        }
        UiColor color = UiColors.of(tint);
        if (nineSliced()) {
            UiNineSlice.paint(painter, computedRect(), texturePath, color,
                    new float[]{regionMin.x(), regionMin.y(), regionMax.x(), regionMax.y()},
                    new float[]{sliceBorders.x(), sliceBorders.y(), sliceBorders.z(), sliceBorders.w()});
            return;
        }
        UiStretch.Placement placement = UiStretch.apply(stretchMode, computedRect(),
                painter.imageSize(texturePath), regionMin.x(), regionMin.y(), regionMax.x(), regionMax.y());
        painter.drawImage(placement.rect(), texturePath, placement.uvMinX(), placement.uvMinY(),
                placement.uvMaxX(), placement.uvMaxY(), color);
    }
}
