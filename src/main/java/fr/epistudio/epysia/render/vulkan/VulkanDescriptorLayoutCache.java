package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;

import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VulkanDescriptorLayoutCache implements AutoCloseable {

    private final VulkanDevice device;
    private final Map<BindingSetLayout, VulkanPipelineLayout> cache = new HashMap<>();

    public VulkanDescriptorLayoutCache(VulkanDevice device) {
        this.device = device;
    }

    public VulkanPipelineLayout layoutFor(BindingSetLayout bindingLayout) {
        return cache.computeIfAbsent(bindingLayout, this::create);
    }

    public static int descriptorTypeOf(BindingType type) {
        return switch (type) {
            case UNIFORM_BUFFER -> VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;
            case STORAGE_BUFFER -> VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;
            case SAMPLED_TEXTURE_2D, SAMPLED_TEXTURE_3D, SAMPLED_TEXTURE_CUBE, SAMPLED_TEXTURE_ARRAY ->
                    VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case STORAGE_IMAGE -> VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        };
    }

    private VulkanPipelineLayout create(BindingSetLayout bindingLayout) {
        long[] setLayouts = new long[DescriptorSetIndex.COUNT];
        for (DescriptorSetIndex set : DescriptorSetIndex.values()) {
            setLayouts[set.setNumber()] = createSetLayout(slotsOf(bindingLayout, set));
        }
        return new VulkanPipelineLayout(setLayouts, createPipelineLayout(setLayouts), bindingLayout.slots());
    }

    private static List<BindingSlot> slotsOf(BindingSetLayout bindingLayout, DescriptorSetIndex set) {
        return bindingLayout.slots().stream()
                .filter(slot -> DescriptorSetIndex.of(slot.type()) == set)
                .sorted((left, right) -> Integer.compare(left.slotIndex(), right.slotIndex()))
                .toList();
    }

    private long createSetLayout(List<BindingSlot> slots) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(slots.size(), stack);
            for (int index = 0; index < slots.size(); index++) {
                bindings.get(index)
                        .binding(slots.get(index).slotIndex())
                        .descriptorType(descriptorTypeOf(slots.get(index).type()))
                        .descriptorCount(1)
                        .stageFlags(VK10.VK_SHADER_STAGE_ALL);
            }
            VkDescriptorSetLayoutCreateInfo createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(slots.isEmpty() ? null : bindings);
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateDescriptorSetLayout(device.handle(), createInfo, null, created),
                    "vkCreateDescriptorSetLayout");
            return created.get(0);
        }
    }

    private long createPipelineLayout(long[] setLayouts) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(stack.longs(setLayouts));
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreatePipelineLayout(device.handle(), createInfo, null, created),
                    "vkCreatePipelineLayout");
            return created.get(0);
        }
    }

    @Override
    public void close() {
        for (VulkanPipelineLayout layout : cache.values()) {
            VK10.vkDestroyPipelineLayout(device.handle(), layout.pipelineLayout(), null);
            for (long setLayout : layout.descriptorSetLayouts()) {
                VK10.vkDestroyDescriptorSetLayout(device.handle(), setLayout, null);
            }
        }
        cache.clear();
    }
}
