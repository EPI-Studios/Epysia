package fr.epistudio.epysia.render.shader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.epistudio.epysia.render.shader.ShaderUniformParser.ParsedSource;

public final class SurfaceShaderComposer {

    public static final int USER_UNIFORM_BINDING = 24;
    public static final int FIRST_SAMPLER_BINDING = 16;
    public static final String SKINNED_DEFINE = "#define SKINNED\n";
    public static final String VERTEX_COLORED_DEFINE = "#define VERTEX_COLORED\n";
    private static final String VERSION_DIRECTIVE = "#version";

    private static final String UNIFORM_BLOCK_NAME = "SurfaceUniforms";
    private static final String FUNCTIONS_MARKER = "// SURFACE_FUNCTIONS";
    private static final String VERTEX_CALL_MARKER = "// SURFACE_VERTEX_CALL";
    private static final String COLOR_CALL_MARKER = "// SURFACE_COLOR_CALL";
    private static final String NORMAL_CALL_MARKER = "// SURFACE_NORMAL_CALL";
    private static final String SHADE_CALL_MARKER = "// SURFACE_SHADE_CALL";
    private static final Pattern VERTEX_FUNCTION_PATTERN = Pattern.compile("void\\s+surfaceVertex\\s*\\(");
    private static final Pattern COLOR_FUNCTION_PATTERN = Pattern.compile("void\\s+surfaceColor\\s*\\(");
    private static final Pattern LIGHT_FUNCTION_PATTERN = Pattern.compile("void\\s+surfaceLight\\s*\\(");
    private static final Pattern NORMAL_FUNCTION_PATTERN = Pattern.compile("void\\s+surfaceNormal\\s*\\(");
    private static final Pattern SHADE_FUNCTION_PATTERN = Pattern.compile("void\\s+surfaceShade\\s*\\(");
    private static final Pattern UNSHADED_MODE_PATTERN =
            Pattern.compile("render_mode\\s+[^;]*\\bunshaded\\b[^;]*;");
    private static final Pattern TIME_IDENTIFIER_PATTERN = Pattern.compile("\\b(time|frameTime)\\b");
    private static final String LIGHT_ENABLED_DEFINE = "#define SURFACE_LIGHT_ENABLED\n";
    private static final String UNSHADED_DEFINE = "#define SURFACE_UNSHADED\n";

    private SurfaceShaderComposer() {
    }

    public static ParsedSource parseUniforms(LoadedShader surface) {
        return ShaderUniformParser.parse(surface.source());
    }

    public static boolean shadowVertexUsesTime(LoadedShader surface) {
        ParsedSource parsed = ShaderUniformParser.parse(surface.source());
        String vertexBody = ShaderComments.mask(split(parsed.body()).vertexBody());
        return TIME_IDENTIFIER_PATTERN.matcher(vertexBody).find();
    }

    public static LoadedShader injectSkinningDefine(LoadedShader vertex) {
        return new LoadedShader(insertAfterVersion(vertex.source(), SKINNED_DEFINE), vertex.dependencyPaths());
    }

    public static LoadedShader injectVertexColoredDefine(LoadedShader shader) {
        return new LoadedShader(insertAfterVersion(shader.source(), VERTEX_COLORED_DEFINE), shader.dependencyPaths());
    }

    public static LoadedShader injectUniformBlock(LoadedShader shader, ParsedSource merged) {
        if (merged.declarations().isEmpty()) {
            return shader;
        }
        String stripped = ShaderUniformParser.parse(shader.source()).body();
        return new LoadedShader(insertAfterVersion(stripped, uniformsBlock(merged)), shader.dependencyPaths());
    }

    public static LoadedShader injectDefineBlock(LoadedShader shader, String defineBlock) {
        return new LoadedShader(insertAfterVersion(shader.source(), defineBlock), shader.dependencyPaths());
    }

    private static String insertAfterVersion(String source, String directive) {
        List<String> lines = new ArrayList<>();
        boolean inserted = false;
        for (String line : source.split("\n", -1)) {
            lines.add(line);
            if (!inserted && line.trim().startsWith(VERSION_DIRECTIVE)) {
                lines.add(directive.stripTrailing());
                inserted = true;
            }
        }
        return String.join("\n", lines);
    }

    private static String defaultVertexFunction() {
        return ShaderSnippets.block("surface/default_vertex.glsl");
    }

    private static String defaultColorFunction() {
        return ShaderSnippets.block("surface/default_color.glsl");
    }

    private static String defaultNormalFunction() {
        return ShaderSnippets.block("surface/default_normal.glsl");
    }

    private static String defaultShadeFunction() {
        return ShaderSnippets.block("surface/default_shade.glsl");
    }

    private static String litVertexCall() {
        return ShaderSnippets.block("surface/lit_vertex_call.glsl");
    }

    private static String shadowVertexCall() {
        return ShaderSnippets.block("surface/shadow_vertex_call.glsl");
    }

    private static String frozenShadowVertexCall() {
        return ShaderSnippets.block("surface/frozen_shadow_vertex_call.glsl");
    }

    private static String colorCall() {
        return ShaderSnippets.line("surface/color_call.glsl");
    }

    private static String normalCall() {
        return ShaderSnippets.line("surface/normal_call.glsl");
    }

    private static String shadeCall() {
        return ShaderSnippets.line("surface/shade_call.glsl");
    }

    public static LoadedShader composeVertex(LoadedShader base, LoadedShader surface) {
        return compose(base, surface, SurfaceSplit::vertexBlock, VERTEX_CALL_MARKER, litVertexCall(),
                VERTEX_MODEL_EXPRESSION);
    }

    public static LoadedShader composeShadowVertex(LoadedShader base, LoadedShader surface) {
        return compose(base, surface, SurfaceSplit::vertexBlock, VERTEX_CALL_MARKER, shadowVertexCall(),
                VERTEX_MODEL_EXPRESSION);
    }

    public static LoadedShader composeFrozenShadowVertex(LoadedShader base, LoadedShader surface) {
        return compose(base, surface, SurfaceSplit::vertexBlock, VERTEX_CALL_MARKER, frozenShadowVertexCall(),
                VERTEX_MODEL_EXPRESSION);
    }

    public static LoadedShader composeFragment(LoadedShader base, LoadedShader surface) {
        LoadedShader composed = compose(base, surface, SurfaceSplit::fragmentBlock, COLOR_CALL_MARKER, colorCall(),
                FRAGMENT_MODEL_EXPRESSION);
        String source = replaceMarker(composed.source(), NORMAL_CALL_MARKER, normalCall());
        source = replaceMarker(source, SHADE_CALL_MARKER, shadeCall());
        return new LoadedShader(source, composed.dependencyPaths());
    }

    public static boolean declaresUnshaded(LoadedShader surface) {
        return UNSHADED_MODE_PATTERN.matcher(ShaderComments.mask(surface.source())).find();
    }

    private static LoadedShader compose(LoadedShader base, LoadedShader surface,
                                        Function<SurfaceSplit, String> blockSelector,
                                        String callMarker, String callStatement, String modelExpression) {
        ParsedSource parsed = ShaderUniformParser.parse(surface.source());
        SurfaceSplit split = split(parsed.body());
        String functionsBlock = renderModeDefines(surface, split)
                + uniformsBlock(parsed) + objectHelpers(modelExpression) + blockSelector.apply(split);
        String source = replaceMarker(base.source(), FUNCTIONS_MARKER, functionsBlock);
        source = replaceMarker(source, callMarker, callStatement);
        Set<String> dependencies = new LinkedHashSet<>(base.dependencyPaths());
        dependencies.addAll(surface.dependencyPaths());
        return new LoadedShader(source, List.copyOf(dependencies));
    }

    private static final String VERTEX_MODEL_EXPRESSION = "OBJECT_MODEL";
    private static final String FRAGMENT_MODEL_EXPRESSION = "instanceTransforms[surfaceInstanceIndex].model";

    private static String objectHelpers(String modelExpression) {
        return "mat4 objectToWorld() { return " + modelExpression + "; }\n"
                + "vec3 objectOrigin() { return " + modelExpression + "[3].xyz; }\n"
                + "vec3 objectScale() {\n"
                + "    mat4 objectModel = " + modelExpression + ";\n"
                + "    return vec3(length(objectModel[0].xyz), length(objectModel[1].xyz), length(objectModel[2].xyz));\n"
                + "}\n";
    }

    private static String uniformsBlock(ParsedSource parsed) {
        StringBuilder block = new StringBuilder();
        appendUniformBuffer(block, parsed.bufferDeclarations());
        appendSamplers(block, parsed.samplerDeclarations());
        return block.toString();
    }

    private static void appendUniformBuffer(StringBuilder block, List<ShaderUniformDeclaration> declarations) {
        if (declarations.isEmpty()) {
            return;
        }
        block.append("layout(std140, binding = ").append(USER_UNIFORM_BINDING)
                .append(") uniform ").append(UNIFORM_BLOCK_NAME).append(" {\n");
        for (ShaderUniformDeclaration declaration : declarations) {
            block.append("    ").append(declaration.kind().glslToken()).append(' ').append(declaration.name());
            if (declaration.isArray()) {
                block.append('[').append(declaration.arraySize()).append(']');
            }
            block.append(";\n");
        }
        block.append("};\n");
    }

    private static void appendSamplers(StringBuilder block, List<ShaderUniformDeclaration> declarations) {
        int binding = FIRST_SAMPLER_BINDING;
        for (ShaderUniformDeclaration declaration : declarations) {
            block.append("layout(binding = ").append(binding).append(") uniform sampler2D ")
                    .append(declaration.name()).append(";\n");
            binding++;
        }
    }

    private static SurfaceSplit split(String source) {
        String masked = maskComments(source);
        Optional<int[]> vertexSpan = functionSpan(masked, VERTEX_FUNCTION_PATTERN);
        Optional<int[]> colorSpan = functionSpan(masked, COLOR_FUNCTION_PATTERN);
        Optional<int[]> lightSpan = functionSpan(masked, LIGHT_FUNCTION_PATTERN);
        Optional<int[]> normalSpan = functionSpan(masked, NORMAL_FUNCTION_PATTERN);
        Optional<int[]> shadeSpan = functionSpan(masked, SHADE_FUNCTION_PATTERN);
        String shared = removeSpans(source, vertexSpan, colorSpan, lightSpan, normalSpan, shadeSpan);
        String vertexFunction = extract(source, vertexSpan, defaultVertexFunction());
        String colorFunction = extract(source, colorSpan, defaultColorFunction());
        String lightFunction = extract(source, lightSpan, "");
        String normalFunction = extract(source, normalSpan, defaultNormalFunction());
        String shadeFunction = extract(source, shadeSpan, defaultShadeFunction());
        return new SurfaceSplit(shared, vertexFunction, colorFunction, lightFunction,
                normalFunction, shadeFunction);
    }

    private static String renderModeDefines(LoadedShader surface, SurfaceSplit split) {
        StringBuilder defines = new StringBuilder();
        if (!split.lightFunction().isEmpty()) {
            defines.append(LIGHT_ENABLED_DEFINE);
        }
        if (declaresUnshaded(surface)) {
            defines.append(UNSHADED_DEFINE);
        }
        return defines.toString();
    }

    private static String extract(String source, Optional<int[]> span, String fallback) {
        return span.map(range -> source.substring(range[0], range[1])).orElse(fallback);
    }

    private static Optional<int[]> functionSpan(String masked, Pattern pattern) {
        Matcher matcher = pattern.matcher(masked);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int bodyStart = masked.indexOf('{', matcher.end());
        if (bodyStart < 0) {
            return Optional.empty();
        }
        return closingBraceIndex(masked, bodyStart)
                .map(bodyEnd -> new int[] {matcher.start(), bodyEnd + 1});
    }

    private static Optional<Integer> closingBraceIndex(String masked, int bodyStart) {
        int depth = 0;
        for (int index = bodyStart; index < masked.length(); index++) {
            if (masked.charAt(index) == '{') {
                depth++;
            } else if (masked.charAt(index) == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(index);
                }
            }
        }
        return Optional.empty();
    }

    @SafeVarargs
    private static String removeSpans(String source, Optional<int[]>... removed) {
        List<int[]> spans = new ArrayList<>();
        for (Optional<int[]> span : removed) {
            span.ifPresent(spans::add);
        }
        spans.sort(Comparator.comparingInt(span -> span[0]));
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (int[] span : spans) {
            result.append(source, cursor, span[0]);
            cursor = span[1];
        }
        return result.append(source.substring(cursor)).toString();
    }

    private static String maskComments(String source) {
        return ShaderComments.mask(source);
    }

    private static String replaceMarker(String source, String marker, String replacement) {
        List<String> lines = new ArrayList<>();
        for (String line : source.split("\n", -1)) {
            lines.add(line.trim().equals(marker) ? replacement : line);
        }
        return String.join("\n", lines);
    }

    private record SurfaceSplit(String shared, String vertexFunction, String colorFunction,
                                String lightFunction, String normalFunction, String shadeFunction) {

        String vertexBlock() {
            return shared + "\n" + vertexFunction;
        }

        String fragmentBlock() {
            return shared + "\n" + colorFunction + "\n" + normalFunction + "\n" + shadeFunction
                    + "\n" + lightFunction;
        }

        String vertexBody() {
            int bodyStart = vertexFunction.indexOf('{');
            return shared + "\n" + (bodyStart < 0 ? vertexFunction : vertexFunction.substring(bodyStart));
        }

        String colorBlock() {
            return shared + "\n" + colorFunction;
        }
    }
}
