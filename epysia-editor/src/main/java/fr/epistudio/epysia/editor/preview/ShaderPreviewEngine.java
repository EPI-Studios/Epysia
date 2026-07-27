package fr.epistudio.epysia.editor.preview;

import java.util.HashMap;
import java.util.Map;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.assets.loaders.TextureAssetLoader;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.editor.gl.GlStateSnapshot;
import fr.epistudio.epysia.render.backend.RenderSurface;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import org.lwjgl.opengl.GL11;

import java.util.List;


public final class ShaderPreviewEngine {

    private final Map<String, String> publishedSources = new HashMap<>();
    private final OpenGlRenderBackend backend;
    private final EpysiaEngine engine;
    private final ShaderLoader shaderLoader = ShaderLoader.autoDetect();
    private final ShaderWatcher shaderWatcher = new ShaderWatcher(shaderLoader.filesystemRoot());
    private final PreviewRenderSurface renderSurface = new PreviewRenderSurface();
    private final boolean postProcessingEnabled;
    private boolean initialized;

    public ShaderPreviewEngine(Window window, OpenGlRenderBackend backend, boolean postProcessingEnabled) {
        this.backend = backend;
        this.postProcessingEnabled = postProcessingEnabled;
        this.engine = new EpysiaEngine(window, backend);
    }

    public void initialize() {
        if (initialized) {
            return;
        }
        engine.addRenderSystem(new MeshRenderSystem(shaderLoader, shaderWatcher, engine.logger()));
        if (postProcessingEnabled) {
            PostProcessSystem postProcessSystem = new PostProcessSystem(shaderLoader, renderSurface, engine.logger());
            postProcessSystem.setShaderWatcher(shaderWatcher);
            engine.addRenderSystem(postProcessSystem);
        }
        engine.initialize();
        engine.assets().register(new MeshAssetLoader(BuiltinMeshes.uploadAll(backend)));
        engine.assets().register(new TextureAssetLoader());
        initialized = true;
    }

    public EpysiaEngine engine() {
        return engine;
    }

    public OpenGlRenderBackend backend() {
        return backend;
    }

    public void addScene(Scene scene) {
        engine.addScene(scene);
    }

    public void publishSource(String shaderPath, String source) {
        shaderLoader.putVirtualSource(shaderPath, source);
        if (source.equals(publishedSources.put(shaderPath, source))) {
            return;
        }
        shaderWatcher.notifyPathChanged(shaderPath);
    }

    public void render(Scene scene, Camera3D camera, PreviewRenderTarget target) {
        if (!initialized) {
            return;
        }
        camera.setAspectRatio((float) target.width() / (float) target.height());
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            shaderWatcher.poll();
            resizeIfNeeded(target);
            engine.setActiveScene(scene);
            engine.render(List.of(camera), target.handle(), Camera3D.CURRENT_STATE_ALPHA);
        } finally {
            snapshot.restore();
        }
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }
        shaderWatcher.stop();
        engine.shutdown();
        initialized = false;
    }

    private void resizeIfNeeded(PreviewRenderTarget target) {
        if (!postProcessingEnabled || renderSurface.matches(target.width(), target.height())) {
            return;
        }
        renderSurface.setSize(target.width(), target.height());
        engine.onResize(target.width(), target.height());
    }

    private static final class PreviewRenderSurface implements RenderSurface {

        private int width = 1;
        private int height = 1;

        void setSize(int newWidth, int newHeight) {
            width = Math.max(1, newWidth);
            height = Math.max(1, newHeight);
        }

        boolean matches(int candidateWidth, int candidateHeight) {
            return width == candidateWidth && height == candidateHeight;
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
