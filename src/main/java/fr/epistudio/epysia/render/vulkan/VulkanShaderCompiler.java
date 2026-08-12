package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.Optional;

public final class VulkanShaderCompiler implements AutoCloseable {

    private static final String ENTRY_POINT = "main";

    private final SpirvDiskCache cache;
    private final long compiler;
    private final long options;

    public VulkanShaderCompiler(SpirvDiskCache cache) {
        this.cache = cache;
        this.compiler = Shaderc.shaderc_compiler_initialize();
        this.options = Shaderc.shaderc_compile_options_initialize();
        configureOptions();
    }

    private static int optimizationLevel() {
        return switch (System.getProperty("epysia.vulkan.spirvOptimization", "size")) {
            case "none" -> Shaderc.shaderc_optimization_level_zero;
            case "size" -> Shaderc.shaderc_optimization_level_size;
            default -> Shaderc.shaderc_optimization_level_performance;
        };
    }

    private static int targetSpirvVersion() {
        return switch (System.getProperty("epysia.vulkan.spirvVersion", "1.6")) {
            case "1.0" -> Shaderc.shaderc_spirv_version_1_0;
            case "1.3" -> Shaderc.shaderc_spirv_version_1_3;
            case "1.5" -> Shaderc.shaderc_spirv_version_1_5;
            default -> Shaderc.shaderc_spirv_version_1_6;
        };
    }

    private void configureOptions() {
        Shaderc.shaderc_compile_options_set_target_env(options,
                Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_3);
        Shaderc.shaderc_compile_options_set_target_spirv(options, targetSpirvVersion());
        Shaderc.shaderc_compile_options_set_optimization_level(options, optimizationLevel());
    }

    public byte[] compile(VulkanShaderStage stage, String vulkanSource, String debugName) {
        String key = cache.keyFor(stage, vulkanSource + optimizationLevel() + targetSpirvVersion());
        Optional<byte[]> cached = cache.read(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        byte[] spirv = invokeShaderc(stage, vulkanSource, debugName);
        cache.write(key, spirv);
        return spirv;
    }

    private byte[] invokeShaderc(VulkanShaderStage stage, String source, String debugName) {
        long result = Shaderc.shaderc_compile_into_spv(compiler, source, stage.shadercKind(),
                debugName, ENTRY_POINT, options);
        try {
            failOnCompileError(result, stage, debugName, source);
            return copyResultBytes(result);
        } finally {
            Shaderc.shaderc_result_release(result);
        }
    }

    private void failOnCompileError(long result, VulkanShaderStage stage, String debugName, String source) {
        if (Shaderc.shaderc_result_get_compilation_status(result) == Shaderc.shaderc_compilation_status_success) {
            return;
        }
        String message = Shaderc.shaderc_result_get_error_message(result);
        throw new EpysiaException("Vulkan " + stage.displayName() + " shader compile failed for "
                + debugName + ": " + message + "\n" + numberLines(source));
    }

    private static byte[] copyResultBytes(long result) {
        ByteBuffer produced = Shaderc.shaderc_result_get_bytes(result);
        if (produced == null) {
            throw new EpysiaException("shaderc produced no SPIR-V bytes.");
        }
        byte[] copy = new byte[produced.remaining()];
        produced.duplicate().get(copy);
        return copy;
    }

    private static String numberLines(String source) {
        StringBuilder numbered = new StringBuilder(source.length() + 512);
        String[] lines = source.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            numbered.append(index + 1).append(": ").append(lines[index]).append('\n');
        }
        return numbered.toString();
    }

    @Override
    public void close() {
        Shaderc.shaderc_compile_options_release(options);
        Shaderc.shaderc_compiler_release(compiler);
    }
}
