package fr.epistudio.epysia.render.vulkan;

public record VulkanBuffer(long handle, long allocation, long sliceByteSize, int sliceCount,
                           long mappedAddress) {

    public long totalByteSize() {
        return sliceByteSize * sliceCount;
    }

    public long sliceOffset(int frameSlot) {
        return sliceCount <= 1 ? 0L : sliceByteSize * (frameSlot % sliceCount);
    }

    public boolean isMapped() {
        return mappedAddress != 0L;
    }
}
