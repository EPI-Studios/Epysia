package fr.epistudio.epysia.render.vulkan;

public sealed interface ResolvedBinding
        permits ResolvedBufferBinding, ResolvedSampledBinding, ResolvedStorageImageBinding {

    int slotIndex();

    DescriptorSetIndex set();
}
