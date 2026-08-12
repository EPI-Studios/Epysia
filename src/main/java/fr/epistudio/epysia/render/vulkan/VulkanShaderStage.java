package fr.epistudio.epysia.render.vulkan;

import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK10;

public enum VulkanShaderStage {
    VERTEX(Shaderc.shaderc_vertex_shader, VK10.VK_SHADER_STAGE_VERTEX_BIT, "vertex"),
    FRAGMENT(Shaderc.shaderc_fragment_shader, VK10.VK_SHADER_STAGE_FRAGMENT_BIT, "fragment"),
    COMPUTE(Shaderc.shaderc_compute_shader, VK10.VK_SHADER_STAGE_COMPUTE_BIT, "compute");

    private final int shadercKind;
    private final int vulkanStageBit;
    private final String displayName;

    VulkanShaderStage(int shadercKind, int vulkanStageBit, String displayName) {
        this.shadercKind = shadercKind;
        this.vulkanStageBit = vulkanStageBit;
        this.displayName = displayName;
    }

    public int shadercKind() {
        return shadercKind;
    }

    public int vulkanStageBit() {
        return vulkanStageBit;
    }

    public String displayName() {
        return displayName;
    }
}
