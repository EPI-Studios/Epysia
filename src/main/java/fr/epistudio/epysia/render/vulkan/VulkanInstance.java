package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

public final class VulkanInstance implements AutoCloseable {

    private static final String VALIDATION_PROPERTY = "epysia.vulkan.validation";
    private static final String VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";
    private static final String ENGINE_NAME = "Epysia";

    private final Queue<String> validationMessages = new ConcurrentLinkedQueue<>();
    private final VkInstance instance;
    private final boolean validationEnabled;
    private final long debugMessenger;

    public VulkanInstance() {
        this.validationEnabled = Boolean.getBoolean(VALIDATION_PROPERTY) && validationLayerAvailable();
        this.instance = createInstance();
        this.debugMessenger = validationEnabled ? createDebugMessenger() : VK10.VK_NULL_HANDLE;
    }

    private static boolean validationLayerAvailable() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            VK10.vkEnumerateInstanceLayerProperties(count, null);
            VkLayerProperties.Buffer layers = VkLayerProperties.malloc(count.get(0), stack);
            VK10.vkEnumerateInstanceLayerProperties(count, layers);
            for (int index = 0; index < layers.capacity(); index++) {
                if (VALIDATION_LAYER.equals(layers.get(index).layerNameString())) {
                    return true;
                }
            }
            return false;
        }
    }

    public List<String> drainValidationMessages() {
        List<String> drained = List.copyOf(validationMessages);
        validationMessages.clear();
        return drained;
    }

    public VkInstance handle() {
        return instance;
    }

    public boolean validationEnabled() {
        return validationEnabled;
    }

    private VkInstance createInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(applicationInfo(stack))
                    .ppEnabledExtensionNames(requiredExtensions(stack))
                    .ppEnabledLayerNames(requestedLayers(stack));
            PointerBuffer created = stack.mallocPointer(1);
            VulkanResult.check(VK10.vkCreateInstance(createInfo, null, created),
                    "vkCreateInstance");
            return new VkInstance(created.get(0), createInfo);
        }
    }

    private VkApplicationInfo applicationInfo(MemoryStack stack) {
        return VkApplicationInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8(ENGINE_NAME))
                .pEngineName(stack.UTF8(ENGINE_NAME))
                .apiVersion(VK13.VK_API_VERSION_1_3);
    }

    private PointerBuffer requiredExtensions(MemoryStack stack) {
        PointerBuffer surfaceExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
        if (surfaceExtensions == null) {
            throw new EpysiaException("GLFW reported no Vulkan surface extensions.");
        }
        if (!validationEnabled) {
            return surfaceExtensions;
        }
        PointerBuffer combined = stack.mallocPointer(surfaceExtensions.remaining() + 1);
        combined.put(surfaceExtensions);
        combined.put(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
        return combined.flip();
    }

    private PointerBuffer requestedLayers(MemoryStack stack) {
        if (!validationEnabled) {
            return null;
        }
        return stack.pointers(stack.UTF8(VALIDATION_LAYER));
    }

    private long createDebugMessenger() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsMessengerCreateInfoEXT createInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                    .sType(EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                    .messageSeverity(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                    .messageType(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                            | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                    .pfnUserCallback(debugCallback());
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(instance, createInfo, null, created),
                    "vkCreateDebugUtilsMessengerEXT");
            return created.get(0);
        }
    }

    private VkDebugUtilsMessengerCallbackEXT debugCallback() {
        return VkDebugUtilsMessengerCallbackEXT.create((severity, types, dataPointer, userData) -> {
            VkDebugUtilsMessengerCallbackDataEXT data =
                    VkDebugUtilsMessengerCallbackDataEXT.create(dataPointer);
            validationMessages.add(MemoryUtil.memUTF8(data.pMessage()));
            return VK10.VK_FALSE;
        });
    }

    @Override
    public void close() {
        if (debugMessenger != VK10.VK_NULL_HANDLE) {
            EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
        }
        VK10.vkDestroyInstance(instance, null);
    }
}
