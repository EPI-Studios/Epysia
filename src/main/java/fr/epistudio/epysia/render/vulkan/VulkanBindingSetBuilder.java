package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.StorageImageBinding;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class VulkanBindingSetBuilder {

    private final VulkanDevice device;
    private final VulkanDescriptorAllocator allocator;
    private final VulkanTextureFactory textureFactory;

    public VulkanBindingSetBuilder(VulkanDevice device, VulkanDescriptorAllocator allocator,
                                   VulkanTextureFactory textureFactory) {
        this.device = device;
        this.allocator = allocator;
        this.textureFactory = textureFactory;
    }

    public VulkanBindingSet describe(BindingSetDescriptor descriptor,
                                     Function<BufferHandle, VulkanBuffer> bufferLookup,
                                     Function<TextureHandle, VulkanTexture> textureLookup) {
        List<ResolvedBinding> resolved = descriptor.bindings().stream()
                .map(binding -> resolveBinding(binding, bufferLookup, textureLookup))
                .toList();
        return new VulkanBindingSet(resolved, texturesOf(resolved, ResolvedSampledBinding.class),
                texturesOf(resolved, ResolvedStorageImageBinding.class));
    }

    private static ResolvedBinding resolveBinding(Binding binding,
                                                  Function<BufferHandle, VulkanBuffer> bufferLookup,
                                                  Function<TextureHandle, VulkanTexture> textureLookup) {
        return switch (binding.resource()) {
            case UniformBufferBinding uniform -> new ResolvedBufferBinding(binding.slotIndex(),
                    DescriptorSetIndex.UNIFORM_BUFFER, bufferLookup.apply(uniform.buffer()),
                    uniform.byteOffset(), uniform.byteSize());
            case StorageBufferBinding storage -> new ResolvedBufferBinding(binding.slotIndex(),
                    DescriptorSetIndex.STORAGE_BUFFER, bufferLookup.apply(storage.buffer()),
                    storage.byteOffset(), storage.byteSize());
            case SampledTextureBinding sampled -> new ResolvedSampledBinding(binding.slotIndex(),
                    textureLookup.apply(sampled.texture()));
            case StorageImageBinding image -> new ResolvedStorageImageBinding(binding.slotIndex(),
                    textureLookup.apply(image.texture()), image.mipLevel());
        };
    }

    private static List<VulkanTexture> texturesOf(List<ResolvedBinding> resolved,
                                                  Class<? extends ResolvedBinding> kind) {
        return resolved.stream()
                .filter(kind::isInstance)
                .map(VulkanBindingSetBuilder::textureOf)
                .toList();
    }

    private static VulkanTexture textureOf(ResolvedBinding binding) {
        return switch (binding) {
            case ResolvedSampledBinding sampled -> sampled.texture();
            case ResolvedStorageImageBinding image -> image.texture();
            case ResolvedBufferBinding ignored ->
                    throw new IllegalArgumentException("Buffer bindings carry no texture.");
        };
    }

    public VulkanDescriptorSets resolveFor(VulkanPipelineLayout layout, List<ResolvedBinding> bindings) {
        long[] descriptorSets = new long[DescriptorSetIndex.COUNT];
        long[] owningPools = new long[DescriptorSetIndex.COUNT];
        allocateSets(layout, descriptorSets, owningPools);
        List<ResolvedBinding> declared = declaredBy(layout, bindings);
        writeDescriptors(descriptorSets, declared);
        return new VulkanDescriptorSets(descriptorSets, owningPools,
                dynamicOf(declared, DescriptorSetIndex.UNIFORM_BUFFER),
                dynamicOf(declared, DescriptorSetIndex.STORAGE_BUFFER));
    }

    private static List<DynamicBufferBinding> dynamicOf(List<ResolvedBinding> declared,
                                                        DescriptorSetIndex set) {
        return declared.stream()
                .filter(binding -> binding.set() == set)
                .map(binding -> ((ResolvedBufferBinding) binding).asDynamic())
                .sorted(Comparator.comparingInt(DynamicBufferBinding::slotIndex))
                .toList();
    }

    private static List<ResolvedBinding> declaredBy(VulkanPipelineLayout layout,
                                                    List<ResolvedBinding> bindings) {
        Set<DeclaredSlot> declared = layout.declaredSlots().stream()
                .map(slot -> new DeclaredSlot(slot.slotIndex(), DescriptorSetIndex.of(slot.type())))
                .collect(Collectors.toSet());
        return bindings.stream()
                .filter(binding -> declared.contains(
                        new DeclaredSlot(binding.slotIndex(), binding.set())))
                .toList();
    }

    private void allocateSets(VulkanPipelineLayout layout, long[] descriptorSets, long[] owningPools) {
        for (DescriptorSetIndex set : DescriptorSetIndex.values()) {
            AllocatedDescriptorSet allocated = allocator.allocate(layout.descriptorSetLayout(set));
            descriptorSets[set.setNumber()] = allocated.descriptorSet();
            owningPools[set.setNumber()] = allocated.pool();
        }
    }

    private void writeDescriptors(long[] descriptorSets, List<ResolvedBinding> bindings) {
        if (bindings.isEmpty()) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(bindings.size(), stack);
            for (int index = 0; index < bindings.size(); index++) {
                configureWrite(writes.get(index), descriptorSets, bindings.get(index), stack);
            }
            VK10.vkUpdateDescriptorSets(device.handle(), writes, null);
        }
    }

    private void configureWrite(VkWriteDescriptorSet write, long[] descriptorSets,
                                ResolvedBinding binding, MemoryStack stack) {
        switch (binding) {
            case ResolvedBufferBinding buffer -> configureBufferWrite(write, descriptorSets, buffer, stack);
            case ResolvedSampledBinding sampled -> configureSampledWrite(write, descriptorSets, sampled, stack);
            case ResolvedStorageImageBinding image ->
                    configureStorageImageWrite(write, descriptorSets, image, stack);
        }
    }

    private void configureBufferWrite(VkWriteDescriptorSet write, long[] descriptorSets,
                                      ResolvedBufferBinding binding, MemoryStack stack) {
        long range = Math.max(1L, Math.min(binding.byteSize(), binding.buffer().sliceByteSize()));
        VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(binding.buffer().handle())
                .offset(0L)
                .range(range);
        write.sType$Default()
                .dstSet(descriptorSets[binding.set().setNumber()])
                .dstBinding(binding.slotIndex())
                .descriptorCount(1)
                .descriptorType(binding.set() == DescriptorSetIndex.UNIFORM_BUFFER
                        ? VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC
                        : VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC)
                .pBufferInfo(info);
    }

    private void configureSampledWrite(VkWriteDescriptorSet write, long[] descriptorSets,
                                       ResolvedSampledBinding binding, MemoryStack stack) {
        VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack)
                .sampler(binding.texture().sampler())
                .imageView(binding.texture().defaultView())
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        write.sType$Default()
                .dstSet(descriptorSets[DescriptorSetIndex.SAMPLED_TEXTURE.setNumber()])
                .dstBinding(binding.slotIndex())
                .descriptorCount(1)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .pImageInfo(info);
    }

    private void configureStorageImageWrite(VkWriteDescriptorSet write, long[] descriptorSets,
                                            ResolvedStorageImageBinding binding, MemoryStack stack) {
        VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack)
                .imageView(textureFactory.attachmentView(binding.texture(), binding.mipLevel(), 0))
                .imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
        write.sType$Default()
                .dstSet(descriptorSets[DescriptorSetIndex.STORAGE_IMAGE.setNumber()])
                .dstBinding(binding.slotIndex())
                .descriptorCount(1)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .pImageInfo(info);
    }

    private record DeclaredSlot(int slotIndex, DescriptorSetIndex set) {
    }
}
