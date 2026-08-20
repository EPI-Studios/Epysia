package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.shader.ShaderUniformValues;
import fr.epistudio.epysia.render.shader.SurfaceUniformHost;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;

@EpysiaComponent(name = "Sprite Renderer", category = "Rendering",
        description = "Draws a texture or an atlas region as a flat 2D quad.")
@RequiresComponent(Transform2D.class)
public final class SpriteRenderer extends Component implements SurfaceUniformHost {

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
    @Export(label = "Flip Around Pivot")
    private boolean flipAroundPivot;
    @Export(label = "Pixels Per Unit", min = 0.01f, max = 10000.0f, step = 1.0f)
    private float pixelsPerUnit = 32.0f;
    @Export(label = "Sorting Layer", step = 1.0f)
    private int sortingLayer;
    @Export(label = "Order In Layer", step = 1.0f)
    private int orderInLayer;
    @Export(label = "Sort By Y")
    private boolean sortByY;
    @Export(label = "Region Min U", min = 0.0f, max = 1.0f, step = 0.01f)
    private float regionMinU;
    @Export(label = "Region Min V", min = 0.0f, max = 1.0f, step = 0.01f)
    private float regionMinV;
    @Export(label = "Region Max U", min = 0.0f, max = 1.0f, step = 0.01f)
    private float regionMaxU = 1.0f;
    @Export(label = "Region Max V", min = 0.0f, max = 1.0f, step = 0.01f)
    private float regionMaxV = 1.0f;
    @Export(label = "Atlas")
    private final AssetRef<SpriteAtlas> atlas = new AssetRef<>(SpriteAtlas.class);
    @Export(label = "Region Name")
    private String regionName = "";
    @Export(label = "Lit")
    private boolean lit;
    @Export(label = "Surface Shader", assetExtensions = {".glsl"})
    private String surfaceShaderPath = "";
    @Export(label = "Shader Uniforms")
    private final ShaderUniformValues surfaceUniforms = new ShaderUniformValues();
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

    private final transient AssetRef<TextureHandle> atlasTexture = new AssetRef<>(TextureHandle.class);
    private transient String appliedAtlasPath = "";
    private transient String appliedRegionName = "";

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

    public boolean flipAroundPivot() {
        return flipAroundPivot;
    }

    public SpriteRenderer setFlipAroundPivot(boolean flipAroundPivot) {
        this.flipAroundPivot = flipAroundPivot;
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

    public boolean sortByY() {
        return sortByY;
    }

    public SpriteRenderer setSortByY(boolean sortByY) {
        this.sortByY = sortByY;
        return this;
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

    public boolean lit() {
        return lit;
    }

    @Override
    public ShaderUniformValues surfaceUniforms() {
        return surfaceUniforms;
    }

    public String surfaceShaderPath() {
        return surfaceShaderPath;
    }

    public SpriteRenderer setSurfaceShaderPath(String path) {
        surfaceShaderPath = path == null ? "" : path;
        return this;
    }

    public SpriteRenderer setLit(boolean lit) {
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

    public SpriteRenderer setMetallic(float metallic) {
        this.metallic = metallic;
        return this;
    }

    public float roughness() {
        return roughness;
    }

    public SpriteRenderer setRoughness(float roughness) {
        this.roughness = roughness;
        return this;
    }

    public float normalStrength() {
        return normalStrength;
    }

    public SpriteRenderer setNormalStrength(float normalStrength) {
        this.normalStrength = normalStrength;
        return this;
    }

    public float emissiveStrength() {
        return emissiveStrength;
    }

    public SpriteRenderer setEmissiveStrength(float emissiveStrength) {
        this.emissiveStrength = emissiveStrength;
        return this;
    }

    public Vector4f shaderParams0() {
        return shaderParams0;
    }

    public Vector4f shaderParams1() {
        return shaderParams1;
    }

    public SpriteRenderer setShaderParams0(float x, float y, float z, float w) {
        shaderParams0.set(x, y, z, w);
        return this;
    }

    public SpriteRenderer setShaderParams1(float x, float y, float z, float w) {
        shaderParams1.set(x, y, z, w);
        return this;
    }

    public int lightLayers() {
        return lightLayers;
    }

    public SpriteRenderer setLightLayers(int lightLayers) {
        this.lightLayers = lightLayers;
        return this;
    }

    public AssetRef<SpriteAtlas> atlasRef() {
        return atlas;
    }

    public SpriteRenderer setAtlasPath(String path) {
        atlas.setPath(path);
        return this;
    }

    public String regionName() {
        return regionName;
    }

    public SpriteRenderer setRegionName(String value) {
        regionName = value;
        return this;
    }

    @Override
    public void onLoad(EngineServices services) {
        if (texture.direct().isEmpty() && !texture.isEmpty()) {
            texture.resolve(services.assets());
        }
        resolveIfNeeded(services, normalMap);
        resolveIfNeeded(services, metallicRoughnessMap);
        resolveIfNeeded(services, emissiveMap);
        refreshAtlas(services);
    }

    private static void resolveIfNeeded(EngineServices services, AssetRef<TextureHandle> reference) {
        if (reference.direct().isEmpty() && !reference.isEmpty()) {
            reference.resolve(services.assets());
        }
    }

    public void refreshAtlas(EngineServices services) {
        if (regionName.isEmpty()) {
            return;
        }
        if (atlas.path().equals(appliedAtlasPath) && regionName.equals(appliedRegionName)) {
            return;
        }
        atlas.resolve(services.assets()).ifPresent(loaded -> applyAtlas(services, loaded));
    }

    private void applyAtlas(EngineServices services, SpriteAtlas loaded) {
        loaded.region(regionName).ifPresent(region -> {
            setRegion(region.minU(), region.minV(), region.maxU(), region.maxV());
            appliedAtlasPath = atlas.path();
            appliedRegionName = regionName;
        });
        if (!loaded.texturePath().isEmpty()) {
            atlasTexture.setPath(loaded.texturePath());
            atlasTexture.resolve(services.assets()).ifPresent(texture::setDirect);
        }
    }
}
