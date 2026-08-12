package fr.epistudio.epysia.render.vulkan;

import java.util.List;

public record VulkanDescriptorSets(
        long[] descriptorSets,
        long[] owningPools,
        List<DynamicBufferBinding> uniformBindings,
        List<DynamicBufferBinding> storageBindings
) {

    public VulkanDescriptorSets {
        uniformBindings = List.copyOf(uniformBindings);
        storageBindings = List.copyOf(storageBindings);
    }

    public int dynamicOffsetCount() {
        return uniformBindings.size() + storageBindings.size();
    }
}
