package fr.epistudio.epysia.render.vulkan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VulkanGlslRewriter {

    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("#version\\s+\\d+(\\s+core)?");
    private static final Pattern BOUND_DECLARATION =
            Pattern.compile("layout\\s*\\(([^)]*\\bbinding\\s*=\\s*\\d+[^)]*)\\)([^;{]*)");
    private static final Pattern SAMPLER_TYPE =
            Pattern.compile("\\b(sampler|isampler|usampler)\\w*\\s+\\w+");
    private static final Pattern IMAGE_TYPE =
            Pattern.compile("\\b(image|iimage|uimage)\\w*\\s+\\w+");
    private static final Pattern VERTEX_ENTRY = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)");
    private static final Pattern INSTANCE_IDENTIFIER = Pattern.compile("\\bgl_InstanceID\\b");
    private static final Pattern VERTEX_IDENTIFIER = Pattern.compile("\\bgl_VertexID\\b");
    private static final Pattern DISCARD_STATEMENT = Pattern.compile("\\bdiscard\\b");

    private static final String RENAMED_ENTRY = "void epysiaSourceMain()";
    private static final String VERSION_LINE = "#version 450 core";
    private static final String DRAW_PARAMETERS =
            Boolean.getBoolean("epysia.vulkan.drawParameters")
                    ? "\n#extension GL_ARB_shader_draw_parameters : require" : "";

    private static final String DEPTH_RANGE_ADAPTER = """

            void main() {
                epysiaSourceMain();
                gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
            }
            """;

    public RewrittenGraphicsShaders rewriteGraphics(String vertexSource, String fragmentSource) {
        return new RewrittenGraphicsShaders(
                rewriteVertex(vertexSource, fragmentSource),
                rewriteFragment(vertexSource, fragmentSource));
    }

    public String rewriteVertex(String vertexSource, String fragmentSource) {
        String vertex = prepare(VulkanShaderStage.VERTEX, vertexSource);
        VaryingLocations locations = locationsFor(vertex, fragmentSource);
        return adaptVertexStage(locations.applyToVertex(vertex));
    }

    public String rewriteFragment(String vertexSource, String fragmentSource) {
        String fragment = demoteInsteadOfDiscard(prepare(VulkanShaderStage.FRAGMENT, fragmentSource));
        VaryingLocations locations = locationsFor(vertexSource, fragment);
        return locations.applyToFragment(fragment);
    }

    private String demoteInsteadOfDiscard(String source) {
        if (Boolean.getBoolean("epysia.vulkan.keepDiscard")
                || !DISCARD_STATEMENT.matcher(source).find()) {
            return source;
        }
        String demoted = DISCARD_STATEMENT.matcher(source).replaceAll("demote");
        return demoted.replaceFirst(Pattern.quote(VERSION_LINE), Matcher.quoteReplacement(
                VERSION_LINE + "\n#extension GL_EXT_demote_to_helper_invocation : require"));
    }

    private VaryingLocations locationsFor(String vertexSource, String fragmentSource) {
        return VaryingLocations.forPair(vertexSource, fragmentSource);
    }

    public String rewriteCompute(String source) {
        return prepare(VulkanShaderStage.COMPUTE, source);
    }

    private String prepare(VulkanShaderStage stage, String source) {
        return assignDescriptorSets(upgradeVersion(stage, source));
    }

    private String upgradeVersion(VulkanShaderStage stage, String source) {
        String header = VERSION_LINE
                + (stage == VulkanShaderStage.VERTEX ? DRAW_PARAMETERS : "");
        Matcher matcher = VERSION_DIRECTIVE.matcher(source);
        if (!matcher.find()) {
            return header + "\n" + source;
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(header));
    }

    private String assignDescriptorSets(String source) {
        Matcher matcher = BOUND_DECLARATION.matcher(source);
        StringBuilder rewritten = new StringBuilder(source.length() + 64);
        while (matcher.find()) {
            DescriptorSetIndex set = classify(matcher.group(2));
            String replacement = "layout(set = " + set.setNumber() + ", " + matcher.group(1) + ")"
                    + matcher.group(2);
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private DescriptorSetIndex classify(String declaration) {
        if (declaration.contains("buffer")) {
            return DescriptorSetIndex.STORAGE_BUFFER;
        }
        if (SAMPLER_TYPE.matcher(declaration).find()) {
            return DescriptorSetIndex.SAMPLED_TEXTURE;
        }
        if (IMAGE_TYPE.matcher(declaration).find()) {
            return DescriptorSetIndex.STORAGE_IMAGE;
        }
        return DescriptorSetIndex.UNIFORM_BUFFER;
    }

    private String adaptVertexStage(String source) {
        String withVertexIndex = VERTEX_IDENTIFIER.matcher(source).replaceAll("gl_VertexIndex");
        String withInstanceIndex = INSTANCE_IDENTIFIER.matcher(withVertexIndex)
                .replaceAll(Boolean.getBoolean("epysia.vulkan.drawParameters")
                        ? "(gl_InstanceIndex - gl_BaseInstanceARB)" : "gl_InstanceIndex");
        return renameEntryPoint(withInstanceIndex) + DEPTH_RANGE_ADAPTER;
    }

    private String renameEntryPoint(String source) {
        Matcher matcher = VERTEX_ENTRY.matcher(source);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Vertex shader has no main() to adapt.");
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(RENAMED_ENTRY));
    }
}
