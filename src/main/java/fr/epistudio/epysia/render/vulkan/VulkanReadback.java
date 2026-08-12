package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.PixelColor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class VulkanReadback {

    private static final int COLOR_COMPONENTS = 4;

    private final VulkanDevice device;
    private final VulkanBufferFactory buffers;
    private final VulkanImmediateCommands immediateCommands;

    public VulkanReadback(VulkanDevice device, VulkanBufferFactory buffers,
                          VulkanImmediateCommands immediateCommands) {
        this.device = device;
        this.buffers = buffers;
        this.immediateCommands = immediateCommands;
    }

    public void readPixels(VulkanTexture texture, int x, int y, int width, int height,
                           ByteBuffer destination) {
        long byteSize = (long) width * height * VulkanFormats.bytesPerPixel(texture.format());
        VulkanBuffer staging = copyToStaging(texture, 0, x, y, width, height, byteSize);
        MemoryUtil.memCopy(staging.mappedAddress(), MemoryUtil.memAddress(destination),
                Math.min(byteSize, destination.remaining()));
        buffers.destroy(staging);
    }

    public PixelColor readPixelFloat(VulkanTexture texture, int x, int y) {
        long byteSize = VulkanFormats.bytesPerPixel(texture.format());
        VulkanBuffer staging = copyToStaging(texture, 0, x, y, 1, 1, byteSize);
        FloatBuffer floats = MemoryUtil.memFloatBuffer(staging.mappedAddress(),
                (int) (byteSize / Float.BYTES));
        PixelColor color = toPixelColor(floats);
        buffers.destroy(staging);
        return color;
    }

    private static PixelColor toPixelColor(FloatBuffer floats) {
        if (floats.remaining() >= COLOR_COMPONENTS) {
            return new PixelColor(floats.get(0), floats.get(1), floats.get(2), floats.get(3));
        }
        float single = floats.remaining() > 0 ? floats.get(0) : 0.0f;
        return new PixelColor(single, single, single, 1.0f);
    }

    public void readTexture(VulkanTexture texture, int mipLevel, FloatBuffer destination) {
        int width = Math.max(1, texture.width() >> mipLevel);
        int height = Math.max(1, texture.height() >> mipLevel);
        long byteSize = (long) width * height * VulkanFormats.bytesPerPixel(texture.format());
        VulkanBuffer staging = copyToStaging(texture, mipLevel, 0, 0, width, height, byteSize);
        MemoryUtil.memCopy(staging.mappedAddress(), MemoryUtil.memAddress(destination),
                Math.min(byteSize, (long) destination.remaining() * Float.BYTES));
        buffers.destroy(staging);
    }

    private VulkanBuffer copyToStaging(VulkanTexture texture, int mipLevel, int x, int y,
                                       int width, int height, long byteSize) {
        VulkanBuffer staging = buffers.createReadback(byteSize);
        immediateCommands.submit(commandBuffer ->
                recordCopy(commandBuffer, texture, staging, mipLevel, x, y, width, height));
        return staging;
    }

    private void recordCopy(VkCommandBuffer commandBuffer, VulkanTexture texture,
                            VulkanBuffer staging, int mipLevel, int x, int y, int width, int height) {
        int previousLayout = texture.currentLayout();
        VulkanImageBarriers.transition(commandBuffer, texture, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(texture.aspectMask())
                    .mipLevel(mipLevel).baseArrayLayer(0).layerCount(1);
            region.get(0).imageOffset().set(x, y, 0);
            region.get(0).imageExtent().width(width).height(height).depth(1);
            VK10.vkCmdCopyImageToBuffer(commandBuffer, texture.image(),
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, staging.handle(), region);
        }
        VulkanImageBarriers.transition(commandBuffer, texture, restoreLayout(previousLayout));
    }

    private static int restoreLayout(int previous) {
        return previous == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : previous;
    }
}
