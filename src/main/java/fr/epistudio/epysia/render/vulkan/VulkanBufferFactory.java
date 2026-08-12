package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.BufferUsage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.LongBuffer;

public final class VulkanBufferFactory {

    private static final int TRANSFER_USAGES =
            VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;

    private final VulkanDevice device;

    public VulkanBufferFactory(VulkanDevice device) {
        this.device = device;
    }

    public VulkanBuffer createMapped(BufferUsage usage, long sliceByteSize, int sliceCount) {
        return create(usageBitsFor(usage), sliceByteSize, sliceCount,
                mappedMemoryUsage(),
                Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT
                        | Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
    }

    private static int mappedMemoryUsage() {
        return switch (System.getProperty("epysia.vulkan.mappedMemory", "device")) {
            case "host" -> Vma.VMA_MEMORY_USAGE_AUTO_PREFER_HOST;
            case "device" -> Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE;
            default -> Vma.VMA_MEMORY_USAGE_AUTO;
        };
    }

    public VulkanBuffer createDeviceLocal(BufferUsage usage, long byteSize) {
        return create(usageBitsFor(usage), byteSize, 1, Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, 0);
    }

    private static int usageBitsFor(BufferUsage usage) {
        return TRANSFER_USAGES
                | VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
                | VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT
                | VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
                | VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                | VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
    }

    private static int diagnosticStagingBuffers;

    public static int diagnosticStagingBuffers() {
        return diagnosticStagingBuffers;
    }

    public VulkanBuffer createReadback(long byteSize) {
        diagnosticStagingBuffers++;
        return create(TRANSFER_USAGES, byteSize, 1,
                Vma.VMA_MEMORY_USAGE_AUTO_PREFER_HOST,
                Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT
                        | Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT);
    }

    private VulkanBuffer create(int usageBits, long sliceByteSize, int sliceCount,
                                int memoryUsage, int allocationFlags) {
        long total = Math.max(1L, sliceByteSize * sliceCount);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(total)
                    .usage(usageBits)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(memoryUsage)
                    .flags(allocationFlags);
            LongBuffer createdBuffer = stack.mallocLong(1);
            PointerBuffer createdAllocation = stack.mallocPointer(1);
            VmaAllocationInfo details = VmaAllocationInfo.calloc(stack);
            VulkanResult.check(Vma.vmaCreateBuffer(device.allocator(), bufferInfo, allocationInfo,
                    createdBuffer, createdAllocation, details), "vmaCreateBuffer");
            return new VulkanBuffer(createdBuffer.get(0), createdAllocation.get(0),
                    Math.max(1L, sliceByteSize), Math.max(1, sliceCount), details.pMappedData());
        }
    }

    public String describePlacement(VulkanBuffer buffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer flags = stack.mallocInt(1);
            Vma.vmaGetAllocationMemoryProperties(device.allocator(), buffer.allocation(), flags);
            int properties = flags.get(0);
            return ((properties & VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0 ? "DEVICE_LOCAL " : "")
                    + ((properties & VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0 ? "HOST_VISIBLE " : "")
                    + ((properties & VK10.VK_MEMORY_PROPERTY_HOST_CACHED_BIT) != 0 ? "HOST_CACHED " : "");
        }
    }

    public void destroy(VulkanBuffer buffer) {
        Vma.vmaDestroyBuffer(device.allocator(), buffer.handle(), buffer.allocation());
    }
}
