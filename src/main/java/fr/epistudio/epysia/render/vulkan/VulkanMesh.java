package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.IndexFormat;

public record VulkanMesh(VulkanBuffer vertexBuffer, VulkanBuffer indexBuffer, int firstIndex,
                         int indexCount, IndexFormat indexFormat) {
}
