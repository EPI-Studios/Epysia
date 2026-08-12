package fr.epistudio.epysia.render.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.IntBuffer;
import java.util.List;
import java.util.Optional;

public final class VulkanDescriptorBinder {

    private final VkCommandBuffer commandBuffer;
    private final int frameSlot;
    private final int overrideSlot;
    private final Optional<DynamicBufferBinding> override;

    public VulkanDescriptorBinder(VkCommandBuffer commandBuffer, int frameSlot, int overrideSlot,
                                  Optional<DynamicBufferBinding> override) {
        this.commandBuffer = commandBuffer;
        this.frameSlot = frameSlot;
        this.overrideSlot = overrideSlot;
        this.override = override;
    }

    public void bind(VulkanPipelineLayout layout, VulkanDescriptorSets set, int bindPoint) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VK10.vkCmdBindDescriptorSets(commandBuffer, bindPoint, layout.pipelineLayout(), 0,
                    stack.longs(set.descriptorSets()), dynamicOffsets(set, stack));
        }
    }

    private IntBuffer dynamicOffsets(VulkanDescriptorSets set, MemoryStack stack) {
        int total = set.dynamicOffsetCount();
        if (total == 0) {
            return null;
        }
        IntBuffer offsets = stack.mallocInt(total);
        fill(offsets, set.uniformBindings(), true);
        fill(offsets, set.storageBindings(), false);
        return offsets.flip();
    }

    private void fill(IntBuffer offsets, List<DynamicBufferBinding> bindings, boolean overridable) {
        for (DynamicBufferBinding binding : bindings) {
            offsets.put((int) resolveOffset(binding, overridable));
        }
    }

    private long resolveOffset(DynamicBufferBinding binding, boolean overridable) {
        if (overridable && override.isPresent() && binding.slotIndex() == overrideSlot) {
            return override.get().dynamicOffset(frameSlot);
        }
        return binding.dynamicOffset(frameSlot);
    }
}
