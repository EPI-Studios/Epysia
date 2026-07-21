package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.gl.GlStateSnapshot;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.backend.RenderSurface;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class MeshThumbnailer {

    private static final int THUMB_PIXEL_SIZE = 128;
    private static final int MAX_ENTRIES = 256;

    private final OpenGlRenderBackend backend = new OpenGlRenderBackend();
    private final Window window = new Window("(thumbnailer)", THUMB_PIXEL_SIZE, THUMB_PIXEL_SIZE);
    private final EpysiaEngine engine = new EpysiaEngine(window, backend);
    private final Scene scene = new Scene("thumbnails");
    private final GameObject targetObject = new GameObject("ThumbTarget");
    private final Transform3D targetTransform = new Transform3D();
    private final MeshRenderer targetRenderer = new MeshRenderer();
    private final Camera3D thumbCamera;
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> failedPaths = new HashSet<>();
    private boolean initialized;

    public MeshThumbnailer() {
        targetObject.addComponent(targetTransform);
        targetObject.addComponent(targetRenderer);
        scene.addGameObject(buildSun());
        GameObject cameraObject = new GameObject("ThumbCam");
        Transform3D cameraTransform = new Transform3D().setPosition(1.6f, 1.4f, 1.6f);
        cameraTransform.lookAt(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        thumbCamera = new Camera3D().setFieldOfViewDegrees(40.0f).setNearFar(0.05f, 50.0f);
        thumbCamera.setAspectRatio(1.0f);
        cameraObject.addComponent(cameraTransform);
        cameraObject.addComponent(thumbCamera);
        scene.addGameObject(cameraObject);
        scene.addGameObject(targetObject);
    }

    private static GameObject buildSun() {
        GameObject sunObject = new GameObject("Sun");
        sunObject.addComponent(new Transform3D().lookAt(-0.5f, -1.0f, -0.3f, 0.0f, 1.0f, 0.0f));
        sunObject.addComponent(new DirectionalLight()
                .setColor(1.0f, 0.94f, 0.84f)
                .setIntensity(2.1f)
                .setAmbient(0.12f, 0.14f, 0.20f));
        return sunObject;
    }

    public OptionalInt get(String meshPath) {
        if (failedPaths.contains(meshPath)) {
            return OptionalInt.empty();
        }
        Entry existing = cache.get(meshPath);
        if (existing != null) {
            return OptionalInt.of(existing.glTextureName());
        }
        ensureInitialized();
        Optional<Entry> rendered = renderEntrySafely(meshPath);
        if (rendered.isEmpty()) {
            failedPaths.add(meshPath);
            return OptionalInt.empty();
        }
        putBounded(meshPath, rendered.get());
        return OptionalInt.of(rendered.get().glTextureName());
    }

    private Optional<Entry> renderEntrySafely(String meshPath) {
        try {
            return renderEntry(meshPath);
        } catch (RuntimeException error) {
            engine.logger().warn("[MeshThumbnailer] Thumbnail failed for " + meshPath + ": " + error.getMessage());
            return Optional.empty();
        }
    }

    private void putBounded(String key, Entry entry) {
        cache.put(key, entry);
        if (cache.size() <= MAX_ENTRIES) {
            return;
        }
        Iterator<Map.Entry<String, Entry>> iterator = cache.entrySet().iterator();
        Map.Entry<String, Entry> eldest = iterator.next();
        iterator.remove();
        destroyEntry(eldest.getValue());
    }

    private void destroyEntry(Entry entry) {
        backend.destroy(entry.renderTarget());
        backend.destroy(entry.colorTexture());
        backend.destroy(entry.depthTexture());
    }

    public void shutdown() {
        for (Entry entry : cache.values()) {
            destroyEntry(entry);
        }
        cache.clear();
        failedPaths.clear();
        if (initialized) {
            engine.shutdown();
            backend.shutdown();
            initialized = false;
        }
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        backend.initialize(new RenderSurface() {
            @Override public int framebufferWidth() { return THUMB_PIXEL_SIZE; }
            @Override public int framebufferHeight() { return THUMB_PIXEL_SIZE; }
        });
        engine.addScene(scene);
        ShaderLoader shaderLoader = ShaderLoader.autoDetect();
        ShaderWatcher shaderWatcher = new ShaderWatcher(shaderLoader.filesystemRoot());
        engine.addRenderSystem(new MeshRenderSystem(shaderLoader, shaderWatcher, engine.logger()));
        engine.initialize();
        BuiltinMeshes builtins = BuiltinMeshes.uploadAll(backend);
        engine.assets().register(new MeshAssetLoader(builtins));
        initialized = true;
    }

    private Optional<Entry> renderEntry(String meshPath) {
        prepareTarget(meshPath);
        if (targetRenderer.mesh().isEmpty()) {
            return Optional.empty();
        }
        TextureHandle color = backend.createTexture(new TextureDescriptor(THUMB_PIXEL_SIZE, THUMB_PIXEL_SIZE,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        TextureHandle depth = backend.createTexture(new TextureDescriptor(THUMB_PIXEL_SIZE, THUMB_PIXEL_SIZE,
                TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT, SamplerFilter.LINEAR));
        RenderTargetHandle target = backend.createRenderTarget(new RenderTargetDescriptor(
                THUMB_PIXEL_SIZE, THUMB_PIXEL_SIZE, List.of(color), Optional.of(depth)));
        renderInto(target);
        return Optional.of(new Entry(target, color, depth, backend.glTextureName(color)));
    }

    private void prepareTarget(String meshPath) {
        targetRenderer.setMeshPath(meshPath);
        targetRenderer.meshRef().clearCache();
        targetRenderer.setMaterials(List.of());
        targetTransform.setPosition(0.0f, 0.0f, 0.0f);
        targetTransform.setScale(1.0f, 1.0f, 1.0f);
        targetTransform.setRotation(new Quaternionf());
        targetRenderer.onLoad(engine);
    }

    private void renderInto(RenderTargetHandle target) {
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            scene.advanceTick();
            engine.render(List.of(thumbCamera), target, 0.0f);
        } finally {
            snapshot.restore();
        }
    }

    private record Entry(RenderTargetHandle renderTarget, TextureHandle colorTexture,
                         TextureHandle depthTexture, int glTextureName) {
    }
}
