package fr.epistudio.epysia.render.decal;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RenderLayers;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.render.backend.TextureHandle;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Optional;

@EpysiaComponent(name = "Decal", category = "Rendering",
        description = "Projects a texture onto the surfaces inside its box, along its forward axis.")
@RequiresComponent(Transform3D.class)
public final class Decal extends Component {

    private static final float STRAIGHT_ANGLE_DEGREES = 90.0f;

    @Export(label = "Texture")
    private final AssetRef<TextureHandle> texture = new AssetRef<>(TextureHandle.class);
    @Export(label = "Tint", color = true)
    private final Vector3f tint = new Vector3f(1.0f, 1.0f, 1.0f);
    @Export(label = "Opacity", min = 0.0f, max = 1.0f, step = 0.01f)
    private float opacity = 1.0f;
    @Export(label = "Blend")
    private DecalBlend blend = DecalBlend.ALPHA;
    @Export(label = "Angle Fade", min = 0.0f, max = 90.0f, step = 1.0f)
    private float angleFadeDegrees = 75.0f;
    @Export(label = "Uv Scale")
    private final Vector2f uvScale = new Vector2f(1.0f, 1.0f);
    @Export(label = "Uv Offset")
    private final Vector2f uvOffset = new Vector2f();
    @Export(label = "Layer Mask", layerMask = true)
    private int layerMask = RenderLayers.ALL;
    @Export(label = "Sort Order", step = 1.0f)
    private int sortOrder;

    public AssetRef<TextureHandle> texture() {
        return texture;
    }

    public Optional<TextureHandle> textureHandle() {
        return texture.direct();
    }

    @Override
    public void onLoad(EngineServices services) {
        if (texture.direct().isEmpty() && !texture.isEmpty()) {
            texture.resolve(services.assets());
        }
    }

    public Vector3f tint() {
        return tint;
    }

    public float opacity() {
        return opacity;
    }

    public Decal setOpacity(float value) {
        opacity = Math.clamp(value, 0.0f, 1.0f);
        return this;
    }

    public DecalBlend blend() {
        return blend;
    }

    public Decal setBlend(DecalBlend value) {
        blend = value;
        return this;
    }

    public float angleFadeCosine() {
        return (float) Math.cos(Math.toRadians(Math.clamp(angleFadeDegrees, 0.0f, STRAIGHT_ANGLE_DEGREES)));
    }

    public Vector2f uvScale() {
        return uvScale;
    }

    public Vector2f uvOffset() {
        return uvOffset;
    }

    public int layerMask() {
        return layerMask;
    }

    public Decal setLayerMask(int mask) {
        layerMask = mask;
        return this;
    }

    public int sortOrder() {
        return sortOrder;
    }
}
