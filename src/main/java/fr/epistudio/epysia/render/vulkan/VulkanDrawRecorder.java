package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.DrawStatistics;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.function.Function;

public final class VulkanDrawRecorder {

    private static final int INDIRECT_COMMAND_STRIDE = 20;

    private final VkCommandBuffer commandBuffer;
    private final DrawStatistics statistics;

    public VulkanDrawRecorder(VkCommandBuffer commandBuffer, DrawStatistics statistics) {
        this.commandBuffer = commandBuffer;
        this.statistics = statistics;
    }

    public void record(DrawCommand command, VulkanPipeline pipeline, VulkanMesh mesh,
                       Function<BufferHandle, VulkanBuffer> bufferLookup) {
        int indexCount = command.indexCountOverride() == DrawCommand.USE_MESH_INDEX_COUNT
                ? mesh.indexCount() : command.indexCountOverride();
        int firstIndex = command.firstIndexOverride() == DrawCommand.USE_MESH_FIRST_INDEX
                ? mesh.firstIndex() : command.firstIndexOverride();
        if (command.indirectBuffer() != null) {
            recordIndirect(command, pipeline, indexCount, bufferLookup);
            return;
        }
        recordDirect(command, pipeline, indexCount, firstIndex);
    }

    private void recordDirect(DrawCommand command, VulkanPipeline pipeline, int indexCount,
                              int firstIndex) {
        boolean instanced = command.instanceCount() > 1
                || (command.instanceBuffer() != null && pipeline.instanceStride() > 0);
        statistics.recordDraw(pipeline.state().topology(), indexCount,
                command.instanceCount(), instanced);
        VK10.vkCmdDrawIndexed(commandBuffer, indexCount, Math.max(1, command.instanceCount()),
                firstIndex, 0, 0);
    }

    private void recordIndirect(DrawCommand command, VulkanPipeline pipeline, int indexCount,
                                Function<BufferHandle, VulkanBuffer> bufferLookup) {
        VulkanBuffer indirect = bufferLookup.apply(command.indirectBuffer());
        int drawCount = command.isMultiDraw() ? command.indirectDrawCount() : 1;
        statistics.recordDraw(pipeline.state().topology(), indexCount, drawCount, true);
        VK10.vkCmdDrawIndexedIndirect(commandBuffer, indirect.handle(), 0L, drawCount,
                INDIRECT_COMMAND_STRIDE);
    }
}
