package fr.epistudio.epysia.editor.scripteditor;

import imgui.extension.texteditor.TextEditorLanguage;

import java.util.List;
import java.util.Set;

public final class GlslLanguageDefinition {

    private static final Set<String> DECLARATIONS = Set.of("struct", "layout", "uniform", "buffer");

    private static final List<String> KEYWORDS = List.of(
            "attribute", "break", "buffer", "case", "centroid", "coherent", "const", "continue",
            "default", "discard", "do", "else", "flat", "for", "highp", "if", "in", "inout",
            "invariant", "layout", "lowp", "mediump", "noperspective", "out", "patch", "precision",
            "readonly", "restrict", "return", "sample", "shared", "smooth", "struct", "subroutine",
            "switch", "uniform", "varying", "volatile", "while", "writeonly",
            "bool", "int", "uint", "float", "double", "void",
            "vec2", "vec3", "vec4", "bvec2", "bvec3", "bvec4", "ivec2", "ivec3", "ivec4",
            "uvec2", "uvec3", "uvec4", "dvec2", "dvec3", "dvec4",
            "mat2", "mat3", "mat4", "mat2x2", "mat2x3", "mat2x4", "mat3x2", "mat3x3", "mat3x4",
            "mat4x2", "mat4x3", "mat4x4",
            "sampler1D", "sampler2D", "sampler3D", "samplerCube", "sampler2DArray",
            "sampler2DShadow", "samplerCubeShadow", "isampler2D", "usampler2D",
            "image2D", "image3D", "imageCube", "atomic_uint",
            "gl_Position", "gl_FragCoord", "gl_FragDepth", "gl_VertexID", "gl_InstanceID",
            "gl_GlobalInvocationID", "gl_LocalInvocationID", "gl_WorkGroupID", "gl_PointSize");

    private GlslLanguageDefinition() {
    }

    public static TextEditorLanguage create(JavaSymbols symbols) {
        return CurlyBraceLanguage.create("GLSL",
                CurlyBraceLanguage.without(KEYWORDS, DECLARATIONS), DECLARATIONS, symbols);
    }

    public static List<String> keywords() {
        return KEYWORDS;
    }
}
