package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.PassClear;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkViewport;

public final class VulkanPassRecorder {

    private final VkCommandBuffer commandBuffer;

    public VulkanPassRecorder(VkCommandBuffer commandBuffer) {
        this.commandBuffer = commandBuffer;
    }

    public void beginScreen(long imageView, int width, int height, PassClear clear) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfo.Buffer color = VkRenderingAttachmentInfo.calloc(1, stack);
            configureColorAttachment(color.get(0), imageView, clear, stack);
            VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(stack)
                    .sType$Default()
                    .layerCount(1)
                    .pColorAttachments(color);
            configureArea(renderingInfo, width, height);
            VK13.vkCmdBeginRendering(commandBuffer, renderingInfo);
        }
    }

    public void beginOffscreen(VulkanRenderTarget target, PassClear clear) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(stack)
                    .sType$Default()
                    .layerCount(1);
            attachColors(renderingInfo, target, clear, stack);
            attachDepth(renderingInfo, target, clear, stack);
            configureArea(renderingInfo, target.width(), target.height());
            VK13.vkCmdBeginRendering(commandBuffer, renderingInfo);
        }
    }

    private static void attachColors(VkRenderingInfo renderingInfo, VulkanRenderTarget target,
                                     PassClear clear, MemoryStack stack) {
        if (target.colorViews().isEmpty()) {
            return;
        }
        VkRenderingAttachmentInfo.Buffer colors =
                VkRenderingAttachmentInfo.calloc(target.colorViews().size(), stack);
        for (int index = 0; index < target.colorViews().size(); index++) {
            configureColorAttachment(colors.get(index), target.colorViews().get(index), clear, stack);
        }
        renderingInfo.pColorAttachments(colors);
    }

    private static void attachDepth(VkRenderingInfo renderingInfo, VulkanRenderTarget target,
                                    PassClear clear, MemoryStack stack) {
        if (target.depthView().isEmpty()) {
            return;
        }
        VkRenderingAttachmentInfo depth = VkRenderingAttachmentInfo.calloc(stack)
                .sType$Default()
                .imageView(target.depthView().get())
                .imageLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .loadOp(clear.clearDepth() ? VK10.VK_ATTACHMENT_LOAD_OP_CLEAR
                        : VK10.VK_ATTACHMENT_LOAD_OP_LOAD)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);
        depth.clearValue().depthStencil().depth(clear.depth()).stencil(clear.stencil());
        renderingInfo.pDepthAttachment(depth);
    }

    private static void configureColorAttachment(VkRenderingAttachmentInfo attachment, long imageView,
                                                 PassClear clear, MemoryStack stack) {
        attachment.sType$Default()
                .imageView(imageView)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(clear.clearColor() ? VK10.VK_ATTACHMENT_LOAD_OP_CLEAR
                        : VK10.VK_ATTACHMENT_LOAD_OP_LOAD)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);
        attachment.clearValue().color().float32(stack.floats(clear.red(), clear.green(),
                clear.blue(), clear.alpha()));
    }

    private static void configureArea(VkRenderingInfo renderingInfo, int width, int height) {
        renderingInfo.renderArea().offset().set(0, 0);
        renderingInfo.renderArea().extent().set(width, height);
    }

    public void setViewport(int x, int y, int width, int height, int targetHeight,
                            boolean flipVertically) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(x)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            applyVerticalOrientation(viewport.get(0), y, height, flipVertically);
            viewport.get(0).width(width);
            VK10.vkCmdSetViewport(commandBuffer, 0, viewport);
        }
    }

    private static void applyVerticalOrientation(VkViewport viewport, int y, int height,
                                                 boolean flipVertically) {
        if (flipVertically) {
            viewport.y(y + height).height(-height);
            return;
        }
        viewport.y(y).height(height);
    }

    public void setScissor(int x, int y, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset().set(Math.max(0, x), Math.max(0, y));
            scissor.get(0).extent().set(Math.max(0, width), Math.max(0, height));
            VK10.vkCmdSetScissor(commandBuffer, 0, scissor);
        }
    }

    public void memoryBarrier() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack)
                    .sType$Default()
                    .srcStageMask(VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                    .srcAccessMask(VK13.VK_ACCESS_2_MEMORY_WRITE_BIT)
                    .dstStageMask(VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                    .dstAccessMask(VK13.VK_ACCESS_2_MEMORY_READ_BIT | VK13.VK_ACCESS_2_MEMORY_WRITE_BIT);
            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pMemoryBarriers(barrier);
            VK13.vkCmdPipelineBarrier2(commandBuffer, dependency);
        }
    }
}
