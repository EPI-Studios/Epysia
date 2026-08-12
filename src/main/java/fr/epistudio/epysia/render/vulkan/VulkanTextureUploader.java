package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.TextureKind;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCopy;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class VulkanTextureUploader {

    private final VulkanBufferFactory buffers;
    private final VulkanImmediateCommands immediateCommands;
    private final VulkanCommandSink frameSink;
    private final Consumer<VulkanBuffer> retireStaging;

    public VulkanTextureUploader(VulkanBufferFactory buffers, VulkanImmediateCommands immediateCommands,
                                 VulkanCommandSink frameSink, Consumer<VulkanBuffer> retireStaging) {
        this.buffers = buffers;
        this.immediateCommands = immediateCommands;
        this.frameSink = frameSink;
        this.retireStaging = retireStaging;
    }

    public void upload(VulkanTexture texture, ByteBuffer pixels) {
        VulkanBuffer staging = buffers.createReadback(pixels.remaining());
        MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), staging.mappedAddress(), pixels.remaining());
        frameSink.record(commandBuffer -> recordUpload(commandBuffer, texture, staging));
        retireStaging.accept(staging);
    }

    private void recordUpload(VkCommandBuffer commandBuffer, VulkanTexture texture, VulkanBuffer staging) {
        VulkanImageBarriers.transition(commandBuffer, texture, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(texture.aspectMask())
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            region.get(0).imageExtent().width(texture.width()).height(texture.height()).depth(1);
            VK10.vkCmdCopyBufferToImage(commandBuffer, staging.handle(), texture.image(),
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
        }
        VulkanImageBarriers.transition(commandBuffer, texture,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    public void generateMipmaps(VulkanTexture texture) {
        frameSink.record(commandBuffer -> recordMipmapChain(commandBuffer, texture));
    }

    private void recordMipmapChain(VkCommandBuffer commandBuffer, VulkanTexture texture) {
        VulkanImageBarriers.transition(commandBuffer, texture, VK10.VK_IMAGE_LAYOUT_GENERAL);
        int sourceWidth = texture.width();
        int sourceHeight = texture.height();
        for (int level = 1; level < texture.mipLevels(); level++) {
            int targetWidth = Math.max(1, sourceWidth / 2);
            int targetHeight = Math.max(1, sourceHeight / 2);
            blitLevel(commandBuffer, texture, level, sourceWidth, sourceHeight, targetWidth, targetHeight);
            sourceWidth = targetWidth;
            sourceHeight = targetHeight;
        }
        VulkanImageBarriers.transition(commandBuffer, texture,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    private void blitLevel(VkCommandBuffer commandBuffer, VulkanTexture texture, int level,
                           int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
            blit.get(0).srcSubresource().aspectMask(texture.aspectMask())
                    .mipLevel(level - 1).baseArrayLayer(0).layerCount(1);
            blit.get(0).dstSubresource().aspectMask(texture.aspectMask())
                    .mipLevel(level).baseArrayLayer(0).layerCount(1);
            blit.get(0).srcOffsets(1).set(sourceWidth, sourceHeight, 1);
            blit.get(0).dstOffsets(1).set(targetWidth, targetHeight, 1);
            VK10.vkCmdBlitImage(commandBuffer, texture.image(), VK10.VK_IMAGE_LAYOUT_GENERAL,
                    texture.image(), VK10.VK_IMAGE_LAYOUT_GENERAL, blit, VK10.VK_FILTER_LINEAR);
        }
    }

    public void copyRegion(VulkanTexture source, int sourceLayer, int sourceX, int sourceY,
                           VulkanTexture destination, int destinationLayer,
                           int destinationX, int destinationY, int width, int height) {
        frameSink.record(commandBuffer -> recordCopy(commandBuffer, source, sourceLayer,
                sourceX, sourceY, destination, destinationLayer, destinationX, destinationY,
                width, height));
    }

    private void recordCopy(VkCommandBuffer commandBuffer, VulkanTexture source, int sourceLayer,
                            int sourceX, int sourceY, VulkanTexture destination, int destinationLayer,
                            int destinationX, int destinationY, int width, int height) {
        int sourceLayout = source.currentLayout();
        int destinationLayout = destination.currentLayout();
        VulkanImageBarriers.transition(commandBuffer, source, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        VulkanImageBarriers.transition(commandBuffer, destination,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        recordCopyRegion(commandBuffer, source, sourceLayer, sourceX, sourceY, destination,
                destinationLayer, destinationX, destinationY, width, height);
        VulkanImageBarriers.transition(commandBuffer, source, restoreLayout(sourceLayout));
        VulkanImageBarriers.transition(commandBuffer, destination, restoreLayout(destinationLayout));
    }

    private static int restoreLayout(int previous) {
        return previous == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : previous;
    }

    private void recordCopyRegion(VkCommandBuffer commandBuffer, VulkanTexture source, int sourceLayer,
                                  int sourceX, int sourceY, VulkanTexture destination,
                                  int destinationLayer, int destinationX, int destinationY,
                                  int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
            region.get(0).srcSubresource().aspectMask(source.aspectMask()).mipLevel(0)
                    .baseArrayLayer(layerOf(source, sourceLayer)).layerCount(1);
            region.get(0).dstSubresource().aspectMask(destination.aspectMask()).mipLevel(0)
                    .baseArrayLayer(layerOf(destination, destinationLayer)).layerCount(1);
            region.get(0).srcOffset().set(sourceX, sourceY, depthOffsetOf(source, sourceLayer));
            region.get(0).dstOffset().set(destinationX, destinationY,
                    depthOffsetOf(destination, destinationLayer));
            region.get(0).extent().width(width).height(height).depth(1);
            VK10.vkCmdCopyImage(commandBuffer, source.image(), VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    destination.image(), VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
        }
    }

    private static int layerOf(VulkanTexture texture, int requested) {
        return texture.kind() == TextureKind.TEXTURE_3D ? 0 : Math.max(0, requested);
    }

    private static int depthOffsetOf(VulkanTexture texture, int requested) {
        return texture.kind() == TextureKind.TEXTURE_3D ? Math.max(0, requested) : 0;
    }
}
