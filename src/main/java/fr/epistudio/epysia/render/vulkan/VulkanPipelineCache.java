package fr.epistudio.epysia.render.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class VulkanPipelineCache implements AutoCloseable {

    private static final String DIRECTORY_PROPERTY = "epysia.vulkan.pipelineCache";

    private final VulkanDevice device;
    private final Optional<Path> cacheFile;
    private final long handle;

    public VulkanPipelineCache(VulkanDevice device) {
        this.device = device;
        this.cacheFile = resolveCacheFile(device);
        this.handle = create();
    }

    public long handle() {
        return handle;
    }

    private long create() {
        Optional<byte[]> seed = cacheFile.flatMap(VulkanPipelineCache::readQuietly);
        ByteBuffer initialData = seed.map(VulkanPipelineCache::toDirectBuffer).orElse(null);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineCacheCreateInfo createInfo = VkPipelineCacheCreateInfo.calloc(stack).sType$Default();
            if (initialData != null) {
                createInfo.pInitialData(initialData);
            }
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreatePipelineCache(device.handle(), createInfo, null, created),
                    "vkCreatePipelineCache");
            return created.get(0);
        } finally {
            if (initialData != null) {
                MemoryUtil.memFree(initialData);
            }
        }
    }

    private static ByteBuffer toDirectBuffer(byte[] bytes) {
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        buffer.put(bytes).flip();
        return buffer;
    }

    private void persist() {
        if (cacheFile.isEmpty()) {
            return;
        }
        int byteCount = readCacheSize();
        if (byteCount <= 0) {
            return;
        }
        ByteBuffer data = MemoryUtil.memAlloc(byteCount);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer size = stack.pointers(byteCount);
            VK10.vkGetPipelineCacheData(device.handle(), handle, size, data);
            writeQuietly(cacheFile.get(), data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private int readCacheSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer size = stack.mallocPointer(1);
            VK10.vkGetPipelineCacheData(device.handle(), handle, size, null);
            return (int) size.get(0);
        }
    }

    private static void writeQuietly(Path path, ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException unwritable) {
            return;
        }
    }

    private static Optional<byte[]> readQuietly(Path path) {
        try {
            return Files.isRegularFile(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<Path> resolveCacheFile(VulkanDevice device) {
        String directory = System.getProperty(DIRECTORY_PROPERTY,
                System.getProperty("user.home", ".") + "/.cache/epysia/pipelines");
        String fileName = device.deviceName().replaceAll("[^A-Za-z0-9]", "_") + ".cache";
        return Optional.of(Path.of(directory, fileName));
    }

    @Override
    public void close() {
        persist();
        VK10.vkDestroyPipelineCache(device.handle(), handle, null);
    }
}
