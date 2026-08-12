package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

public final class VulkanSwapchain implements AutoCloseable {

    private static final int PREFERRED_FORMAT = VK10.VK_FORMAT_B8G8R8A8_UNORM;

    private final VulkanDevice device;
    private final long surface;
    private final boolean vsync;

    private long swapchain = VK10.VK_NULL_HANDLE;
    private long[] images = new long[0];
    private long[] imageViews = new long[0];
    private int surfaceFormat = PREFERRED_FORMAT;
    private int width;
    private int height;

    public VulkanSwapchain(VulkanDevice device, long surface, boolean vsync, int width, int height) {
        this.device = device;
        this.surface = surface;
        this.vsync = vsync;
        recreate(width, height);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int format() {
        return surfaceFormat;
    }

    public int imageCount() {
        return images.length;
    }

    public long image(int index) {
        return images[index];
    }

    public long imageView(int index) {
        return imageViews[index];
    }

    public void recreate(int requestedWidth, int requestedHeight) {
        device.waitIdle();
        destroyImageViews();
        long previous = swapchain;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = readCapabilities(stack);
            VkExtent2D extent = chooseExtent(capabilities, requestedWidth, requestedHeight, stack);
            this.width = extent.width();
            this.height = extent.height();
            this.surfaceFormat = chooseFormat(stack);
            this.swapchain = createSwapchain(capabilities, extent, previous, stack);
        }
        if (previous != VK10.VK_NULL_HANDLE) {
            KHRSwapchain.vkDestroySwapchainKHR(device.handle(), previous, null);
        }
        fetchImages();
        createImageViews();
    }

    private VkSurfaceCapabilitiesKHR readCapabilities(MemoryStack stack) {
        VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
        VulkanResult.check(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                device.physicalDevice(), surface, capabilities), "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
        return capabilities;
    }

    private static VkExtent2D chooseExtent(VkSurfaceCapabilitiesKHR capabilities,
                                           int requestedWidth, int requestedHeight, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return capabilities.currentExtent();
        }
        return VkExtent2D.malloc(stack)
                .width(clamp(requestedWidth, capabilities.minImageExtent().width(),
                        capabilities.maxImageExtent().width()))
                .height(clamp(requestedHeight, capabilities.minImageExtent().height(),
                        capabilities.maxImageExtent().height()));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int chooseFormat(MemoryStack stack) {
        IntBuffer count = stack.mallocInt(1);
        KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(device.physicalDevice(), surface, count, null);
        VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
        KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(device.physicalDevice(), surface, count, formats);
        for (int index = 0; index < formats.capacity(); index++) {
            if (formats.get(index).format() == PREFERRED_FORMAT) {
                return PREFERRED_FORMAT;
            }
        }
        return formats.get(0).format();
    }

    private int choosePresentMode(MemoryStack stack) {
        if (vsync) {
            return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
        }
        IntBuffer available = readPresentModes(stack);
        if (supports(available, KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR)) {
            return KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
        }
        if (supports(available, KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR)) {
            return KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
        }
        return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
    }

    private IntBuffer readPresentModes(MemoryStack stack) {
        IntBuffer count = stack.mallocInt(1);
        KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(device.physicalDevice(), surface, count, null);
        IntBuffer modes = stack.mallocInt(count.get(0));
        KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(device.physicalDevice(), surface, count, modes);
        return modes;
    }

    private static boolean supports(IntBuffer modes, int wanted) {
        for (int index = 0; index < modes.capacity(); index++) {
            if (modes.get(index) == wanted) {
                return true;
            }
        }
        return false;
    }

    private long createSwapchain(VkSurfaceCapabilitiesKHR capabilities, VkExtent2D extent,
                                 long previous, MemoryStack stack) {
        VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType$Default()
                .surface(surface)
                .minImageCount(desiredImageCount(capabilities))
                .imageFormat(surfaceFormat)
                .imageColorSpace(KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                .imageExtent(extent)
                .imageArrayLayers(1)
                .imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                        | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
                        | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                .imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                .preTransform(capabilities.currentTransform())
                .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(choosePresentMode(stack))
                .clipped(true)
                .oldSwapchain(previous);
        LongBuffer created = stack.mallocLong(1);
        VulkanResult.check(KHRSwapchain.vkCreateSwapchainKHR(device.handle(), createInfo, null, created),
                "vkCreateSwapchainKHR");
        return created.get(0);
    }

    private static int desiredImageCount(VkSurfaceCapabilitiesKHR capabilities) {
        int desired = capabilities.minImageCount() + 1;
        if (capabilities.maxImageCount() > 0) {
            return Math.min(desired, capabilities.maxImageCount());
        }
        return desired;
    }

    private void fetchImages() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            KHRSwapchain.vkGetSwapchainImagesKHR(device.handle(), swapchain, count, null);
            LongBuffer fetched = stack.mallocLong(count.get(0));
            KHRSwapchain.vkGetSwapchainImagesKHR(device.handle(), swapchain, count, fetched);
            images = new long[count.get(0)];
            fetched.get(images);
        }
    }

    private void createImageViews() {
        imageViews = new long[images.length];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int index = 0; index < images.length; index++) {
                imageViews[index] = createImageView(images[index], stack);
            }
        }
    }

    private long createImageView(long image, MemoryStack stack) {
        VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                .sType$Default()
                .image(image)
                .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                .format(surfaceFormat);
        createInfo.subresourceRange()
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .levelCount(1)
                .layerCount(1);
        LongBuffer created = stack.mallocLong(1);
        VulkanResult.check(VK10.vkCreateImageView(device.handle(), createInfo, null, created),
                "vkCreateImageView");
        return created.get(0);
    }

    public int acquireNextImage(long imageAvailableSemaphore) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer imageIndex = stack.mallocInt(1);
            int result = KHRSwapchain.vkAcquireNextImageKHR(device.handle(), swapchain,
                    Long.MAX_VALUE, imageAvailableSemaphore, VK10.VK_NULL_HANDLE, imageIndex);
            if (result == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                return -1;
            }
            if (result != VK10.VK_SUCCESS && result != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                throw new EpysiaException("vkAcquireNextImageKHR failed with " + VulkanResult.describe(result));
            }
            return imageIndex.get(0);
        }
    }

    public int present(int imageIndex, long waitSemaphore) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(waitSemaphore))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchain))
                    .pImageIndices(stack.ints(imageIndex));
            int result = KHRSwapchain.vkQueuePresentKHR(device.graphicsQueue(), presentInfo);
            if (result != KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR && result != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                VulkanResult.check(result, "vkQueuePresentKHR");
            }
            return result;
        }
    }

    private void destroyImageViews() {
        for (long view : imageViews) {
            VK10.vkDestroyImageView(device.handle(), view, null);
        }
        imageViews = new long[0];
    }

    @Override
    public void close() {
        destroyImageViews();
        if (swapchain != VK10.VK_NULL_HANDLE) {
            KHRSwapchain.vkDestroySwapchainKHR(device.handle(), swapchain, null);
            swapchain = VK10.VK_NULL_HANDLE;
        }
    }
}
