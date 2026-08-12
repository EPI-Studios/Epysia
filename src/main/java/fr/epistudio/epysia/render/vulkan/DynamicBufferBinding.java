package fr.epistudio.epysia.render.vulkan;

public record DynamicBufferBinding(int slotIndex, VulkanBuffer buffer, long byteOffset, long byteSize) {

    public long dynamicOffset(int frameSlot) {
        return buffer.sliceOffset(frameSlot) + byteOffset;
    }
}
