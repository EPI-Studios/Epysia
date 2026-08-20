package fr.epistudio.epysia.render.decal;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.SceneTexture;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.BlendMode;
import fr.epistudio.epysia.render.backend.CullMode;
import fr.epistudio.epysia.render.backend.DepthTest;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.mesh.CubeMesh;
import fr.epistudio.epysia.render.mesh.MeshShaderBindings;
import fr.epistudio.epysia.render.mesh.MeshUploader;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DecalRenderSystem implements RenderSystem {

    public static final int DECAL_ORDER = 220;
    public static final RenderPass DECAL = RenderPasses.register("DECAL", DECAL_ORDER);

    private static final String VERTEX_PATH = "decal/decal.vert.glsl";
    private static final String FRAGMENT_PATH = "decal/decal.frag.glsl";
    private static final int POSITION_STRIDE = MeshShaderBindings.VERTEX_STRIDE;

    private final ShaderLoader shaderLoader;
    private final Logger logger;
    private final Map<DecalBlend, PipelineHandle> pipelines = new EnumMap<>(DecalBlend.class);
    private final Map<Decal, DecalResources> resources = new IdentityHashMap<>();
    private final List<Decal> visited = new ArrayList<>();
    private final Matrix4f scratchModel = new Matrix4f();
    private final Matrix4f scratchInverseModel = new Matrix4f();
    private final Matrix4f scratchViewProjection = new Matrix4f();
    private final Matrix4f scratchInverseViewProjection = new Matrix4f();
    private RenderBackend backend;
    private StageConfigurer stageConfigurer;
    private UploadedMesh cube;
    private boolean warnedAboutPrepass;

    public DecalRenderSystem(ShaderLoader shaderLoader, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.logger = logger;
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        this.stageConfigurer = configurer;
        cube = MeshUploader.upload(backend, CubeMesh.data());
        for (DecalBlend blend : DecalBlend.values()) {
            pipelines.put(blend, createPipeline(blend));
        }
        configurer.bindStageTargetFollowing(DECAL, RenderPasses.OPAQUE_3D, PassClear.none());
    }

    private PipelineHandle createPipeline(DecalBlend blend) {
        VertexLayout layout = new VertexLayout(
                List.of(new VertexAttribute(0, VertexFormat.FLOAT3, 0)), POSITION_STRIDE);
        RenderState state = new RenderState(Topology.TRIANGLES, DepthTest.DISABLED,
                blendModeOf(blend), CullMode.FRONT, false);
        return backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(shaderLoader.load(VERTEX_PATH).source(),
                        shaderLoader.load(FRAGMENT_PATH).source()),
                layout, state, DecalResources.layout()));
    }

    private static BlendMode blendModeOf(DecalBlend blend) {
        return switch (blend) {
            case ALPHA -> BlendMode.ALPHA_BLEND;
            case MULTIPLY -> BlendMode.MULTIPLY;
            case ADDITIVE -> BlendMode.ADDITIVE;
        };
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        Optional<Camera3D> camera = context.primaryCamera();
        if (camera.isEmpty() || sceneTextures().isEmpty()) {
            warnAboutMissingPrepass(scene);
            return;
        }
        camera.get().viewProjection(context.interpolationAlpha()).get(scratchViewProjection);
        scratchViewProjection.invert(scratchInverseViewProjection);
        visited.clear();
        for (Decal decal : scene.componentsOf(Decal.class)) {
            submit(decal, frame);
        }
        dropUnvisited();
    }

    private void submit(Decal decal, FrameBuilder frame) {
        if (!decal.enabled() || decal.owner().isEmpty()) {
            return;
        }
        Transform3D transform = decal.owner().get().getComponentOrNull(Transform3D.class);
        TextureHandle texture = liveTexture(decal);
        if (transform == null || texture == null) {
            return;
        }
        visited.add(decal);
        scratchModel.set(transform.worldMatrix());
        scratchModel.invert(scratchInverseModel);
        DecalResources decalResources = resourcesFor(decal, texture);
        decalResources.write(scratchViewProjection, scratchInverseViewProjection, scratchModel,
                scratchInverseModel, decal);
        backend.writeBuffer(decalResources.ubo(), decalResources.staging(), 0L);
        frame.submit(DECAL, DrawCommand.of(pipelines.get(decal.blend()), cube.submeshes().get(0).handle(),
                decalResources.bindings(), decal.sortOrder()));
    }

    private TextureHandle liveTexture(Decal decal) {
        TextureHandle handle = resolveTexture(decal).orElse(null);
        if (handle == null) {
            return null;
        }
        if (backend.hasTexture(handle)) {
            return handle;
        }
        forget(decal);
        return null;
    }

    private Optional<TextureHandle> resolveTexture(Decal decal) {
        Optional<TextureHandle> cached = decal.textureHandle();
        if (cached.isPresent()) {
            return cached;
        }
        return stageConfigurer.assetRegistry().flatMap(registry -> decal.texture().resolve(registry));
    }

    private void forget(Decal decal) {
        decal.texture().clearCache();
        DecalResources stale = resources.remove(decal);
        if (stale != null) {
            stale.destroy(backend);
        }
    }

    private DecalResources resourcesFor(Decal decal, TextureHandle texture) {
        DecalResources existing = resources.get(decal);
        if (existing != null && existing.matches(texture, depthTexture(), normalTexture())) {
            return existing;
        }
        if (existing != null) {
            existing.destroy(backend);
        }
        DecalResources created = new DecalResources(backend, texture, depthTexture(), normalTexture());
        resources.put(decal, created);
        return created;
    }

    private void dropUnvisited() {
        resources.keySet().removeIf(decal -> {
            if (visited.contains(decal)) {
                return false;
            }
            resources.get(decal).destroy(backend);
            return true;
        });
    }

    private void warnAboutMissingPrepass(Scene scene) {
        if (warnedAboutPrepass || normalTexture() != null || scene.componentsOf(Decal.class).isEmpty()) {
            return;
        }
        warnedAboutPrepass = true;
        logger.warn("[DecalRenderSystem] Decals need the depth prepass, turn it on in the project settings");
    }

    private Optional<TextureHandle> sceneTextures() {
        if (depthTexture() == null || normalTexture() == null) {
            return Optional.empty();
        }
        return Optional.of(depthTexture());
    }

    private TextureHandle depthTexture() {
        return stageConfigurer.sceneTexture(SceneTexture.SCENE_DEPTH).orElse(null);
    }

    private TextureHandle normalTexture() {
        return stageConfigurer.sceneTexture(SceneTexture.SCENE_NORMAL).orElse(null);
    }

    @Override
    public void shutdown(RenderBackend renderBackend) {
        for (DecalResources decalResources : resources.values()) {
            decalResources.destroy(renderBackend);
        }
        resources.clear();
        if (cube != null) {
            cube.destroy(renderBackend);
        }
        logger.info("[DecalRenderSystem] shut down");
    }
}
