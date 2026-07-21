package fr.epistudio.epysia.render.postfx;

import fr.epistudio.epysia.render.shader.ShaderUniformParser.ParsedSource;

import java.util.ArrayList;
import java.util.List;
import fr.epistudio.epysia.render.shader.ShaderUniformParser;
import fr.epistudio.epysia.render.shader.ShaderUniformDeclaration;

public final class PostEffectComposer {

    public static final int SCENE_COLOR_BINDING = 0;
    public static final int SCENE_DEPTH_BINDING = 1;
    public static final int FRAME_UNIFORM_BINDING = 2;
    public static final int USER_UNIFORM_BINDING = 3;
    public static final int FIRST_SAMPLER_BINDING = 4;
    public static final int FRAME_UNIFORM_SIZE = 96;
    public static final int FRAME_INVERSE_VIEW_PROJECTION_OFFSET = 32;

    private static final String UNIFORMS_MARKER = "// POST_EFFECT_UNIFORMS";
    private static final String FUNCTIONS_MARKER = "// POST_EFFECT_FUNCTIONS";
    private static final String EMPTY_BLOCK_MEMBER = "float postEffectUnusedUniform;";

    private PostEffectComposer() {
    }

    public static String compose(String templateSource, ParsedSource parsed) {
        String result = replaceMarker(templateSource, UNIFORMS_MARKER, uniformsBlock(parsed));
        return replaceMarker(result, FUNCTIONS_MARKER, parsed.body());
    }

    private static String uniformsBlock(ParsedSource parsed) {
        StringBuilder block = new StringBuilder();
        block.append("layout(std140, binding = ").append(USER_UNIFORM_BINDING)
                .append(") uniform PostEffectUniforms {\n");
        appendBufferMembers(block, parsed);
        block.append("};\n");
        appendSamplers(block, parsed);
        return block.toString();
    }

    private static void appendBufferMembers(StringBuilder block, ParsedSource parsed) {
        List<ShaderUniformDeclaration> bufferDeclarations = parsed.bufferDeclarations();
        if (bufferDeclarations.isEmpty()) {
            block.append("    ").append(EMPTY_BLOCK_MEMBER).append('\n');
            return;
        }
        for (ShaderUniformDeclaration declaration : bufferDeclarations) {
            block.append("    ").append(declaration.kind().glslToken()).append(' ').append(declaration.name());
            if (declaration.isArray()) {
                block.append('[').append(declaration.arraySize()).append(']');
            }
            block.append(";\n");
        }
    }

    private static void appendSamplers(StringBuilder block, ParsedSource parsed) {
        int binding = FIRST_SAMPLER_BINDING;
        for (ShaderUniformDeclaration declaration : parsed.samplerDeclarations()) {
            block.append("layout(binding = ").append(binding).append(") uniform sampler2D ")
                    .append(declaration.name()).append(";\n");
            binding++;
        }
    }

    private static String replaceMarker(String source, String marker, String replacement) {
        List<String> lines = new ArrayList<>();
        for (String line : source.split("\n", -1)) {
            lines.add(line.trim().equals(marker) ? replacement : line);
        }
        return String.join("\n", lines);
    }
}
