package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.shader.ShaderLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class ShaderTranslationReport {

    private static final String SHADER_EXTENSION = ".glsl";
    private static final String VERSION_MARKER = "#version";

    private static final String UNCOMPOSED_MARKER = "no matching overloaded function found";

    private final ShaderLoader loader;
    private final VulkanGlslRewriter rewriter;
    private final VulkanShaderCompiler compiler;
    private final Logger logger;

    public ShaderTranslationReport(ShaderLoader loader, VulkanGlslRewriter rewriter,
                                   VulkanShaderCompiler compiler, Logger logger) {
        this.loader = loader;
        this.rewriter = rewriter;
        this.compiler = compiler;
        this.logger = logger;
    }

    public static void main(String[] arguments) {
        Logger logger = new ConsoleLogger();
        ShaderLoader loader = ShaderLoader.autoDetect();
        try (VulkanShaderCompiler compiler = new VulkanShaderCompiler(new SpirvDiskCache())) {
            new ShaderTranslationReport(loader, new VulkanGlslRewriter(), compiler, logger).run();
        }
    }

    public void run() {
        Path root = loader.filesystemRoot()
                .orElseThrow(() -> new EpysiaException("Shader source root is not available on disk."));
        List<String> failures = new ArrayList<>();
        List<String> awaitingComposition = new ArrayList<>();
        int translated = 0;
        for (Path shader : entryPoints(root)) {
            translated += translateOne(root, shader, failures, awaitingComposition) ? 1 : 0;
        }
        logger.info("Translated " + translated + ", awaiting composition " + awaitingComposition.size()
                + ", failed " + failures.size());
        awaitingComposition.forEach(logger::warn);
        failures.forEach(logger::error);
        if (!failures.isEmpty()) {
            throw new EpysiaException(failures.size() + " shaders failed Vulkan translation.");
        }
    }

    private boolean translateOne(Path root, Path shader, List<String> failures,
                                 List<String> awaitingComposition) {
        String relative = root.relativize(shader).toString();
        Optional<VulkanShaderStage> stage = stageOf(relative);
        if (stage.isEmpty()) {
            return false;
        }
        try {
            compiler.compile(stage.get(), vulkanSourceOf(stage.get(), relative), relative);
            return true;
        } catch (RuntimeException failed) {
            record(relative, failed, failures, awaitingComposition);
            return false;
        }
    }

    private static void record(String relative, RuntimeException failed, List<String> failures,
                               List<String> awaitingComposition) {
        String message = String.valueOf(failed.getMessage());
        if (message.contains(UNCOMPOSED_MARKER)) {
            awaitingComposition.add(relative + " needs a composed hook function.");
            return;
        }
        failures.add(relative + " -> " + message);
    }

    private String vulkanSourceOf(VulkanShaderStage stage, String relative) {
        String source = loader.load(relative).source();
        return switch (stage) {
            case VERTEX -> rewriter.rewriteVertex(source, "");
            case FRAGMENT -> rewriter.rewriteFragment("", source);
            case COMPUTE -> rewriter.rewriteCompute(source);
        };
    }

    private List<Path> entryPoints(Path root) {
        try (Stream<Path> walked = Files.walk(root)) {
            return walked.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(SHADER_EXTENSION))
                    .filter(ShaderTranslationReport::declaresVersion)
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new EpysiaException("Could not walk shader root " + root, unreadable);
        }
    }

    private static boolean declaresVersion(Path path) {
        try {
            return Files.readString(path).contains(VERSION_MARKER);
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static Optional<VulkanShaderStage> stageOf(String relativePath) {
        if (relativePath.contains(".vert.")) {
            return Optional.of(VulkanShaderStage.VERTEX);
        }
        if (relativePath.contains(".frag.")) {
            return Optional.of(VulkanShaderStage.FRAGMENT);
        }
        if (relativePath.contains(".comp.")) {
            return Optional.of(VulkanShaderStage.COMPUTE);
        }
        return Optional.empty();
    }
}
