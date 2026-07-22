package fr.epistudio.epysia.editor.preview;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.assets.loaders.TextureAssetLoader;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.gl.GlStateSnapshot;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.backend.RenderSurface;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.vfx.ParticleEffect;
import fr.epistudio.epysia.vfx.VfxRenderSystem;
import fr.epistudio.epysia.window.Window;
import imgui.ImGui;

import java.nio.file.Path;
import java.util.List;

public final class VfxPreviewPanel {

    private static final int TARGET_WIDTH = 640;
    private static final int TARGET_HEIGHT = 360;
    private static final int POOL_SIZE = 1024;
    private static final float ASPECT_HEIGHT_FACTOR = TARGET_HEIGHT / (float) TARGET_WIDTH;
    private static final float MINIMUM_SPEED = 0.1f;
    private static final float MAXIMUM_SPEED = 3.0f;
    private static final float EMITTER_X = 0.0f;
    private static final float EMITTER_Y = -0.6f;
    private static final float EMITTER_Z = -4.0f;
    private static final float FIELD_OF_VIEW_DEGREES = 65.0f;
    private static final float NEAR_PLANE = 0.1f;
    private static final float FAR_PLANE = 100.0f;
    private static final int POOL_EXHAUSTED_COLOR = 0xFF5A5AE8;

    private final OpenGlRenderBackend backend;
    private final ShaderLoader shaderLoader = ShaderLoader.autoDetect();
    private final ShaderWatcher shaderWatcher = new ShaderWatcher(shaderLoader.filesystemRoot());
    private final PreviewRenderSurface renderSurface = new PreviewRenderSurface();
    private final Scene scene = new Scene("VfxPreview");
    private final Camera3D camera = new Camera3D();
    private final EpysiaEngine engine;
    private final float[] speed = {1.0f};

    private MeshRenderSystem meshRenderSystem;
    private VfxRenderSystem vfxRenderSystem;
    private PreviewRenderTarget target;
    private GameObject emitter;
    private ParticleEffect effect;
    private boolean initialized;
    private boolean playing = true;
    private boolean stepRequested;

    public VfxPreviewPanel(Window window, OpenGlRenderBackend backend) {
        this.backend = backend;
        this.engine = new EpysiaEngine(window, backend);
    }

    public void render(Path openGraphFile) {
        ensureInitialized();
        applyGraphPath(openGraphFile);
        vfxRenderSystem.setTimeScale(speed[0]);
        if (playing || stepRequested) {
            advanceFrame();
            stepRequested = false;
        }
        drawImage();
        renderControls();
        renderStatistics();
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }
        shaderWatcher.stop();
        target.destroy(backend);
        engine.shutdown();
        initialized = false;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        meshRenderSystem = new MeshRenderSystem(shaderLoader, shaderWatcher, engine.logger());
        PostProcessSystem postProcessSystem = new PostProcessSystem(shaderLoader, renderSurface, engine.logger());
        postProcessSystem.setShaderWatcher(shaderWatcher);
        vfxRenderSystem = new VfxRenderSystem(shaderLoader, meshRenderSystem, engine.logger());
        engine.addScene(scene);
        engine.addRenderSystem(meshRenderSystem);
        engine.addRenderSystem(vfxRenderSystem);
        engine.addRenderSystem(postProcessSystem);
        engine.initialize();
        engine.assets().register(new MeshAssetLoader(BuiltinMeshes.uploadAll(backend)));
        engine.assets().register(new TextureAssetLoader());
        renderSurface.setSize(TARGET_WIDTH, TARGET_HEIGHT);
        engine.onResize(TARGET_WIDTH, TARGET_HEIGHT);
        target = PreviewRenderTarget.create(backend, TARGET_WIDTH, TARGET_HEIGHT);
        buildScene();
        initialized = true;
    }

    private void buildScene() {
        camera.setActive(true).setNearFar(NEAR_PLANE, FAR_PLANE).setFieldOfViewDegrees(FIELD_OF_VIEW_DEGREES);
        camera.setAspectRatio(TARGET_WIDTH / (float) TARGET_HEIGHT);
        GameObject cameraObject = new GameObject("preview-camera");
        Transform3D cameraTransform = new Transform3D();
        cameraTransform.setPosition(0.0f, 0.0f, 0.0f);
        cameraTransform.lookAt(EMITTER_X, EMITTER_Y, EMITTER_Z, 0.0f, 1.0f, 0.0f);
        cameraObject.addComponent(cameraTransform);
        cameraObject.addComponent(camera);
        scene.addGameObject(cameraObject);
        emitter = new GameObject("preview-emitter");
        Transform3D emitterTransform = new Transform3D();
        emitterTransform.setPosition(EMITTER_X, EMITTER_Y, EMITTER_Z);
        emitter.addComponent(emitterTransform);
        effect = newEffect();
        emitter.addComponent(effect);
        scene.addGameObject(emitter);
    }

    private static ParticleEffect newEffect() {
        return new ParticleEffect().setPoolSize(POOL_SIZE);
    }

    private void applyGraphPath(Path openGraphFile) {
        String absolute = openGraphFile.toAbsolutePath().normalize().toString();
        if (!absolute.equals(effect.graphPath())) {
            effect.setGraphPath(absolute);
        }
    }

    private void advanceFrame() {
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            shaderWatcher.poll();
            engine.setActiveScene(scene);
            engine.render(List.of(camera), target.handle(), Camera3D.CURRENT_STATE_ALPHA);
        } finally {
            snapshot.restore();
        }
    }

    private void drawImage() {
        float width = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float height = width * ASPECT_HEIGHT_FACTOR;
        ImGui.image(target.glTextureName(), width, height, 0.0f, 1.0f, 1.0f, 0.0f);
    }

    private void renderControls() {
        if (ImGui.button(playing ? "Pause" : "Play")) {
            playing = !playing;
        }
        ImGui.sameLine();
        if (ImGui.button("Restart")) {
            restart();
        }
        ImGui.sameLine();
        if (ImGui.button("Step")) {
            stepRequested = true;
        }
        ImGui.setNextItemWidth(-1.0f);
        ImGui.sliderFloat("##vfx-speed", speed, MINIMUM_SPEED, MAXIMUM_SPEED, "speed %.2fx");
    }

    private void restart() {
        ParticleEffect replacement = newEffect().setGraphPath(effect.graphPath());
        emitter.replaceComponent(effect, replacement);
        effect = replacement;
    }

    private void renderStatistics() {
        int poolSize = effect.poolSize();
        int alive = vfxRenderSystem.aliveCountOf(effect).orElse(0);
        ImGui.text("alive: " + alive + " / " + poolSize);
        ImGui.progressBar(alive / (float) poolSize, -1.0f, 0.0f, alive + " / " + poolSize);
        if (alive >= poolSize) {
            ImGui.textColored(POOL_EXHAUSTED_COLOR, "pool exhausted");
        }
    }

    private static final class PreviewRenderSurface implements RenderSurface {

        private int width = 1;
        private int height = 1;

        void setSize(int newWidth, int newHeight) {
            width = Math.max(1, newWidth);
            height = Math.max(1, newHeight);
        }

        @Override
        public int framebufferWidth() {
            return width;
        }

        @Override
        public int framebufferHeight() {
            return height;
        }
    }
}
