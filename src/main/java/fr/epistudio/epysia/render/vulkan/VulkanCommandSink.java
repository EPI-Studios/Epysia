package fr.epistudio.epysia.render.vulkan;

import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.function.Consumer;

public interface VulkanCommandSink {

    void record(Consumer<VkCommandBuffer> recorder);
}
