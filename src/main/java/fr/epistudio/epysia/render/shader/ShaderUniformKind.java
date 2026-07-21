package fr.epistudio.epysia.render.shader;

import fr.epistudio.epysia.exceptions.EpysiaException;

public enum ShaderUniformKind {
    FLOAT("float", 4, 4),
    INT("int", 4, 4),
    BOOL("bool", 4, 4),
    VECTOR2("vec2", 8, 8),
    VECTOR3("vec3", 12, 16),
    VECTOR4("vec4", 16, 16),
    MATRIX4("mat4", 64, 16),
    SAMPLER2D("sampler2D", 0, 0);

    public static final int ARRAY_ELEMENT_STRIDE = 16;

    private final String glslToken;
    private final int byteSize;
    private final int byteAlignment;

    ShaderUniformKind(String glslToken, int byteSize, int byteAlignment) {
        this.glslToken = glslToken;
        this.byteSize = byteSize;
        this.byteAlignment = byteAlignment;
    }

    public String glslToken() {
        return glslToken;
    }

    public int byteSize() {
        return byteSize;
    }

    public int byteAlignment() {
        return byteAlignment;
    }

    public static ShaderUniformKind fromGlslToken(String token) {
        for (ShaderUniformKind kind : values()) {
            if (kind.glslToken.equals(token)) {
                return kind;
            }
        }
        throw new EpysiaException("Unsupported post effect uniform type: " + token);
    }
}
