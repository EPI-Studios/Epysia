package fr.epistudio.epysia.render.vulkan;

public record ResolvedStorageImageBinding(int slotIndex, VulkanTexture texture, int mipLevel)
        implements ResolvedBinding {

    @Override
    public DescriptorSetIndex set() {
        return DescriptorSetIndex.STORAGE_IMAGE;
    }
}
