package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.logging.Logger;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRPipelineExecutableProperties;
import org.lwjgl.vulkan.VkPipelineExecutableInfoKHR;
import org.lwjgl.vulkan.VkPipelineExecutablePropertiesKHR;
import org.lwjgl.vulkan.VkPipelineExecutableStatisticKHR;
import org.lwjgl.vulkan.VkPipelineInfoKHR;

import java.nio.IntBuffer;

public final class VulkanPipelineStatistics {

    public static final String ENABLED_PROPERTY = "epysia.vulkan.pipelineStats";

    private final VulkanDevice device;
    private final Logger logger;

    public VulkanPipelineStatistics(VulkanDevice device, Logger logger) {
        this.device = device;
        this.logger = logger;
    }

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    public void report(long pipeline, String label) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineInfoKHR pipelineInfo = VkPipelineInfoKHR.calloc(stack)
                    .sType$Default()
                    .pipeline(pipeline);
            IntBuffer count = stack.mallocInt(1);
            KHRPipelineExecutableProperties.vkGetPipelineExecutablePropertiesKHR(
                    device.handle(), pipelineInfo, count, null);
            if (count.get(0) == 0) {
                return;
            }
            VkPipelineExecutablePropertiesKHR.Buffer executables =
                    VkPipelineExecutablePropertiesKHR.calloc(count.get(0), stack);
            executables.forEach(executable -> executable.sType$Default());
            KHRPipelineExecutableProperties.vkGetPipelineExecutablePropertiesKHR(
                    device.handle(), pipelineInfo, count, executables);
            reportExecutables(pipeline, label, executables);
        }
    }

    private void reportExecutables(long pipeline, String label,
                                   VkPipelineExecutablePropertiesKHR.Buffer executables) {
        for (int index = 0; index < executables.capacity(); index++) {
            logger.info("[pipeline-stats] " + label + " / " + executables.get(index).nameString()
                    + " " + statisticsOf(pipeline, index));
        }
    }

    private String statisticsOf(long pipeline, int executableIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineExecutableInfoKHR executableInfo = VkPipelineExecutableInfoKHR.calloc(stack)
                    .sType$Default()
                    .pipeline(pipeline)
                    .executableIndex(executableIndex);
            IntBuffer count = stack.mallocInt(1);
            KHRPipelineExecutableProperties.vkGetPipelineExecutableStatisticsKHR(
                    device.handle(), executableInfo, count, null);
            if (count.get(0) == 0) {
                return "no statistics";
            }
            VkPipelineExecutableStatisticKHR.Buffer statistics =
                    VkPipelineExecutableStatisticKHR.calloc(count.get(0), stack);
            statistics.forEach(statistic -> statistic.sType$Default());
            KHRPipelineExecutableProperties.vkGetPipelineExecutableStatisticsKHR(
                    device.handle(), executableInfo, count, statistics);
            return describe(statistics);
        }
    }

    private static String describe(VkPipelineExecutableStatisticKHR.Buffer statistics) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < statistics.capacity(); index++) {
            VkPipelineExecutableStatisticKHR statistic = statistics.get(index);
            text.append(statistic.nameString()).append('=')
                    .append(valueOf(statistic)).append("  ");
        }
        return text.toString();
    }

    private static String valueOf(VkPipelineExecutableStatisticKHR statistic) {
        return switch (statistic.format()) {
            case KHRPipelineExecutableProperties.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_BOOL32_KHR ->
                    String.valueOf(statistic.value().b32());
            case KHRPipelineExecutableProperties.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_INT64_KHR ->
                    String.valueOf(statistic.value().i64());
            case KHRPipelineExecutableProperties.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_UINT64_KHR ->
                    String.valueOf(statistic.value().u64());
            default -> String.valueOf(statistic.value().f64());
        };
    }
}
