package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

public final class VulkanDescriptorAllocator implements AutoCloseable {

    private static final int SETS_PER_POOL = 1024;
    private static final int DESCRIPTORS_PER_TYPE = 2048;

    private final VulkanDevice device;
    private final List<Long> pools = new ArrayList<>();

    private long activePool = VK10.VK_NULL_HANDLE;

    public VulkanDescriptorAllocator(VulkanDevice device) {
        this.device = device;
    }

    private static int diagnosticAllocations;

    public static int diagnosticAllocations() {
        return diagnosticAllocations;
    }

    public AllocatedDescriptorSet allocate(long setLayout) {
        diagnosticAllocations++;
        if (activePool == VK10.VK_NULL_HANDLE) {
            activePool = createPool();
        }
        long allocated = tryAllocate(activePool, setLayout);
        if (allocated != VK10.VK_NULL_HANDLE) {
            return new AllocatedDescriptorSet(allocated, activePool);
        }
        activePool = createPool();
        long retried = tryAllocate(activePool, setLayout);
        if (retried == VK10.VK_NULL_HANDLE) {
            throw new EpysiaException("Could not allocate a descriptor set from a fresh pool.");
        }
        return new AllocatedDescriptorSet(retried, activePool);
    }

    public void free(long pool, long descriptorSet) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VK10.vkFreeDescriptorSets(device.handle(), pool, stack.longs(descriptorSet));
        }
    }

    public long activePool() {
        return activePool;
    }

    private long tryAllocate(long pool, long setLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(pool)
                    .pSetLayouts(stack.longs(setLayout));
            LongBuffer allocated = stack.mallocLong(1);
            int result = VK10.vkAllocateDescriptorSets(device.handle(), allocateInfo, allocated);
            return result == VK10.VK_SUCCESS ? allocated.get(0) : VK10.VK_NULL_HANDLE;
        }
    }

    private long createPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(4, stack);
            sizes.get(0).type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                    .descriptorCount(DESCRIPTORS_PER_TYPE);
            sizes.get(1).type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC)
                    .descriptorCount(DESCRIPTORS_PER_TYPE);
            sizes.get(2).type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(DESCRIPTORS_PER_TYPE);
            sizes.get(3).type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(DESCRIPTORS_PER_TYPE);
            VkDescriptorPoolCreateInfo createInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .maxSets(SETS_PER_POOL)
                    .pPoolSizes(sizes);
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateDescriptorPool(device.handle(), createInfo, null, created),
                    "vkCreateDescriptorPool");
            pools.add(created.get(0));
            return created.get(0);
        }
    }

    @Override
    public void close() {
        pools.forEach(pool -> VK10.vkDestroyDescriptorPool(device.handle(), pool, null));
        pools.clear();
        activePool = VK10.VK_NULL_HANDLE;
    }
}
