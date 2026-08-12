package fr.epistudio.epysia.render.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.LongBuffer;

public final class VulkanFrameRing implements AutoCloseable {

    public static final int FRAMES_IN_FLIGHT = 3;

    private final VulkanDevice device;
    private final long[] commandPools = new long[FRAMES_IN_FLIGHT];
    private final VkCommandBuffer[] commandBuffers = new VkCommandBuffer[FRAMES_IN_FLIGHT];
    private final long[] inFlightFences = new long[FRAMES_IN_FLIGHT];
    private final long[] imageAvailableSemaphores = new long[FRAMES_IN_FLIGHT];
    private long[] renderFinishedSemaphores = new long[0];

    private int frameSlot;

    public VulkanFrameRing(VulkanDevice device, int swapchainImageCount) {
        this.device = device;
        for (int slot = 0; slot < FRAMES_IN_FLIGHT; slot++) {
            commandPools[slot] = createCommandPool();
            commandBuffers[slot] = allocateCommandBuffer(commandPools[slot]);
            inFlightFences[slot] = createSignalledFence();
            imageAvailableSemaphores[slot] = createSemaphore();
        }
        resizePresentSemaphores(swapchainImageCount);
    }

    public int frameSlot() {
        return frameSlot;
    }

    public VkCommandBuffer commandBuffer() {
        return commandBuffers[frameSlot];
    }

    public long imageAvailableSemaphore() {
        return imageAvailableSemaphores[frameSlot];
    }

    public long renderFinishedSemaphore(int imageIndex) {
        return renderFinishedSemaphores[imageIndex];
    }

    public void resizePresentSemaphores(int swapchainImageCount) {
        destroyPresentSemaphores();
        renderFinishedSemaphores = new long[swapchainImageCount];
        for (int index = 0; index < swapchainImageCount; index++) {
            renderFinishedSemaphores[index] = createSemaphore();
        }
    }

    public void waitForSlot() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer fence = stack.longs(inFlightFences[frameSlot]);
            VK10.vkWaitForFences(device.handle(), fence, true, Long.MAX_VALUE);
            VK10.vkResetFences(device.handle(), fence);
        }
        VK10.vkResetCommandPool(device.handle(), commandPools[frameSlot], 0);
    }

    public void beginRecording() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VulkanResult.check(VK10.vkBeginCommandBuffer(commandBuffer(), beginInfo),
                    "vkBeginCommandBuffer");
        }
    }

    public void submit(int imageIndex, boolean waitOnAcquire) {
        VulkanResult.check(VK10.vkEndCommandBuffer(commandBuffer()), "vkEndCommandBuffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer()));
            if (waitOnAcquire) {
                submitInfo.waitSemaphoreCount(1)
                        .pWaitSemaphores(stack.longs(imageAvailableSemaphore()))
                        .pWaitDstStageMask(stack.ints(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                        .pSignalSemaphores(stack.longs(renderFinishedSemaphores[imageIndex]));
            }
            VulkanResult.check(VK10.vkQueueSubmit(device.graphicsQueue(), submitInfo,
                    inFlightFences[frameSlot]), "vkQueueSubmit");
        }
    }

    public void advance() {
        frameSlot = (frameSlot + 1) % FRAMES_IN_FLIGHT;
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

    private VkCommandBuffer allocateCommandBuffer(long pool) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(pool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer allocated = stack.mallocPointer(1);
            VulkanResult.check(VK10.vkAllocateCommandBuffers(device.handle(), allocateInfo, allocated),
                    "vkAllocateCommandBuffers");
            return new VkCommandBuffer(allocated.get(0), device.handle());
        }
    }

    private long createSignalledFence() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFenceCreateInfo createInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateFence(device.handle(), createInfo, null, created),
                    "vkCreateFence");
            return created.get(0);
        }
    }

    private long createSemaphore() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo createInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateSemaphore(device.handle(), createInfo, null, created),
                    "vkCreateSemaphore");
            return created.get(0);
        }
    }

    private void destroyPresentSemaphores() {
        for (long semaphore : renderFinishedSemaphores) {
            VK10.vkDestroySemaphore(device.handle(), semaphore, null);
        }
        renderFinishedSemaphores = new long[0];
    }

    @Override
    public void close() {
        destroyPresentSemaphores();
        for (int slot = 0; slot < FRAMES_IN_FLIGHT; slot++) {
            VK10.vkDestroySemaphore(device.handle(), imageAvailableSemaphores[slot], null);
            VK10.vkDestroyFence(device.handle(), inFlightFences[slot], null);
            VK10.vkDestroyCommandPool(device.handle(), commandPools[slot], null);
        }
    }
}
