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

    public static final int USER_UNIFORM_BINDING = 4;
    public static final int FIRST_SAMPLER_BINDING = 14;
    public static final String SKINNED_DEFINE = "#define SKINNED\n";
    private static final String VERSION_DIRECTIVE = "#version";

    private static final String UNIFORM_BLOCK_NAME = "SurfaceUniforms";
    private static final String FUNCTIONS_MARKER = "// SURFACE_FUNCTIONS";
    private static final String VERTEX_CALL_MARKER = "// SURFACE_VERTEX_CALL";
    private static final String COLOR_CALL_MARKER = "// SURFACE_COLOR_CALL";
    private static final Pattern VERTEX_FUNCTION_PATTERN = Pattern.compile("void\\s+surfaceVertex\\s*\\(");
    private static final Pattern COLOR_FUNCTION_PATTERN = Pattern.compile("void\\s+surfaceColor\\s*\\(");
    private static final Pattern LIGHT_FUNCTION_PATTERN = Pattern.compile("void\\s+surfaceLight\\s*\\(");
    private static final Pattern UNSHADED_MODE_PATTERN =
            Pattern.compile("render_mode\\s+[^;]*\\bunshaded\\b[^;]*;");
    private static final Pattern TIME_IDENTIFIER_PATTERN = Pattern.compile("\\b(time|frameTime)\\b");
    private static final String LIGHT_ENABLED_DEFINE = "#define SURFACE_LIGHT_ENABLED\n";
    private static final String UNSHADED_DEFINE = "#define SURFACE_UNSHADED\n";
    private static final String DEFAULT_VERTEX_FUNCTION = """
            void surfaceVertex(inout vec3 worldPosition, in vec3 localPosition, in vec3 worldNormal, in vec2 uv, in float time) {
            }
            """;
    private static final String DEFAULT_COLOR_FUNCTION = """
            void surfaceColor(inout vec4 albedoColor, inout float metallic, inout float roughness, inout vec3 emissive, in vec2 uv, in vec3 worldPosition, in float time) {
            }
            """;
    private static final String LIT_VERTEX_CALL = """
            surfaceVertex(vertexWorldPosition, inPosition, vertexWorldNormal, vertexUv, frameTime());
                worldPosition = vec4(vertexWorldPosition, 1.0);
            """;
    private static final String SHADOW_VERTEX_CALL = """
            vec3 surfaceWorldPosition = worldPosition.xyz;
                surfaceVertex(surfaceWorldPosition, inPosition, normalize(mat3(OBJECT_NORMAL_MATRIX) * inNormal), inUv, frameTime());
                worldPosition = vec4(surfaceWorldPosition, 1.0);
            """;
    private static final String FROZEN_SHADOW_VERTEX_CALL = """
            vec3 surfaceWorldPosition = worldPosition.xyz;
                surfaceVertex(surfaceWorldPosition, inPosition, normalize(mat3(OBJECT_NORMAL_MATRIX) * inNormal), inUv, 0.0);
                worldPosition = vec4(surfaceWorldPosition, 1.0);
            """;
    private static final String COLOR_CALL =
            "surfaceColor(albedoColor, metallic, roughness, emissive, vertexUv, vertexWorldPosition, frameTime());";

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

    public static LoadedShader composeVertex(LoadedShader base, LoadedShader surface) {
        return compose(base, surface, SurfaceSplit::vertexBlock, VERTEX_CALL_MARKER, LIT_VERTEX_CALL);
    }

    public static LoadedShader composeShadowVertex(LoadedShader base, LoadedShader surface) {
        return compose(base, surface, SurfaceSplit::vertexBlock, VERTEX_CALL_MARKER, SHADOW_VERTEX_CALL);
    }

    public static LoadedShader composeFrozenShadowVertex(LoadedShader base, LoadedShader surface) {
        return compose(base, surface, SurfaceSplit::vertexBlock, VERTEX_CALL_MARKER, FROZEN_SHADOW_VERTEX_CALL);
    }

    public static LoadedShader composeFragment(LoadedShader base, LoadedShader surface) {
        return compose(base, surface, SurfaceSplit::fragmentBlock, COLOR_CALL_MARKER, COLOR_CALL);
    }

    public static boolean declaresUnshaded(LoadedShader surface) {
        return UNSHADED_MODE_PATTERN.matcher(ShaderComments.mask(surface.source())).find();
    }

    private static LoadedShader compose(LoadedShader base, LoadedShader surface,
                                        Function<SurfaceSplit, String> blockSelector,
                                        String callMarker, String callStatement) {
        ParsedSource parsed = ShaderUniformParser.parse(surface.source());
        SurfaceSplit split = split(parsed.body());
        String functionsBlock = renderModeDefines(surface, split)
                + uniformsBlock(parsed) + OBJECT_HELPERS + blockSelector.apply(split);
        String source = replaceMarker(base.source(), FUNCTIONS_MARKER, functionsBlock);
        source = replaceMarker(source, callMarker, callStatement);
        Set<String> dependencies = new LinkedHashSet<>(base.dependencyPaths());
        dependencies.addAll(surface.dependencyPaths());
        return new LoadedShader(source, List.copyOf(dependencies));
    }

    private static final String OBJECT_HELPERS = """
            mat4 objectToWorld() { return object.model; }
            vec3 objectOrigin() { return object.model[3].xyz; }
            vec3 objectScale() {
                return vec3(length(object.model[0].xyz), length(object.model[1].xyz), length(object.model[2].xyz));
            }
            """;

    private static final Pattern OBJECT_HELPER_PATTERN =
            Pattern.compile("\\b(objectToWorld|objectOrigin|objectScale)\\s*\\(");

    public static boolean usesObjectHelpers(LoadedShader surface) {
        return OBJECT_HELPER_PATTERN.matcher(ShaderComments.mask(surface.source())).find();
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
        String shared = removeSpans(source, vertexSpan, colorSpan, lightSpan);
        String vertexFunction = extract(source, vertexSpan, DEFAULT_VERTEX_FUNCTION);
        String colorFunction = extract(source, colorSpan, DEFAULT_COLOR_FUNCTION);
        String lightFunction = extract(source, lightSpan, "");
        return new SurfaceSplit(shared, vertexFunction, colorFunction, lightFunction);
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
                                String lightFunction) {

        String vertexBlock() {
            return shared + "\n" + vertexFunction;
        }

        String fragmentBlock() {
            return shared + "\n" + colorFunction + "\n" + lightFunction;
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
