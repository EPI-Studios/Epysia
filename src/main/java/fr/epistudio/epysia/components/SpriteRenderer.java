package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.render.backend.TextureHandle;
import org.joml.Vector3f;

import java.util.Optional;

@EpysiaComponent(name = "Sprite Renderer", category = "Rendering")
@RequiresComponent(Transform2D.class)
public final class SpriteRenderer extends Component {

    @Export(label = "Texture")
    private final AssetRef<TextureHandle> texture = new AssetRef<>(TextureHandle.class);
    @Export(label = "Tint")
    private final Vector3f tint = new Vector3f(1.0f, 1.0f, 1.0f);
    @Export(label = "Opacity", min = 0.0f, max = 1.0f, step = 0.01f)
    private float opacity = 1.0f;
    @Export(label = "Flip X")
    private boolean flipX;
    @Export(label = "Flip Y")
    private boolean flipY;
    @Export(label = "Pixels Per Unit", min = 0.01f, max = 10000.0f, step = 1.0f)
    private float pixelsPerUnit = 100.0f;
    @Export(label = "Sorting Layer", step = 1.0f)
    private int sortingLayer;
    @Export(label = "Order In Layer", step = 1.0f)
    private int orderInLayer;
    @Export(label = "Region Min U", min = 0.0f, max = 1.0f, step = 0.01f)
    private float regionMinU;
    @Export(label = "Region Min V", min = 0.0f, max = 1.0f, step = 0.01f)
    private float regionMinV;
    @Export(label = "Region Max U", min = 0.0f, max = 1.0f, step = 0.01f)
    private float regionMaxU = 1.0f;
    @Export(label = "Region Max V", min = 0.0f, max = 1.0f, step = 0.01f)
    private float regionMaxV = 1.0f;

    public AssetRef<TextureHandle> textureRef() {
        return texture;
    }

    public SpriteRenderer setTexture(TextureHandle value) {
        texture.setDirect(value);
        return this;
    }

    public SpriteRenderer setTexturePath(String path) {
        texture.setPath(path);
        return this;
    }

    public Optional<TextureHandle> texture() {
        return texture.direct();
    }

    public Vector3f tint() {
        return tint;
    }

    public SpriteRenderer setTint(float red, float green, float blue) {
        tint.set(red, green, blue);
        return this;
    }

    public float opacity() {
        return opacity;
    }

    public SpriteRenderer setOpacity(float opacity) {
        this.opacity = opacity;
        return this;
    }

    public boolean flipX() {
        return flipX;
    }

    public SpriteRenderer setFlipX(boolean flipX) {
        this.flipX = flipX;
        return this;
    }

    public boolean flipY() {
        return flipY;
    }

    public SpriteRenderer setFlipY(boolean flipY) {
        this.flipY = flipY;
        return this;
    }

    public float pixelsPerUnit() {
        return pixelsPerUnit;
    }

    public SpriteRenderer setPixelsPerUnit(float pixelsPerUnit) {
        this.pixelsPerUnit = pixelsPerUnit;
        return this;
    }

    public int sortingLayer() {
        return sortingLayer;
    }

    public SpriteRenderer setSortingLayer(int sortingLayer) {
        this.sortingLayer = sortingLayer;
        return this;
    }

    public int orderInLayer() {
        return orderInLayer;
    }

    public SpriteRenderer setOrderInLayer(int orderInLayer) {
        this.orderInLayer = orderInLayer;
        return this;
    }

    public float regionMinU() {
        return regionMinU;
    }

    public float regionMinV() {
        return regionMinV;
    }

    public float regionMaxU() {
        return regionMaxU;
    }

    public float regionMaxV() {
        return regionMaxV;
    }

    public SpriteRenderer setRegion(float minU, float minV, float maxU, float maxV) {
        regionMinU = minU;
        regionMinV = minV;
        regionMaxU = maxU;
        regionMaxV = maxV;
        return this;
    }

    @Override
    public void onLoad(EngineServices services) {
        if (texture.direct().isEmpty() && !texture.isEmpty()) {
            texture.resolve(services.assets());
        }
    }
}
