package fr.epistudio.epysia.editor.preview;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphKind;
import fr.epistudio.epysia.graph.shader.ShaderGraphCompiler;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.postfx.PostEffectInsertionPoint;
import fr.epistudio.epysia.window.Window;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public final class ShaderGraphPreviewService {

    public static final String SPHERE_MESH = "preset:" + BuiltinMeshes.SPHERE;
    public static final String CUBE_MESH = "preset:" + BuiltinMeshes.CUBE;
    public static final String PLANE_MESH = "preset:" + BuiltinMeshes.UNIT_QUAD;
    public static final int NODE_PREVIEW_PIXELS = 64;

    private static final int MAIN_PREVIEW_PIXELS = 320;
    private static final long DEBOUNCE_NANOS = 180_000_000L;
    private static final long NODE_ANIMATION_INTERVAL_NANOS = 120_000_000L;
    private static final String MAIN_SURFACE_PATH = "epysiaPreview/main.surf.glsl";
    private static final String MAIN_POST_PATH = "epysiaPreview/main.post.glsl";
    private static final String REFERENCE_PATH = "epysiaPreview/reference.surf.glsl";
    private static final String POST_EFFECT_NAME = "Graph Preview";

    private final ShaderPreviewEngine surfaceEngine;
    private final ShaderPreviewEngine postEngine;
    private final ShaderGraphCompiler compiler = new ShaderGraphCompiler();
    private final NodePreviewCache nodeCache = new NodePreviewCache();
    private final Map<Path, String> meshPathsByGraph = new HashMap<>();
    private ShaderPreviewStage mainSurfaceStage;
    private ShaderPreviewStage mainPostStage;
    private ShaderPreviewStage nodeSurfaceStage;
    private ShaderPreviewStage nodePostStage;
    private PreviewRenderTarget mainTarget;
    private String mainAppliedKey = "";
    private String mainErrorMessage = "";
    private long mainPendingSinceNanos;
    private String mainPendingKey = "";
    private boolean initialized;

    public ShaderGraphPreviewService(Window window, OpenGlRenderBackend backend) {
        this.surfaceEngine = new ShaderPreviewEngine(window, backend, false);
        this.postEngine = new ShaderPreviewEngine(window, backend, true);
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        surfaceEngine.initialize();
        postEngine.initialize();
        mainSurfaceStage = registerStage(surfaceEngine, "PreviewMainSurface", SPHERE_MESH, true);
        nodeSurfaceStage = registerStage(surfaceEngine, "PreviewNodeSurface", PLANE_MESH, false);
        mainPostStage = registerStage(postEngine, "PreviewMainPost", PLANE_MESH, false);
        nodePostStage = registerStage(postEngine, "PreviewNodePost", PLANE_MESH, false);
        postEngine.publishSource(REFERENCE_PATH, ShaderPreviewReference.SURFACE_SOURCE);
        preparePostStage(mainPostStage, MAIN_POST_PATH);
        preparePostStage(nodePostStage, MAIN_POST_PATH);
        mainTarget = PreviewRenderTarget.create(surfaceEngine.backend(), MAIN_PREVIEW_PIXELS, MAIN_PREVIEW_PIXELS);
        initialized = true;
    }

    private static ShaderPreviewStage registerStage(ShaderPreviewEngine engine, String name,
                                                    String meshPath, boolean orbitEnabled) {
        ShaderPreviewStage stage = new ShaderPreviewStage(name, meshPath, orbitEnabled);
        engine.addScene(stage.scene());
        return stage;
    }

    private void preparePostStage(ShaderPreviewStage stage, String effectPath) {
        stage.setSurfaceShaderPath(REFERENCE_PATH);
        stage.scene().postEffects().add(POST_EFFECT_NAME, effectPath, PostEffectInsertionPoint.AFTER_TONEMAP);
    }

    public void beginFrame() {
        nodeCache.beginFrame(surfaceEngine.backend());
    }

    public String meshPathFor(Path graphPath) {
        return meshPathsByGraph.getOrDefault(graphPath, SPHERE_MESH);
    }

    public void setMeshPath(Path graphPath, String meshPath) {
        meshPathsByGraph.put(graphPath, meshPath);
    }

    public void orbitMain(float deltaYaw, float deltaPitch) {
        if (initialized) {
            mainSurfaceStage.orbit(deltaYaw, deltaPitch);
        }
    }

    public void zoomMain(float wheelDelta) {
        if (initialized) {
            mainSurfaceStage.zoom(wheelDelta);
        }
    }

    public Optional<String> mainErrorMessage() {
        return mainErrorMessage.isEmpty() ? Optional.empty() : Optional.of(mainErrorMessage);
    }

    public OptionalInt mainPreviewTexture(Path graphPath, GraphAsset asset, long nowNanos) {
        ensureInitialized();
        String key = GraphMainKey.of(asset) + '|' + meshPathFor(graphPath);
        if (!key.equals(mainAppliedKey) && debounceElapsed(key, nowNanos)) {
            rebuildMainPreview(graphPath, asset, key);
        } else if (!mainAppliedKey.isEmpty()) {
            refreshMainPreviewFrame(asset);
        }
        return mainAppliedKey.isEmpty() ? OptionalInt.empty() : OptionalInt.of(mainTarget.glTextureName());
    }

    private void refreshMainPreviewFrame(GraphAsset asset) {
        boolean surface = asset.kind() == GraphKind.SHADER_SURFACE;
        ShaderPreviewEngine engine = surface ? surfaceEngine : postEngine;
        ShaderPreviewStage stage = surface ? mainSurfaceStage : mainPostStage;
        engine.render(stage.scene(), stage.camera(), mainTarget);
    }

    private boolean debounceElapsed(String key, long nowNanos) {
        if (!key.equals(mainPendingKey)) {
            mainPendingKey = key;
            mainPendingSinceNanos = nowNanos;
            return false;
        }
        return nowNanos - mainPendingSinceNanos >= DEBOUNCE_NANOS;
    }

    private void rebuildMainPreview(Path graphPath, GraphAsset asset, String key) {
        boolean surface = asset.kind() == GraphKind.SHADER_SURFACE;
        ShaderPreviewEngine engine = surface ? surfaceEngine : postEngine;
        ShaderPreviewStage stage = surface ? mainSurfaceStage : mainPostStage;
        String shaderPath = surface ? MAIN_SURFACE_PATH : MAIN_POST_PATH;
        try {
            engine.publishSource(shaderPath, compiler.compile(asset, graphPath.getFileName().toString()));
            mainErrorMessage = "";
        } catch (EpysiaException failure) {
            mainErrorMessage = failure.getMessage();
            return;
        }
        applyMainStage(surface, stage, shaderPath, graphPath, engine);
        mainAppliedKey = key;
    }

    private void applyMainStage(boolean surface, ShaderPreviewStage stage, String shaderPath,
                                Path graphPath, ShaderPreviewEngine engine) {
        if (surface) {
            stage.setSurfaceShaderPath(shaderPath);
            stage.setMeshPath(meshPathFor(graphPath), engine.engine());
        }
        stage.reloadMesh(engine.engine());
        engine.render(stage.scene(), stage.camera(), mainTarget);
    }

    public OptionalInt nodePreviewTexture(Path graphPath, GraphAsset asset, int nodeId, String pinName) {
        ensureInitialized();
        NodePreviewCache.PreviewKey key = new NodePreviewCache.PreviewKey(graphPath, nodeId, pinName);
        String upstreamKey = GraphUpstreamKey.of(asset, nodeId, pinName);
        Optional<NodePreviewEntry> known = nodeCache.find(key);
        if (known.isPresent() && known.get().matches(upstreamKey)) {
            animateNodePreview(known.get(), asset);
            return textureOf(known.get());
        }
        if (!nodeCache.canRebuild()) {
            return known.map(ShaderGraphPreviewService::textureOf).orElseGet(OptionalInt::empty);
        }
        nodeCache.consumeRebuild();
        return textureOf(rebuildNodePreview(key, asset, nodeId, pinName, upstreamKey));
    }

    private void animateNodePreview(NodePreviewEntry entry, GraphAsset asset) {
        long nowNanos = System.nanoTime();
        if (entry.hasError() || !entry.animationDue(nowNanos, NODE_ANIMATION_INTERVAL_NANOS)) {
            return;
        }
        boolean surface = asset.kind() == GraphKind.SHADER_SURFACE;
        ShaderPreviewEngine engine = surface ? surfaceEngine : postEngine;
        renderNodeStage(surface, engine, nodeSlotPath(entry.slot(), surface), entry);
        entry.stampFrame(nowNanos);
    }

    private NodePreviewEntry rebuildNodePreview(NodePreviewCache.PreviewKey key, GraphAsset asset,
                                                int nodeId, String pinName, String upstreamKey) {
        boolean surface = asset.kind() == GraphKind.SHADER_SURFACE;
        ShaderPreviewEngine engine = surface ? surfaceEngine : postEngine;
        NodePreviewEntry entry = nodeCache.claim(key, engine.backend(), NODE_PREVIEW_PIXELS);
        String shaderPath = nodeSlotPath(entry.slot(), surface);
        try {
            engine.publishSource(shaderPath,
                    compiler.compilePreview(asset, nodeId, pinName, "node" + nodeId));
        } catch (EpysiaException failure) {
            entry.markFailed(upstreamKey, failure.getMessage());
            return entry;
        }
        renderNodeStage(surface, engine, shaderPath, entry);
        entry.markRendered(upstreamKey);
        entry.stampFrame(System.nanoTime());
        return entry;
    }

    private void renderNodeStage(boolean surface, ShaderPreviewEngine engine, String shaderPath,
                                 NodePreviewEntry entry) {
        ShaderPreviewStage stage = surface ? nodeSurfaceStage : nodePostStage;
        if (surface) {
            stage.setSurfaceShaderPath(shaderPath);
        } else {
            stage.scene().postEffects().effects().get(0).setShaderPath(shaderPath);
        }
        stage.reloadMesh(engine.engine());
        engine.render(stage.scene(), stage.camera(), entry.target());
    }

    private static String nodeSlotPath(int slot, boolean surface) {
        return "epysiaPreview/node" + slot + (surface ? ".surf.glsl" : ".post.glsl");
    }

    private static OptionalInt textureOf(NodePreviewEntry entry) {
        return entry.hasError() ? OptionalInt.empty() : OptionalInt.of(entry.target().glTextureName());
    }

    public Optional<String> nodeErrorMessage(Path graphPath, int nodeId, String pinName) {
        return nodeCache.find(new NodePreviewCache.PreviewKey(graphPath, nodeId, pinName))
                .flatMap(NodePreviewEntry::errorMessage);
    }

    public int liveNodeTargetCount() {
        return nodeCache.liveTargetCount();
    }

    public void invalidateGraph(Path graphPath) {
        nodeCache.invalidateGraph(graphPath);
        meshPathsByGraph.remove(graphPath);
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }
        nodeCache.shutdown(surfaceEngine.backend());
        mainTarget.destroy(surfaceEngine.backend());
        surfaceEngine.shutdown();
        postEngine.shutdown();
        initialized = false;
    }
}
