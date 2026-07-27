package fr.epistudio.epysia.render.environment;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import org.joml.Vector3f;

public final class Environment {

    private static final float REBAKE_DOT_THRESHOLD = 0.9999f;

    private final SkySettings settings = new SkySettings();
    private final FullscreenQuad quad = new FullscreenQuad();
    private final EnvironmentMaps maps;
    private final SkyPass skyPass;
    private final Vector3f defaultSunDirection = new Vector3f(0.35f, 0.8f, 0.45f).normalize();
    private final Vector3f lastSunDirection = new Vector3f(Float.NaN);
    private float lastSkyIntensity = Float.NaN;
    private SkySource source = SkySource.PROCEDURAL;
    private boolean sourceDirty;

    public Environment(ShaderLoader shaderLoader) {
        this.maps = new EnvironmentMaps(shaderLoader, quad);
        this.skyPass = new SkyPass(shaderLoader, quad);
    }

    public SkySettings settings() {
        return settings;
    }

    public Vector3f defaultSunDirection() {
        return defaultSunDirection;
    }

    public void initialize(RenderBackend backend) {
        quad.initialize(backend);
        maps.initialize(backend);
        skyPass.initialize(backend);
    }

    public SkySource source() {
        return source;
    }

    public void setSource(SkySource newSource) {
        if (newSource == null || newSource.sameAs(source)) {
            return;
        }
        source = newSource;
        sourceDirty = true;
    }

    public void prepareFrame(Vector3f sunDirection) {
        if (sourceDirty) {
            skyPass.rebuild(source);
            maps.rebuildSkyCapture(source);
            sourceDirty = false;
            lastSkyIntensity = Float.NaN;
        }
        boolean sunMoved = !(lastSunDirection.dot(sunDirection) > REBAKE_DOT_THRESHOLD);
        boolean intensityChanged = lastSkyIntensity != settings.skyIntensity();
        if (sunMoved || intensityChanged) {
            maps.bake(sunDirection, settings.skyIntensity());
            lastSunDirection.set(sunDirection);
            lastSkyIntensity = settings.skyIntensity();
        }
    }

    public void collectSky(Camera3D camera, Vector3f sunDirection, FrameBuilder frame, float alpha) {
        if (settings.skyIntensity() <= 0.0f) {
            return;
        }
        skyPass.collect(camera, sunDirection, settings.skyIntensity(), frame, alpha);
    }

    public TextureHandle irradiance() {
        return maps.irradiance();
    }

    public TextureHandle prefiltered() {
        return maps.prefiltered();
    }

    public TextureHandle brdfLut() {
        return maps.brdfLut();
    }

    public void shutdown() {
        skyPass.shutdown();
        maps.shutdown();
        quad.shutdown();
    }
}
