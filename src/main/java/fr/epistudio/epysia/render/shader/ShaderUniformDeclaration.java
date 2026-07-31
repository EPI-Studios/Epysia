package fr.epistudio.epysia.render.shader;

public record ShaderUniformDeclaration(String name, ShaderUniformKind kind, int arraySize, boolean color,
                                           String defaultText, boolean standardRedGreenBlue,
                                           boolean tangentNormal) {

    public ShaderUniformDeclaration(String name, ShaderUniformKind kind, int arraySize, boolean color) {
        this(name, kind, arraySize, color, "", false, false);
    }

    public ShaderUniformDeclaration(String name, ShaderUniformKind kind, int arraySize, boolean color,
                                    String defaultText) {
        this(name, kind, arraySize, color, defaultText, false, false);
    }

    public boolean isArray() {
        return arraySize > 0;
    }

    public boolean hasDefault() {
        return !defaultText.isEmpty();
    }

    public boolean isSampler() {
        return kind == ShaderUniformKind.SAMPLER2D;
    }

    public int packedByteSize() {
        return isArray() ? arraySize * ShaderUniformKind.ARRAY_ELEMENT_STRIDE : kind.byteSize();
    }

    public int packedByteAlignment() {
        return isArray() ? ShaderUniformKind.ARRAY_ELEMENT_STRIDE : kind.byteAlignment();
    }
}
