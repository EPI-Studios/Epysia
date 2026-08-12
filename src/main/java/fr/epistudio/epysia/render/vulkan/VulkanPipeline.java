package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.VertexLayout;
import org.lwjgl.vulkan.VK10;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class VulkanPipeline {

    private final VulkanPipelineLayout layout;
    private final Optional<RenderState> state;
    private final Optional<VertexLayout> vertexLayout;
    private final Optional<VertexLayout> instanceLayout;
    private final Map<RenderingFormats, Long> graphicsVariants = new HashMap<>();

    private long vertexModule;
    private long fragmentModule;
    private long computePipeline = VK10.VK_NULL_HANDLE;

    public VulkanPipeline(VulkanPipelineLayout layout, Optional<RenderState> state,
                          Optional<VertexLayout> vertexLayout, Optional<VertexLayout> instanceLayout) {
        this.layout = layout;
        this.state = state;
        this.vertexLayout = vertexLayout;
        this.instanceLayout = instanceLayout;
    }

    public VulkanPipelineLayout layout() {
        return layout;
    }

    public RenderState state() {
        return state.orElseThrow(() -> new IllegalStateException("Compute pipeline has no render state."));
    }

    public boolean isCompute() {
        return computePipeline != VK10.VK_NULL_HANDLE;
    }

    public long computePipeline() {
        return computePipeline;
    }

    public void useComputePipeline(long pipeline) {
        this.computePipeline = pipeline;
    }

    public Optional<VertexLayout> vertexLayout() {
        return vertexLayout;
    }

    public Optional<VertexLayout> instanceLayout() {
        return instanceLayout;
    }

    public int vertexStride() {
        return vertexLayout.map(VertexLayout::byteStride).orElse(0);
    }

    public int instanceStride() {
        return instanceLayout.map(VertexLayout::byteStride).orElse(0);
    }

    public long vertexModule() {
        return vertexModule;
    }

    public long fragmentModule() {
        return fragmentModule;
    }

    public void useModules(long vertex, long fragment) {
        this.vertexModule = vertex;
        this.fragmentModule = fragment;
    }

    public Map<RenderingFormats, Long> graphicsVariants() {
        return graphicsVariants;
    }
}
