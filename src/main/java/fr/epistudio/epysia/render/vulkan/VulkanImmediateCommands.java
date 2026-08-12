package fr.epistudio.epysia.render.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.LongBuffer;
import java.util.function.Consumer;

public final class VulkanImmediateCommands implements AutoCloseable {

    private final VulkanDevice device;
    private final long commandPool;
    private final VkCommandBuffer commandBuffer;
    private final long fence;

    private boolean recording;

    public VulkanImmediateCommands(VulkanDevice device) {
        this.device = device;
        this.commandPool = createCommandPool();
        this.commandBuffer = allocateCommandBuffer();
        this.fence = createFence();
    }

    public void submit(Consumer<VkCommandBuffer> recorder) {
        recorder.accept(beginScoped());
        flushScoped();
    }

    public VkCommandBuffer beginScoped() {
        if (recording) {
            return commandBuffer;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            beginRecording(stack);
        }
        recording = true;
        return commandBuffer;
    }

    private static int diagnosticFlushes;

    public static int diagnosticFlushes() {
        return diagnosticFlushes;
    }

    public void flushScoped() {
        if (!recording) {
            return;
        }
        diagnosticFlushes++;
        recording = false;
        VulkanResult.check(VK10.vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer));
            VulkanResult.check(VK10.vkQueueSubmit(device.graphicsQueue(), submitInfo, fence),
                    "vkQueueSubmit");
            awaitCompletion(stack);
        }
    }

    private void beginRecording(MemoryStack stack) {
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType$Default()
                .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        VulkanResult.check(VK10.vkBeginCommandBuffer(commandBuffer, beginInfo), "vkBeginCommandBuffer");
    }

    private void awaitCompletion(MemoryStack stack) {
        LongBuffer pending = stack.longs(fence);
        VK10.vkWaitForFences(device.handle(), pending, true, Long.MAX_VALUE);
        VK10.vkResetFences(device.handle(), pending);
        VK10.vkResetCommandPool(device.handle(), commandPool, 0);
    }

    private long createCommandPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT)
                    .queueFamilyIndex(device.graphicsQueueFamily());
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateCommandPool(device.handle(), createInfo, null, created),
                    "vkCreateCommandPool");
            return created.get(0);
        }
    }

    private VkCommandBuffer allocateCommandBuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer allocated = stack.mallocPointer(1);
            VulkanResult.check(VK10.vkAllocateCommandBuffers(device.handle(), allocateInfo, allocated),
                    "vkAllocateCommandBuffers");
            return new VkCommandBuffer(allocated.get(0), device.handle());
        }
    }

    private long createFence() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFenceCreateInfo createInfo = VkFenceCreateInfo.calloc(stack).sType$Default();
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateFence(device.handle(), createInfo, null, created),
                    "vkCreateFence");
            return created.get(0);
        }
    }

    @Override
    public void close() {
        VK10.vkDestroyFence(device.handle(), fence, null);
        VK10.vkDestroyCommandPool(device.handle(), commandPool, null);
    }
}
