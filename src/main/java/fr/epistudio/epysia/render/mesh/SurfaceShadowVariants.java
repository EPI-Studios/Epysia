package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.render.shader.SurfaceShaderComposer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SurfaceShadowVariants {

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final Logger logger;
    private final String vertexPath;
    private final String fragmentPath;
    private final RenderState renderState;
    private final Runnable pipelineInvalidation;
    private record Variant(String surfacePath, boolean frozenTime, boolean skinned) {
    }

    private final Map<Variant, PipelineHandle> pipelines = new HashMap<>();

    private RenderBackend backend;
    private BindingSetLayout bindingLayout;
    private BindingSetLayout skinnedBindingLayout;
    private PipelineHandle basePipeline;

    SurfaceShadowVariants(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger,
                          String vertexPath, String fragmentPath, RenderState renderState,
                          Runnable pipelineInvalidation) {
        this.shaderLoader = shaderLoader;
        this.shaderWatcher = shaderWatcher;
        this.logger = logger;
        this.vertexPath = vertexPath;
        this.fragmentPath = fragmentPath;
        this.renderState = renderState;
        this.pipelineInvalidation = pipelineInvalidation;
    }

    void initialize(RenderBackend backend, BindingSetLayout bindingLayout, PipelineHandle basePipeline) {
        this.backend = backend;
        this.bindingLayout = bindingLayout;
        this.skinnedBindingLayout = withJointPalette(bindingLayout);
        this.basePipeline = basePipeline;
    }

    static BindingSetLayout withJointPalette(BindingSetLayout layout) {
        List<BindingSlot> slots = new ArrayList<>(layout.slots());
        slots.add(new BindingSlot(MeshShaderBindings.JOINT_PALETTE_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        return new BindingSetLayout(slots);
    }

    PipelineHandle pipelineFor(String surfacePath, boolean frozenTime, boolean skinned) {
        if (surfacePath.isEmpty() && !skinned) {
            return basePipeline;
        }
        return pipelines.computeIfAbsent(new Variant(surfacePath, frozenTime, skinned), this::buildPipeline);
    }

    private PipelineHandle buildPipeline(Variant variant) {
        try {
            PipelineHandle handle = backend.createPipeline(buildDescriptor(variant));
            registerHotReload(handle, variant);
            return handle;
        } catch (EpysiaException exception) {
            logger.error("Surface shadow pipeline failed for " + variant.surfacePath() + ", using base shadow", exception);
            return basePipeline;
        }
    }

    private PipelineDescriptor buildDescriptor(Variant variant) {
        return new PipelineDescriptor(loadShaderSource(variant), vertexLayoutFor(variant.skinned()),
                renderState, layoutFor(variant));
    }

    private static VertexLayout vertexLayoutFor(boolean skinned) {
        List<VertexAttribute> attributes = new ArrayList<>(List.of(
                new VertexAttribute(0, VertexFormat.FLOAT3, 0),
                new VertexAttribute(1, VertexFormat.FLOAT3, 12),
                new VertexAttribute(2, VertexFormat.FLOAT2, 24)));
        if (!skinned) {
            return new VertexLayout(attributes, MeshShaderBindings.VERTEX_STRIDE);
        }
        attributes.add(new VertexAttribute(4, VertexFormat.UINT16X4, MeshShaderBindings.VERTEX_STRIDE));
        attributes.add(new VertexAttribute(5, VertexFormat.FLOAT4, MeshShaderBindings.VERTEX_STRIDE + 8));
        return new VertexLayout(attributes, MeshShaderBindings.SKINNED_VERTEX_STRIDE);
    }

    private BindingSetLayout layoutFor(Variant variant) {
        List<BindingSlot> slots = new ArrayList<>(
                (variant.skinned() ? skinnedBindingLayout : bindingLayout).slots());
        if (!variant.surfacePath().isEmpty()) {
            SurfaceUniformBinder.appendSlots(slots,
                    SurfaceShaderComposer.parseUniforms(shaderLoader.load(variant.surfacePath())));
        }
        return new BindingSetLayout(slots);
    }

    private ShaderSource loadShaderSource(Variant variant) {
        return new ShaderSource(composeVertex(variant).source(), shaderLoader.load(fragmentPath).source());
    }

    private LoadedShader composeVertex(Variant variant) {
        LoadedShader vertex = composeSurface(shaderLoader.load(vertexPath), variant);
        return variant.skinned() ? SurfaceShaderComposer.injectSkinningDefine(vertex) : vertex;
    }

    private LoadedShader composeSurface(LoadedShader base, Variant variant) {
        if (variant.surfacePath().isEmpty()) {
            return base;
        }
        LoadedShader surface = shaderLoader.load(variant.surfacePath());
        return variant.frozenTime()
                ? SurfaceShaderComposer.composeFrozenShadowVertex(base, surface)
                : SurfaceShaderComposer.composeShadowVertex(base, surface);
    }

    private void registerHotReload(PipelineHandle handle, Variant variant) {
        if (!shaderWatcher.active()) {
            return;
        }
        shaderWatcher.watch(composeVertex(variant).dependencyPaths(), () -> reloadPipeline(handle, variant));
    }

    private void reloadPipeline(PipelineHandle handle, Variant variant) {
        try {
            backend.updatePipelineShaders(handle, loadShaderSource(variant));
            pipelineInvalidation.run();
            logger.info("Reloaded surface shadow pipeline: " + vertexPath + " + " + variant.surfacePath());
        } catch (EpysiaException exception) {
            logger.error("Surface shadow reload failed for " + variant.surfacePath()
                    + ", keeping previous program", exception);
        }
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        for (PipelineHandle handle : pipelines.values()) {
            if (handle != basePipeline) {
                backend.destroy(handle);
            }
        }
        pipelines.clear();
    }
}
