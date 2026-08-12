package fr.epistudio.epysia.render.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VulkanProfiler implements AutoCloseable {

    private static final String ENABLED_PROPERTY = "epysia.gpu.profiling";
    private static final int MAX_SECTIONS = 64;
    private static final int SUPPRESSED_SECTION = -1;
    private static final int TIMESTAMPS_PER_SECTION = 2;
    private static final int QUERIES_PER_FRAME = MAX_SECTIONS * TIMESTAMPS_PER_SECTION;

    private final VulkanDevice device;
    private final boolean enabled;
    private final long[] queryPools = new long[VulkanFrameRing.FRAMES_IN_FLIGHT];
    private final String[][] sectionNames =
            new String[VulkanFrameRing.FRAMES_IN_FLIGHT][MAX_SECTIONS];
    private final int[] sectionCounts = new int[VulkanFrameRing.FRAMES_IN_FLIGHT];
    private final Map<String, Long> latestTimings = new LinkedHashMap<>();

    private final Deque<Integer> openSections = new ArrayDeque<>();
    private int currentSection;
    private boolean sectionActive;

    public VulkanProfiler(VulkanDevice device) {
        this.device = device;
        this.enabled = Boolean.getBoolean(ENABLED_PROPERTY);
        if (enabled) {
            createQueryPools();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public Map<String, Long> latestTimings() {
        return latestTimings;
    }

    public void beginFrame(VkCommandBuffer commandBuffer, int frameSlot) {
        if (!enabled) {
            return;
        }
        drainPreviousFrame(frameSlot);
        currentSection = 0;
        openSections.clear();
        VK10.vkCmdResetQueryPool(commandBuffer, queryPools[frameSlot], 0, QUERIES_PER_FRAME);
    }

    public void beginSection(VkCommandBuffer commandBuffer, int frameSlot, String name) {
        if (!enabled || currentSection >= MAX_SECTIONS) {
            openSections.push(SUPPRESSED_SECTION);
            return;
        }
        sectionNames[frameSlot][currentSection] = name;
        VK13.vkCmdWriteTimestamp2(commandBuffer, VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
                queryPools[frameSlot], currentSection * TIMESTAMPS_PER_SECTION);
        openSections.push(currentSection);
        currentSection++;
    }

    public void endSection(VkCommandBuffer commandBuffer, int frameSlot) {
        if (openSections.isEmpty()) {
            return;
        }
        int section = openSections.pop();
        if (section == SUPPRESSED_SECTION) {
            return;
        }
        VK13.vkCmdWriteTimestamp2(commandBuffer, VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
                queryPools[frameSlot], section * TIMESTAMPS_PER_SECTION + 1);
    }

    public void endFrame(int frameSlot) {
        if (enabled) {
            sectionCounts[frameSlot] = currentSection;
        }
    }

    private void drainPreviousFrame(int frameSlot) {
        int sections = sectionCounts[frameSlot];
        if (sections == 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer results = stack.mallocLong(sections * TIMESTAMPS_PER_SECTION);
            int status = VK10.vkGetQueryPoolResults(device.handle(), queryPools[frameSlot], 0,
                    sections * TIMESTAMPS_PER_SECTION, results, Long.BYTES, VK10.VK_QUERY_RESULT_64_BIT);
            if (status != VK10.VK_SUCCESS) {
                return;
            }
            recordTimings(frameSlot, sections, results);
        }
    }

    private void recordTimings(int frameSlot, int sections, LongBuffer results) {
        latestTimings.clear();
        for (int section = 0; section < sections; section++) {
            long start = results.get(section * TIMESTAMPS_PER_SECTION);
            long end = results.get(section * TIMESTAMPS_PER_SECTION + 1);
            long nanos = (long) ((end - start) * device.limits().timestampPeriodNanos());
            latestTimings.merge(sectionNames[frameSlot][section], nanos, Long::sum);
        }
    }

    private void createQueryPools() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int slot = 0; slot < VulkanFrameRing.FRAMES_IN_FLIGHT; slot++) {
                VkQueryPoolCreateInfo createInfo = VkQueryPoolCreateInfo.calloc(stack)
                        .sType$Default()
                        .queryType(VK10.VK_QUERY_TYPE_TIMESTAMP)
                        .queryCount(QUERIES_PER_FRAME);
                LongBuffer created = stack.mallocLong(1);
                VulkanResult.check(VK10.vkCreateQueryPool(device.handle(), createInfo, null, created),
                        "vkCreateQueryPool");
                queryPools[slot] = created.get(0);
            }
        }
    }

    @Override
    public void close() {
        if (!enabled) {
            return;
        }
        for (long pool : queryPools) {
            VK10.vkDestroyQueryPool(device.handle(), pool, null);
        }
    }
}
