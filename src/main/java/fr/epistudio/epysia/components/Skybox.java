package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.environment.SkyMode;
import fr.epistudio.epysia.render.environment.SkySource;

@EpysiaComponent(name = "Skybox", category = "Rendering",
        description = "Background and ambient light for the scene, procedural or from a cubemap.")
public final class Skybox extends Component {

    @Export(label = "Mode")
    private SkyMode mode = SkyMode.PROCEDURAL;

    @Export(label = "Sky Shader")
    private String skyShaderPath = "";

    @Export(label = "Environment Map")
    private final AssetRef<TextureHandle> environmentMap = new AssetRef<>(TextureHandle.class);

    @Export(label = "Sky Intensity", min = 0.0f, max = 20.0f, step = 0.05f)
    private float skyIntensity = 1.0f;

    @Export(label = "Ambient Intensity", min = 0.0f, max = 20.0f, step = 0.05f)
    private float ambientIntensity = 1.0f;

    public SkyMode mode() {
        return mode;
    }

    public Skybox setMode(SkyMode mode) {
        this.mode = mode;
        return this;
    }

    public String skyShaderPath() {
        return skyShaderPath;
    }

    public Skybox setSkyShaderPath(String skyShaderPath) {
        this.skyShaderPath = skyShaderPath == null ? "" : skyShaderPath;
        return this;
    }

    public AssetRef<TextureHandle> environmentMapRef() {
        return environmentMap;
    }

    public float skyIntensity() {
        return skyIntensity;
    }

    public float ambientIntensity() {
        return ambientIntensity;
    }

    public SkySource source() {
        return switch (mode) {
            case PROCEDURAL -> SkySource.PROCEDURAL;
            case SHADER -> SkySource.ofShader(skyShaderPath);
            case TEXTURE -> environmentMap.direct().map(SkySource::ofTexture).orElse(SkySource.PROCEDURAL);
        };
    }

    @Override
    public void onLoad(EngineServices services) {
        if (environmentMap.direct().isEmpty() && !environmentMap.isEmpty()) {
            environmentMap.resolve(services.assets());
        }
    }
}
