package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector4f;

@EpysiaComponent(name = "Ui Panel", category = "UI",
        description = "Solid rectangle, the usual background behind other elements.")
public final class UiPanel extends UiElement {
    @Export(label = "Color", color = true)
    private final Vector4f color = new Vector4f(0.1f, 0.1f, 0.12f, 0.85f);

    @Export(label = "Corner radius", min = 0.0f, step = 1.0f)
    private float cornerRadius;
    @Export(label = "Border size", min = 0.0f, step = 1.0f)
    private float borderSize;
    @Export(label = "Border color", color = true)
    private final Vector4f borderColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    @Export(label = "Fragment shader", assetExtensions = {".glsl"})
    private String shaderFragmentPath = "";

    public UiPanel setShaderFragmentPath(String path) {
        shaderFragmentPath = path == null ? "" : path;
        return this;
    }

    @Override
    public java.util.Optional<UiShader> shader() {
        return shaderFragmentPath.isEmpty()
                ? java.util.Optional.empty()
                : java.util.Optional.of(UiShader.of("ui_image.vert.glsl", shaderFragmentPath));
    }

    public UiColor color() {
        return UiColors.of(color);
    }

    public UiPanel setColor(UiColor value) {
        UiColors.copyInto(value, color);
        return this;
    }

    public float cornerRadius() {
        return cornerRadius;
    }

    public UiPanel setCornerRadius(float radius) {
        cornerRadius = Math.max(radius, 0.0f);
        return this;
    }

    public UiPanel setBorder(float size, UiColor color) {
        borderSize = Math.max(size, 0.0f);
        UiColors.copyInto(color, borderColor);
        return this;
    }

    @Override
    protected void paint(UiPainter painter) {
        painter.fillRoundedRect(computedRect(), cornerRadius, color());
        painter.strokeRoundedRect(computedRect(), cornerRadius, borderSize, UiColors.of(borderColor));
    }
}
