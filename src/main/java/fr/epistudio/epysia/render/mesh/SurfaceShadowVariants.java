package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
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
    private record Variant(String surfacePath, boolean frozenTime) {
    }

    private final Map<Variant, PipelineHandle> pipelines = new HashMap<>();

    private RenderBackend backend;
    private BindingSetLayout bindingLayout;
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
        this.basePipeline = basePipeline;
    }

    PipelineHandle pipelineFor(String surfacePath, boolean frozenTime) {
        if (surfacePath.isEmpty()) {
            return basePipeline;
        }
        return pipelines.computeIfAbsent(new Variant(surfacePath, frozenTime), this::buildPipeline);
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
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexAttribute normal = new VertexAttribute(1, VertexFormat.FLOAT3, 12);
        VertexAttribute uv = new VertexAttribute(2, VertexFormat.FLOAT2, 24);
        VertexLayout layout = new VertexLayout(List.of(position, normal, uv), MeshShaderBindings.VERTEX_STRIDE);
        return new PipelineDescriptor(loadShaderSource(variant), layout, renderState, layoutFor(variant.surfacePath()));
    }

    private BindingSetLayout layoutFor(String surfacePath) {
        List<BindingSlot> slots = new ArrayList<>(bindingLayout.slots());
        SurfaceUniformBinder.appendSlots(slots, SurfaceShaderComposer.parseUniforms(shaderLoader.load(surfacePath)));
        return new BindingSetLayout(slots);
    }

    private ShaderSource loadShaderSource(Variant variant) {
        return new ShaderSource(composeVertex(variant).source(), shaderLoader.load(fragmentPath).source());
    }

    private LoadedShader composeVertex(Variant variant) {
        LoadedShader base = shaderLoader.load(vertexPath);
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
