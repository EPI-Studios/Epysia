package fr.epistudio.epysia.graph.shader;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.PinType;

public record ShaderExpression(PinType type, String code) {

    public static String glslType(PinType type) {
        return switch (type) {
            case FLOAT -> "float";
            case VECTOR2 -> "vec2";
            case VECTOR3 -> "vec3";
            case VECTOR4 -> "vec4";
            default -> throw new EpysiaException("Type has no GLSL equivalent: " + type);
        };
    }

    public static int componentCount(PinType type) {
        return switch (type) {
            case FLOAT -> 1;
            case VECTOR2 -> 2;
            case VECTOR3 -> 3;
            case VECTOR4 -> 4;
            default -> throw new EpysiaException("Type has no GLSL equivalent: " + type);
        };
    }

    public ShaderExpression promoteTo(PinType target) {
        if (target == PinType.NUMERIC || target == type) {
            return this;
        }
        if (type == PinType.FLOAT && target.isShaderValue()) {
            return new ShaderExpression(target, glslType(target) + "(" + code + ")");
        }
        throw new EpysiaException("Cannot convert " + type + " to " + target + " in shader graph");
    }
}
