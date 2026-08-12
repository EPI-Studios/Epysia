package fr.epistudio.epysia.render.vulkan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VulkanBindingSet {

    private final List<ResolvedBinding> bindings;
    private final List<VulkanTexture> sampledTextures;
    private final List<VulkanTexture> storageImages;
    private final Map<VulkanPipelineLayout, VulkanDescriptorSets> resolved = new HashMap<>();

    public VulkanBindingSet(List<ResolvedBinding> bindings, List<VulkanTexture> sampledTextures,
                            List<VulkanTexture> storageImages) {
        this.bindings = List.copyOf(bindings);
        this.sampledTextures = List.copyOf(sampledTextures);
        this.storageImages = List.copyOf(storageImages);
    }

    public List<ResolvedBinding> bindings() {
        return bindings;
    }

    public List<VulkanTexture> sampledTextures() {
        return sampledTextures;
    }

    public List<VulkanTexture> storageImages() {
        return storageImages;
    }

    public Map<VulkanPipelineLayout, VulkanDescriptorSets> resolved() {
        return resolved;
    }
}
