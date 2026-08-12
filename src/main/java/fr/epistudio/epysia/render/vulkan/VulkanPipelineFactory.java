package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.BlendMode;
import fr.epistudio.epysia.render.backend.CullMode;
import fr.epistudio.epysia.render.backend.DepthTest;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.StencilOperation;
import fr.epistudio.epysia.render.backend.StencilState;
import fr.epistudio.epysia.render.backend.StencilTest;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexLayout;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRPipelineExecutableProperties;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkStencilOpState;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.LongBuffer;
import java.util.Optional;

public final class VulkanPipelineFactory {

    private static final int VERTEX_BINDING = 0;
    private static final int INSTANCE_BINDING = 1;

    private final VulkanDevice device;
    private final long pipelineCache;
    private final Optional<VulkanPipelineStatistics> statistics;

    public VulkanPipelineFactory(VulkanDevice device, long pipelineCache,
                                 Optional<VulkanPipelineStatistics> statistics) {
        this.device = device;
        this.pipelineCache = pipelineCache;
        this.statistics = statistics;
    }

    public long createGraphicsVariant(VulkanPipeline pipeline, RenderingFormats formats) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkGraphicsPipelineCreateInfo.Buffer createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .pNext(renderingInfo(formats, stack).address())
                    .pStages(shaderStages(pipeline, stack))
                    .pVertexInputState(vertexInput(pipeline, stack))
                    .pInputAssemblyState(inputAssembly(pipeline.state().topology(), stack))
                    .pViewportState(viewportState(stack))
                    .pRasterizationState(rasterization(pipeline.state(), stack))
                    .pMultisampleState(multisample(stack))
                    .pDepthStencilState(depthStencil(pipeline.state(), formats, stack))
                    .pColorBlendState(colorBlend(pipeline.state(), formats, stack))
                    .pDynamicState(dynamicState(stack))
                    .flags(statistics.isPresent()
                            ? KHRPipelineExecutableProperties.VK_PIPELINE_CREATE_CAPTURE_STATISTICS_BIT_KHR : 0)
                    .layout(pipeline.layout().pipelineLayout());
            LongBuffer created = stack.mallocLong(1);
            VulkanResult.check(VK10.vkCreateGraphicsPipelines(device.handle(), pipelineCache,
                    createInfo, null, created), "vkCreateGraphicsPipelines");
            statistics.ifPresent(reporter -> reporter.report(created.get(0), labelOf(formats)));
            return created.get(0);
        }
    }

    private static String labelOf(RenderingFormats formats) {
        return "colors" + formats.colorFormats() + " depth" + formats.depthFormat();
    }

    private static VkPipelineRenderingCreateInfo renderingInfo(RenderingFormats formats, MemoryStack stack) {
        VkPipelineRenderingCreateInfo info = VkPipelineRenderingCreateInfo.calloc(stack)
                .sType$Default()
                .depthAttachmentFormat(formats.depthFormat())
                .stencilAttachmentFormat(formats.stencilFormat());
        if (formats.colorFormats().isEmpty()) {
            return info;
        }
        int[] colors = formats.colorFormats().stream().mapToInt(Integer::intValue).toArray();
        return info.pColorAttachmentFormats(stack.ints(colors));
    }

    private VkPipelineShaderStageCreateInfo.Buffer shaderStages(VulkanPipeline pipeline, MemoryStack stack) {
        VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
        stages.get(0).sType$Default()
                .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                .module(pipeline.vertexModule())
                .pName(stack.UTF8("main"));
        stages.get(1).sType$Default()
                .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(pipeline.fragmentModule())
                .pName(stack.UTF8("main"));
        return stages;
    }

    private VkPipelineVertexInputStateCreateInfo vertexInput(VulkanPipeline pipeline, MemoryStack stack) {
        int bindingCount = pipeline.instanceLayout().isPresent() ? 2 : 1;
        VkVertexInputBindingDescription.Buffer bindings =
                VkVertexInputBindingDescription.calloc(bindingCount, stack);
        bindings.get(0).binding(VERTEX_BINDING).stride(pipeline.vertexStride())
                .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        pipeline.instanceLayout().ifPresent(layout -> bindings.get(1).binding(INSTANCE_BINDING)
                .stride(layout.byteStride()).inputRate(VK10.VK_VERTEX_INPUT_RATE_INSTANCE));
        return VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType$Default()
                .pVertexBindingDescriptions(bindings)
                .pVertexAttributeDescriptions(vertexAttributes(pipeline, stack));
    }

    private VkVertexInputAttributeDescription.Buffer vertexAttributes(VulkanPipeline pipeline,
                                                                      MemoryStack stack) {
        int total = attributeCount(pipeline.vertexLayout()) + attributeCount(pipeline.instanceLayout());
        VkVertexInputAttributeDescription.Buffer attributes =
                VkVertexInputAttributeDescription.calloc(total, stack);
        int index = fillAttributes(attributes, 0, pipeline.vertexLayout(), VERTEX_BINDING);
        fillAttributes(attributes, index, pipeline.instanceLayout(), INSTANCE_BINDING);
        return attributes;
    }

    private static int attributeCount(Optional<VertexLayout> layout) {
        return layout.map(present -> present.attributes().size()).orElse(0);
    }

    private static int fillAttributes(VkVertexInputAttributeDescription.Buffer target, int startIndex,
                                      Optional<VertexLayout> layout, int binding) {
        if (layout.isEmpty()) {
            return startIndex;
        }
        int index = startIndex;
        for (VertexAttribute attribute : layout.get().attributes()) {
            target.get(index)
                    .location(attribute.location())
                    .binding(binding)
                    .format(VulkanFormats.of(attribute.format()))
                    .offset(attribute.byteOffset());
            index++;
        }
        return index;
    }

    private static VkPipelineInputAssemblyStateCreateInfo inputAssembly(Topology topology,
                                                                        MemoryStack stack) {
        return VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType$Default()
                .topology(switch (topology) {
                    case TRIANGLES -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
                    case TRIANGLE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
                    case LINES -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
                });
    }

    private static VkPipelineViewportStateCreateInfo viewportState(MemoryStack stack) {
        return VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType$Default()
                .viewportCount(1)
                .scissorCount(1);
    }

    private static VkPipelineRasterizationStateCreateInfo rasterization(RenderState state,
                                                                        MemoryStack stack) {
        return VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType$Default()
                .depthClampEnable(state.depthClamp())
                .polygonMode(VK10.VK_POLYGON_MODE_FILL)
                .cullMode(cullModeOf(state.cullMode()))
                .frontFace(VK10.VK_FRONT_FACE_CLOCKWISE)
                .lineWidth(1.0f);
    }

    private static int cullModeOf(CullMode cullMode) {
        return switch (cullMode) {
            case NONE -> VK10.VK_CULL_MODE_NONE;
            case BACK -> VK10.VK_CULL_MODE_BACK_BIT;
            case FRONT -> VK10.VK_CULL_MODE_FRONT_BIT;
        };
    }

    private static VkPipelineMultisampleStateCreateInfo multisample(MemoryStack stack) {
        return VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType$Default()
                .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .minSampleShading(1.0f);
    }

    private static VkPipelineDepthStencilStateCreateInfo depthStencil(RenderState state,
                                                                      RenderingFormats formats,
                                                                      MemoryStack stack) {
        VkPipelineDepthStencilStateCreateInfo info = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType$Default()
                .depthTestEnable(formats.hasDepth() && state.depthTest() != DepthTest.DISABLED)
                .depthWriteEnable(formats.hasDepth() && state.depthWrite())
                .depthCompareOp(compareOpOf(state.depthTest()))
                .minDepthBounds(0.0f)
                .maxDepthBounds(1.0f);
        applyStencil(info, state.stencil(), formats);
        return info;
    }

    private static void applyStencil(VkPipelineDepthStencilStateCreateInfo info, StencilState stencil,
                                     RenderingFormats formats) {
        boolean enabled = stencil.enabled() && formats.hasStencil();
        info.stencilTestEnable(enabled);
        if (!enabled) {
            return;
        }
        configureStencilFace(info.front(), stencil);
        configureStencilFace(info.back(), stencil);
    }

    private static void configureStencilFace(VkStencilOpState face, StencilState stencil) {
        face.failOp(stencilOperationOf(stencil.onStencilFail()))
                .passOp(stencilOperationOf(stencil.onPass()))
                .depthFailOp(stencilOperationOf(stencil.onDepthFail()))
                .compareOp(stencilTestOf(stencil.test()))
                .compareMask(stencil.compareMask())
                .writeMask(stencil.writeMask())
                .reference(stencil.reference());
    }

    private static int compareOpOf(DepthTest depthTest) {
        return switch (depthTest) {
            case DISABLED -> VK10.VK_COMPARE_OP_ALWAYS;
            case LESS -> VK10.VK_COMPARE_OP_LESS;
            case LESS_EQUAL -> VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
        };
    }

    private static int stencilTestOf(StencilTest test) {
        return switch (test) {
            case NEVER -> VK10.VK_COMPARE_OP_NEVER;
            case LESS -> VK10.VK_COMPARE_OP_LESS;
            case LESS_EQUAL -> VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
            case GREATER -> VK10.VK_COMPARE_OP_GREATER;
            case GREATER_EQUAL -> VK10.VK_COMPARE_OP_GREATER_OR_EQUAL;
            case EQUAL -> VK10.VK_COMPARE_OP_EQUAL;
            case NOT_EQUAL -> VK10.VK_COMPARE_OP_NOT_EQUAL;
            case ALWAYS -> VK10.VK_COMPARE_OP_ALWAYS;
        };
    }

    private static int stencilOperationOf(StencilOperation operation) {
        return switch (operation) {
            case KEEP -> VK10.VK_STENCIL_OP_KEEP;
            case ZERO -> VK10.VK_STENCIL_OP_ZERO;
            case REPLACE -> VK10.VK_STENCIL_OP_REPLACE;
            case INCREMENT_CLAMP -> VK10.VK_STENCIL_OP_INCREMENT_AND_CLAMP;
            case DECREMENT_CLAMP -> VK10.VK_STENCIL_OP_DECREMENT_AND_CLAMP;
            case INVERT -> VK10.VK_STENCIL_OP_INVERT;
            case INCREMENT_WRAP -> VK10.VK_STENCIL_OP_INCREMENT_AND_WRAP;
            case DECREMENT_WRAP -> VK10.VK_STENCIL_OP_DECREMENT_AND_WRAP;
        };
    }

    private static VkPipelineColorBlendStateCreateInfo colorBlend(RenderState state,
                                                                  RenderingFormats formats,
                                                                  MemoryStack stack) {
        int attachmentCount = formats.colorFormats().size();
        VkPipelineColorBlendStateCreateInfo info = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType$Default();
        if (attachmentCount == 0) {
            return info;
        }
        VkPipelineColorBlendAttachmentState.Buffer attachments =
                VkPipelineColorBlendAttachmentState.calloc(attachmentCount, stack);
        for (int index = 0; index < attachmentCount; index++) {
            configureBlendAttachment(attachments.get(index), state);
        }
        return info.pAttachments(attachments);
    }

    private static void configureBlendAttachment(VkPipelineColorBlendAttachmentState attachment,
                                                 RenderState state) {
        attachment.colorWriteMask(state.colorWrite() ? colorWriteMask() : 0);
        if (state.blendMode() == BlendMode.OPAQUE) {
            attachment.blendEnable(false);
            return;
        }
        attachment.blendEnable(true)
                .colorBlendOp(VK10.VK_BLEND_OP_ADD)
                .alphaBlendOp(VK10.VK_BLEND_OP_ADD)
                .srcColorBlendFactor(sourceFactorOf(state.blendMode()))
                .dstColorBlendFactor(destinationFactorOf(state.blendMode()))
                .srcAlphaBlendFactor(sourceFactorOf(state.blendMode()))
                .dstAlphaBlendFactor(destinationFactorOf(state.blendMode()));
    }

    private static int colorWriteMask() {
        return VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT
                | VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT;
    }

    private static int sourceFactorOf(BlendMode blendMode) {
        return blendMode == BlendMode.ADDITIVE ? VK10.VK_BLEND_FACTOR_ONE
                : VK10.VK_BLEND_FACTOR_SRC_ALPHA;
    }

    private static int destinationFactorOf(BlendMode blendMode) {
        return blendMode == BlendMode.ADDITIVE ? VK10.VK_BLEND_FACTOR_ONE
                : VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
    }

    private static VkPipelineDynamicStateCreateInfo dynamicState(MemoryStack stack) {
        return VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType$Default()
                .pDynamicStates(stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR));
    }
}
