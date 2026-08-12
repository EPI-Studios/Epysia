package fr.epistudio.epysia.render.vulkan;

public record ResolvedBufferBinding(int slotIndex, DescriptorSetIndex set, VulkanBuffer buffer,
                                    long byteOffset, long byteSize) implements ResolvedBinding {

    public DynamicBufferBinding asDynamic() {
        return new DynamicBufferBinding(slotIndex, buffer, byteOffset, byteSize);
    }
}
