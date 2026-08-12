package fr.epistudio.epysia.render.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;

public final class VulkanImageBarriers {

    private VulkanImageBarriers() {
    }

    public static void transition(VkCommandBuffer commandBuffer, VulkanTexture texture, int newLayout) {
        if (texture.allLayersAt(newLayout)) {
            return;
        }
        transitionRange(commandBuffer, texture.image(), texture.aspectMask(), texture.mipLevels(),
                0, texture.layerCount(), texture.currentLayout(), newLayout);
        texture.recordLayout(newLayout);
    }

    public static void discardInto(VkCommandBuffer commandBuffer, VulkanTexture texture,
                                   int layer, int newLayout) {
        int base = Math.max(0, layer);
        int count = layer < 0 ? texture.layerCount() : 1;
        transitionRange(commandBuffer, texture.image(), texture.aspectMask(), texture.mipLevels(),
                base, count, VK10.VK_IMAGE_LAYOUT_UNDEFINED, newLayout);
        if (layer < 0) {
            texture.recordLayout(newLayout);
            return;
        }
        texture.recordLayout(layer, newLayout);
    }

    public static void transitionLayer(VkCommandBuffer commandBuffer, VulkanTexture texture,
                                       int layer, int newLayout) {
        if (layer < 0) {
            transition(commandBuffer, texture, newLayout);
            return;
        }
        if (texture.currentLayout(layer) == newLayout) {
            return;
        }
        transitionRange(commandBuffer, texture.image(), texture.aspectMask(), texture.mipLevels(),
                layer, 1, texture.currentLayout(layer), newLayout);
        texture.recordLayout(layer, newLayout);
    }

    public static void transitionRange(VkCommandBuffer commandBuffer, long image, int aspectMask,
                                       int mipLevels, int baseLayer, int layerCount,
                                       int oldLayout, int newLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack)
                    .sType$Default()
                    .srcStageMask(stageOf(oldLayout))
                    .srcAccessMask(accessOf(oldLayout))
                    .dstStageMask(stageOf(newLayout))
                    .dstAccessMask(accessOf(newLayout))
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(aspectMask)
                    .baseMipLevel(0)
                    .levelCount(mipLevels)
                    .baseArrayLayer(baseLayer)
                    .layerCount(layerCount);
            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pImageMemoryBarriers(barrier);
            VK13.vkCmdPipelineBarrier2(commandBuffer, dependency);
        }
    }

    public static void transitionRaw(VkCommandBuffer commandBuffer, long image, int aspectMask,
                                     int mipLevels, int layerCount, int oldLayout, int newLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack)
                    .sType$Default()
                    .srcStageMask(stageOf(oldLayout))
                    .srcAccessMask(accessOf(oldLayout))
                    .dstStageMask(stageOf(newLayout))
                    .dstAccessMask(accessOf(newLayout))
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(aspectMask)
                    .baseMipLevel(0)
                    .levelCount(mipLevels)
                    .baseArrayLayer(0)
                    .layerCount(layerCount);
            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pImageMemoryBarriers(barrier);
            VK13.vkCmdPipelineBarrier2(commandBuffer, dependency);
        }
    }

    private static long stageOf(int layout) {
        return switch (layout) {
            case VK10.VK_IMAGE_LAYOUT_UNDEFINED -> VK13.VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT;
            case VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL ->
                    VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT;
            case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                 VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL ->
                    VK13.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK13.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT;
            case VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL ->
                    VK13.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                            | VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT;
            case VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL ->
                    VK13.VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT;
            case KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT;
            default -> VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT;
        };
    }

    private static long accessOf(int layout) {
        return switch (layout) {
            case VK10.VK_IMAGE_LAYOUT_UNDEFINED, KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> 0L;
            case VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL ->
                    VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT | VK13.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT;
            case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL ->
                    VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                            | VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
            case VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL ->
                    VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
            case VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT;
            case VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> VK13.VK_ACCESS_2_TRANSFER_READ_BIT;
            case VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT;
            default -> VK13.VK_ACCESS_2_SHADER_READ_BIT | VK13.VK_ACCESS_2_SHADER_WRITE_BIT;
        };
    }
}
