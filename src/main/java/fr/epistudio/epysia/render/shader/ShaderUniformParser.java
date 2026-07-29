package fr.epistudio.epysia.render.shader;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.shader.ShaderComments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderUniformParser {

    private static final Pattern UNIFORM_PATTERN = Pattern.compile(
            "\\buniform\\s+(float|int|bool|vec2|vec3|vec4|mat4|sampler2D)\\s+(\\w+)\\s*(?:\\[\\s*(\\d+)\\s*\\])?\\s*;");
    private static final String COLOR_ANNOTATION = "@color";
    private static final String DEFAULT_ANNOTATION = "@default";
    private static final int BLOCK_ALIGNMENT = 16;

    private ShaderUniformParser() {
    }

    public static final class ParsedSource {

        private final List<ShaderUniformDeclaration> declarations;
        private final String body;
        private final Map<String, Integer> byteOffsetsByName;
        private final int uniformBufferSize;
        private final List<ShaderUniformDeclaration> samplerDeclarations;
        private final List<ShaderUniformDeclaration> bufferDeclarations;

        public ParsedSource(List<ShaderUniformDeclaration> declarations, String body,
                            Map<String, Integer> byteOffsetsByName, int uniformBufferSize) {
            this.declarations = declarations;
            this.body = body;
            this.byteOffsetsByName = byteOffsetsByName;
            this.uniformBufferSize = uniformBufferSize;
            List<ShaderUniformDeclaration> samplers = new ArrayList<>();
            List<ShaderUniformDeclaration> buffers = new ArrayList<>();
            for (ShaderUniformDeclaration declaration : declarations) {
                if (declaration.isSampler()) {
                    samplers.add(declaration);
                } else {
                    buffers.add(declaration);
                }
            }
            this.samplerDeclarations = List.copyOf(samplers);
            this.bufferDeclarations = List.copyOf(buffers);
        }

        public List<ShaderUniformDeclaration> declarations() {
            return declarations;
        }

        public String body() {
            return body;
        }

        public Map<String, Integer> byteOffsetsByName() {
            return byteOffsetsByName;
        }

        public int uniformBufferSize() {
            return uniformBufferSize;
        }

        public List<ShaderUniformDeclaration> samplerDeclarations() {
            return samplerDeclarations;
        }

        public List<ShaderUniformDeclaration> bufferDeclarations() {
            return bufferDeclarations;
        }

        public boolean hasBufferDeclarations() {
            return !bufferDeclarations.isEmpty();
        }

        public static ParsedSource empty() {
            return new ParsedSource(List.of(), "", Map.of(), 0);
        }
    }

    public static ParsedSource parse(String source) {
        String masked = ShaderComments.mask(source);
        List<ShaderUniformDeclaration> declarations = new ArrayList<>();
        List<int[]> spans = new ArrayList<>();
        Matcher matcher = UNIFORM_PATTERN.matcher(masked);
        while (matcher.find()) {
            if (isQualified(masked, matcher.start())) {
                continue;
            }
            declarations.add(toDeclaration(source, matcher));
            spans.add(new int[] {matcher.start(), matcher.end()});
        }
        return new ParsedSource(List.copyOf(declarations), removeSpans(source, spans),
                computeOffsets(declarations), computeBufferSize(declarations));
    }

    public static ParsedSource merge(List<ParsedSource> sources) {
        Map<String, ShaderUniformDeclaration> byName = new LinkedHashMap<>();
        for (ParsedSource parsed : sources) {
            for (ShaderUniformDeclaration declaration : parsed.declarations()) {
                byName.putIfAbsent(declaration.name(), declaration);
            }
        }
        List<ShaderUniformDeclaration> declarations = List.copyOf(byName.values());
        return new ParsedSource(declarations, "", computeOffsets(declarations),
                computeBufferSize(declarations));
    }

    private static boolean isQualified(String masked, int declarationStart) {
        int lineStart = masked.lastIndexOf('\n', declarationStart) + 1;
        return masked.substring(lineStart, declarationStart).contains("layout");
    }

    private static ShaderUniformDeclaration toDeclaration(String source, Matcher matcher) {
        ShaderUniformKind kind = ShaderUniformKind.fromGlslToken(matcher.group(1));
        String name = matcher.group(2);
        int arraySize = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        validate(kind, name, arraySize);
        String remainder = lineRemainder(source, matcher.end());
        boolean color = remainder.contains(COLOR_ANNOTATION)
                && (kind == ShaderUniformKind.VECTOR3 || kind == ShaderUniformKind.VECTOR4);
        return new ShaderUniformDeclaration(name, kind, arraySize, color, defaultAnnotation(remainder));
    }

    private static String defaultAnnotation(String remainder) {
        int index = remainder.indexOf(DEFAULT_ANNOTATION);
        if (index < 0) {
            return "";
        }
        String text = remainder.substring(index + DEFAULT_ANNOTATION.length()).strip();
        int nextAnnotation = text.indexOf('@');
        return nextAnnotation < 0 ? text : text.substring(0, nextAnnotation).strip();
    }

    private static void validate(ShaderUniformKind kind, String name, int arraySize) {
        if (arraySize == 0) {
            return;
        }
        if (kind != ShaderUniformKind.FLOAT && kind != ShaderUniformKind.VECTOR4) {
            throw new EpysiaException("Post effect uniform arrays support only float and vec4: " + name);
        }
    }

    private static String lineRemainder(String source, int declarationEnd) {
        int lineEnd = source.indexOf('\n', declarationEnd);
        return lineEnd < 0 ? source.substring(declarationEnd) : source.substring(declarationEnd, lineEnd);
    }

    private static String removeSpans(String source, List<int[]> spans) {
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (int[] span : spans) {
            result.append(source, cursor, span[0]);
            cursor = span[1];
        }
        return result.append(source.substring(cursor)).toString();
    }

    private static Map<String, Integer> computeOffsets(List<ShaderUniformDeclaration> declarations) {
        Map<String, Integer> offsets = new LinkedHashMap<>();
        int cursor = 0;
        for (ShaderUniformDeclaration declaration : declarations) {
            if (declaration.isSampler()) {
                continue;
            }
            int aligned = alignTo(cursor, declaration.packedByteAlignment());
            offsets.put(declaration.name(), aligned);
            cursor = aligned + declaration.packedByteSize();
        }
        return offsets;
    }

    private static int computeBufferSize(List<ShaderUniformDeclaration> declarations) {
        int cursor = 0;
        for (ShaderUniformDeclaration declaration : declarations) {
            if (declaration.isSampler()) {
                continue;
            }
            cursor = alignTo(cursor, declaration.packedByteAlignment()) + declaration.packedByteSize();
        }
        return alignTo(Math.max(cursor, BLOCK_ALIGNMENT), BLOCK_ALIGNMENT);
    }

    private static int alignTo(int offset, int alignment) {
        return ((offset + alignment - 1) / alignment) * alignment;
    }
}
