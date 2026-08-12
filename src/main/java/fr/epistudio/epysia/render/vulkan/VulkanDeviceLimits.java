package fr.epistudio.epysia.render.vulkan;

public record VulkanDeviceLimits(
        int uniformBufferOffsetAlignment,
        int storageBufferOffsetAlignment,
        float timestampPeriodNanos,
        int maximumSamplerAnisotropy
) {
}
