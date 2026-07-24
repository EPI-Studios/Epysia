package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.render.backend.TextureHandle;
import org.joml.Vector3f;

import java.util.Optional;

@EpysiaComponent(name = "Tilemap Renderer", category = "Rendering")
@RequiresComponent(Transform2D.class)
public final class TilemapRenderer extends Component {

    @Export(label = "Tilemap")
    private final AssetRef<SpriteTilemap> tilemap = new AssetRef<>(SpriteTilemap.class);
    @Export(label = "Tint")
    private final Vector3f tint = new Vector3f(1.0f, 1.0f, 1.0f);
    @Export(label = "Opacity", min = 0.0f, max = 1.0f, step = 0.01f)
    private float opacity = 1.0f;
    @Export(label = "Sorting Layer", step = 1.0f)
    private int sortingLayer;
    @Export(label = "Order In Layer", step = 1.0f)
    private int orderInLayer;

    private final transient AssetRef<SpriteAtlas> atlas = new AssetRef<>(SpriteAtlas.class);
    private final transient AssetRef<TextureHandle> texture = new AssetRef<>(TextureHandle.class);
    private transient String appliedAtlasPath = "";

    public AssetRef<SpriteTilemap> tilemapRef() {
        return tilemap;
    }

    public TilemapRenderer setTilemapPath(String path) {
        tilemap.setPath(path);
        return this;
    }

    public TilemapRenderer setTilemap(SpriteTilemap value) {
        tilemap.setDirect(value);
        return this;
    }

    public Optional<SpriteTilemap> tilemapValue() {
        return tilemap.direct();
    }

    public TilemapRenderer setAtlas(SpriteAtlas value) {
        atlas.setDirect(value);
        return this;
    }

    public Optional<SpriteAtlas> atlasValue() {
        return atlas.direct();
    }

    public TilemapRenderer setTexture(TextureHandle value) {
        texture.setDirect(value);
        return this;
    }

    public Optional<TextureHandle> texture() {
        return texture.direct();
    }

    public Vector3f tint() {
        return tint;
    }

    public TilemapRenderer setTint(float red, float green, float blue) {
        tint.set(red, green, blue);
        return this;
    }

    public float opacity() {
        return opacity;
    }

    public TilemapRenderer setOpacity(float opacity) {
        this.opacity = opacity;
        return this;
    }

    public int sortingLayer() {
        return sortingLayer;
    }

    public TilemapRenderer setSortingLayer(int sortingLayer) {
        this.sortingLayer = sortingLayer;
        return this;
    }

    public int orderInLayer() {
        return orderInLayer;
    }

    public TilemapRenderer setOrderInLayer(int orderInLayer) {
        this.orderInLayer = orderInLayer;
        return this;
    }

    @Override
    public void onLoad(EngineServices services) {
        refresh(services);
    }

    public void refresh(EngineServices services) {
        tilemap.resolve(services.assets()).ifPresent(loaded -> refreshAtlas(services, loaded));
    }

    private void refreshAtlas(EngineServices services, SpriteTilemap loaded) {
        if (loaded.atlasPath().isEmpty() || loaded.atlasPath().equals(appliedAtlasPath)) {
            return;
        }
        atlas.setPath(loaded.atlasPath());
        atlas.resolve(services.assets()).ifPresent(loadedAtlas -> applyAtlasTexture(services, loadedAtlas));
        appliedAtlasPath = loaded.atlasPath();
    }

    private void applyAtlasTexture(EngineServices services, SpriteAtlas loadedAtlas) {
        if (loadedAtlas.texturePath().isEmpty()) {
            return;
        }
        texture.setPath(loadedAtlas.texturePath());
        texture.resolve(services.assets());
    }
}
