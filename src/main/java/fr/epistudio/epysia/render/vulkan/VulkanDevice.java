package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.EXTVertexAttributeRobustness;
import org.lwjgl.vulkan.KHRPipelineExecutableProperties;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDevicePipelineExecutablePropertiesFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceVertexAttributeRobustnessFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan13Features;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;

public final class VulkanDevice implements AutoCloseable {

    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final VkQueue graphicsQueue;
    private final int graphicsQueueFamily;
    private final long allocator;
    private final VulkanDeviceLimits limits;
    private final String deviceName;

    public VulkanDevice(VulkanInstance instance, long surface) {
        this.physicalDevice = selectPhysicalDevice(instance, surface);
        this.graphicsQueueFamily = findGraphicsQueueFamily(physicalDevice, surface);
        this.device = createLogicalDevice();
        this.graphicsQueue = fetchQueue(graphicsQueueFamily);
        this.allocator = createAllocator(instance);
        this.limits = readLimits();
        this.deviceName = readDeviceName();
    }

    public VkDevice handle() {
        return device;
    }

    public VkPhysicalDevice physicalDevice() {
        return physicalDevice;
    }

    public VkQueue graphicsQueue() {
        return graphicsQueue;
    }

    public int graphicsQueueFamily() {
        return graphicsQueueFamily;
    }

    public long allocator() {
        return allocator;
    }

    public VulkanDeviceLimits limits() {
        return limits;
    }

    public String deviceName() {
        return deviceName;
    }

    public void waitIdle() {
        VK10.vkDeviceWaitIdle(device);
    }

    private VkPhysicalDevice selectPhysicalDevice(VulkanInstance instance, long surface) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer candidates = enumeratePhysicalDevices(instance, stack);
            VkPhysicalDevice best = null;
            int bestScore = Integer.MIN_VALUE;
            while (candidates.hasRemaining()) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(candidates.get(), instance.handle());
                int score = scoreOf(candidate, surface, stack);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best == null || bestScore < 0) {
                throw new EpysiaException("No Vulkan 1.3 device with graphics and present support found.");
            }
            return best;
        }
    }

    private static PointerBuffer enumeratePhysicalDevices(VulkanInstance instance, MemoryStack stack) {
        IntBuffer count = stack.mallocInt(1);
        VulkanResult.check(VK10.vkEnumeratePhysicalDevices(instance.handle(), count, null),
                "vkEnumeratePhysicalDevices");
        if (count.get(0) == 0) {
            throw new EpysiaException("No Vulkan physical devices are available.");
        }
        PointerBuffer devices = stack.mallocPointer(count.get(0));
        VulkanResult.check(VK10.vkEnumeratePhysicalDevices(instance.handle(), count, devices),
                "vkEnumeratePhysicalDevices");
        return devices;
    }

    private static int scoreOf(VkPhysicalDevice candidate, long surface, MemoryStack stack) {
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
        VK10.vkGetPhysicalDeviceProperties(candidate, properties);
        if (properties.apiVersion() < VK13.VK_API_VERSION_1_3) {
            return Integer.MIN_VALUE;
        }
        if (!hasGraphicsAndPresent(candidate, surface)) {
            return Integer.MIN_VALUE;
        }
        return properties.deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU ? 1000 : 100;
    }

    private static boolean hasGraphicsAndPresent(VkPhysicalDevice candidate, long surface) {
        try {
            findGraphicsQueueFamily(candidate, surface);
            return true;
        } catch (EpysiaException missing) {
            return false;
        }
    }

    private static int findGraphicsQueueFamily(VkPhysicalDevice candidate, long surface) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.malloc(count.get(0), stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, families);
            for (int family = 0; family < families.capacity(); family++) {
                if (supportsGraphicsAndPresent(candidate, surface, families.get(family), family, stack)) {
                    return family;
                }
            }
        }
        throw new EpysiaException("No queue family supports both graphics and presentation.");
    }

    private static boolean supportsGraphicsAndPresent(VkPhysicalDevice candidate, long surface,
                                                      VkQueueFamilyProperties family, int familyIndex,
                                                      MemoryStack stack) {
        if ((family.queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) == 0
                || (family.queueFlags() & VK10.VK_QUEUE_COMPUTE_BIT) == 0) {
            return false;
        }
        IntBuffer supported = stack.mallocInt(1);
        KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(candidate, familyIndex, surface, supported);
        return supported.get(0) == VK10.VK_TRUE;
    }

    private VkDevice createLogicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueCreateInfos(stack))
                    .ppEnabledExtensionNames(stack.pointers(
                            stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME),
                            stack.UTF8(EXTVertexAttributeRobustness.VK_EXT_VERTEX_ATTRIBUTE_ROBUSTNESS_EXTENSION_NAME),
                            stack.UTF8(KHRPipelineExecutableProperties.VK_KHR_PIPELINE_EXECUTABLE_PROPERTIES_EXTENSION_NAME)))
                    .pNext(requiredFeatures(stack).address());
            PointerBuffer created = stack.mallocPointer(1);
            VulkanResult.check(VK10.vkCreateDevice(physicalDevice, createInfo, null, created),
                    "vkCreateDevice");
            return new VkDevice(created.get(0), physicalDevice, createInfo);
        }
    }

    private VkDeviceQueueCreateInfo.Buffer queueCreateInfos(MemoryStack stack) {
        return VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(graphicsQueueFamily)
                .pQueuePriorities(stack.floats(1.0f));
    }

    private static VkPhysicalDeviceFeatures2 requiredFeatures(MemoryStack stack) {
        VkPhysicalDevicePipelineExecutablePropertiesFeaturesKHR executableProperties =
                VkPhysicalDevicePipelineExecutablePropertiesFeaturesKHR.calloc(stack)
                        .sType$Default()
                        .pipelineExecutableInfo(true);
        VkPhysicalDeviceVertexAttributeRobustnessFeaturesEXT attributeRobustness =
                VkPhysicalDeviceVertexAttributeRobustnessFeaturesEXT.calloc(stack)
                        .sType$Default()
                        .vertexAttributeRobustness(!Boolean.getBoolean("epysia.vulkan.noAttributeRobustness"))
                        .pNext(executableProperties.address());
        VkPhysicalDeviceVulkan13Features thirteen = VkPhysicalDeviceVulkan13Features.calloc(stack)
                .sType$Default()
                .dynamicRendering(true)
                .synchronization2(true)
                .shaderDemoteToHelperInvocation(true)
                .pNext(attributeRobustness.address());
        VkPhysicalDeviceVulkan12Features twelve = VkPhysicalDeviceVulkan12Features.calloc(stack)
                .sType$Default()
                .timelineSemaphore(true)
                .drawIndirectCount(true)
                .pNext(thirteen.address());
        VkPhysicalDeviceVulkan11Features eleven = VkPhysicalDeviceVulkan11Features.calloc(stack)
                .sType$Default()
                .shaderDrawParameters(true)
                .pNext(twelve.address());
        VkPhysicalDeviceFeatures2 features = VkPhysicalDeviceFeatures2.calloc(stack)
                .sType$Default()
                .pNext(eleven.address());
        enableCoreFeatures(features);
        return features;
    }

    private static void enableCoreFeatures(VkPhysicalDeviceFeatures2 features) {
        features.features()
                .multiDrawIndirect(true)
                .drawIndirectFirstInstance(true)
                .samplerAnisotropy(true)
                .depthClamp(true)
                .independentBlend(true)
                .fillModeNonSolid(true)
                .fragmentStoresAndAtomics(true)
                .vertexPipelineStoresAndAtomics(true)
                .shaderStorageImageWriteWithoutFormat(true)
                .shaderInt64(true);
    }

    private VkQueue fetchQueue(int family) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer queue = stack.mallocPointer(1);
            VK10.vkGetDeviceQueue(device, family, 0, queue);
            return new VkQueue(queue.get(0), device);
        }
    }

    private long createAllocator(VulkanInstance instance) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VmaVulkanFunctions functions = VmaVulkanFunctions.calloc(stack)
                    .set(instance.handle(), device);
            VmaAllocatorCreateInfo createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .physicalDevice(physicalDevice)
                    .device(device)
                    .instance(instance.handle())
                    .vulkanApiVersion(VK13.VK_API_VERSION_1_3)
                    .pVulkanFunctions(functions);
            PointerBuffer created = stack.mallocPointer(1);
            VulkanResult.check(Vma.vmaCreateAllocator(createInfo, created), "vmaCreateAllocator");
            return created.get(0);
        }
    }

    private VulkanDeviceLimits readLimits() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
            return new VulkanDeviceLimits(
                    (int) properties.limits().minUniformBufferOffsetAlignment(),
                    (int) properties.limits().minStorageBufferOffsetAlignment(),
                    properties.limits().timestampPeriod(),
                    (int) properties.limits().maxSamplerAnisotropy());
        }
    }

    private String readDeviceName() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
            return properties.deviceNameString();
        }
    }

    @Override
    public void close() {
        Vma.vmaDestroyAllocator(allocator);
        VK10.vkDestroyDevice(device, null);
    }
}
