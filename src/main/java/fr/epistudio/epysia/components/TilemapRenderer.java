package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.render.backend.TextureHandle;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;

@EpysiaComponent(name = "Tilemap Renderer", category = "Rendering",
        description = "Draws a tilemap asset, layer by layer.")
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
    @Export(label = "Lit")
    private boolean lit;
    @Export(label = "Normal Map")
    private final AssetRef<TextureHandle> normalMap = new AssetRef<>(TextureHandle.class);
    @Export(label = "Metallic Roughness Map")
    private final AssetRef<TextureHandle> metallicRoughnessMap = new AssetRef<>(TextureHandle.class);
    @Export(label = "Emissive Map")
    private final AssetRef<TextureHandle> emissiveMap = new AssetRef<>(TextureHandle.class);
    @Export(label = "Metallic", min = 0.0f, max = 1.0f, step = 0.01f)
    private float metallic;
    @Export(label = "Roughness", min = 0.0f, max = 1.0f, step = 0.01f)
    private float roughness = 0.8f;
    @Export(label = "Normal Strength", min = 0.0f, max = 4.0f, step = 0.01f)
    private float normalStrength = 1.0f;
    @Export(label = "Emissive Strength", min = 0.0f, max = 20.0f, step = 0.05f)
    private float emissiveStrength = 1.0f;
    @Export(label = "Shader Params 0", step = 0.01f)
    private final Vector4f shaderParams0 = new Vector4f();
    @Export(label = "Shader Params 1", step = 0.01f)
    private final Vector4f shaderParams1 = new Vector4f();
    @Export(label = "Light Layers", step = 1.0f)
    private int lightLayers = Light2D.ALL_LIGHT_LAYERS;

    private final transient AssetRef<SpriteAtlas> atlas = new AssetRef<>(SpriteAtlas.class);
    private final transient AssetRef<TextureHandle> texture = new AssetRef<>(TextureHandle.class);
    private transient String appliedAtlasPath = "";

    public boolean lit() {
        return lit;
    }

    public TilemapRenderer setLit(boolean lit) {
        this.lit = lit;
        return this;
    }

    public AssetRef<TextureHandle> normalMapRef() {
        return normalMap;
    }

    public AssetRef<TextureHandle> metallicRoughnessMapRef() {
        return metallicRoughnessMap;
    }

    public AssetRef<TextureHandle> emissiveMapRef() {
        return emissiveMap;
    }

    public float metallic() {
        return metallic;
    }

    public float roughness() {
        return roughness;
    }

    public float normalStrength() {
        return normalStrength;
    }

    public float emissiveStrength() {
        return emissiveStrength;
    }

    public Vector4f shaderParams0() {
        return shaderParams0;
    }

    public Vector4f shaderParams1() {
        return shaderParams1;
    }

    public TilemapRenderer setShaderParams0(float x, float y, float z, float w) {
        shaderParams0.set(x, y, z, w);
        return this;
    }

    public TilemapRenderer setShaderParams1(float x, float y, float z, float w) {
        shaderParams1.set(x, y, z, w);
        return this;
    }

    public int lightLayers() {
        return lightLayers;
    }

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

    public int cellXAt(float localX) {
        return tilemapValue().map(map -> (int) Math.floor(localX / map.cellWidth())).orElse(0);
    }

    public int cellYAt(float localY) {
        return tilemapValue().map(map -> (int) Math.floor(localY / map.cellHeight())).orElse(0);
    }

    public Optional<TileData> tileDataAt(float localX, float localY) {
        return tilemapValue().flatMap(map ->
                map.existingTileData(map.tileIndex(cellXAt(localX), cellYAt(localY))));
    }

    public Optional<String> tileValueAt(float localX, float localY, String key) {
        return tileDataAt(localX, localY).flatMap(data -> data.customValue(key));
    }

    public boolean solidAt(float localX, float localY) {
        return tilemapValue().map(map -> map.isCellSolid(cellXAt(localX), cellYAt(localY))).orElse(false);
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
        resolveIfNeeded(services, normalMap);
        resolveIfNeeded(services, metallicRoughnessMap);
        resolveIfNeeded(services, emissiveMap);
        tilemap.resolve(services.assets()).ifPresent(loaded -> refreshAtlas(services, loaded));
    }

    private static void resolveIfNeeded(EngineServices services, AssetRef<TextureHandle> reference) {
        if (reference.direct().isEmpty() && !reference.isEmpty()) {
            reference.resolve(services.assets());
        }
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
