package fr.epistudio.epysia.render.vulkan;

public record ResolvedSampledBinding(int slotIndex, VulkanTexture texture) implements ResolvedBinding {

    @Override
    public DescriptorSetIndex set() {
        return DescriptorSetIndex.SAMPLED_TEXTURE;
    }
}
