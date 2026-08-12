package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.BindingSlot;

import java.util.List;

public record VulkanPipelineLayout(long[] descriptorSetLayouts, long pipelineLayout,
                                   List<BindingSlot> declaredSlots) {

    public VulkanPipelineLayout {
        declaredSlots = List.copyOf(declaredSlots);
    }

    public long descriptorSetLayout(DescriptorSetIndex set) {
        return descriptorSetLayouts[set.setNumber()];
    }
}
