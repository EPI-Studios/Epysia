package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.Locale;

public record VfxExpression(String glsl, int components) {

    public static VfxExpression scalar(String glsl) {
        return new VfxExpression(glsl, 1);
    }

    public static VfxExpression vector3(String glsl) {
        return new VfxExpression(glsl, 3);
    }

    public static VfxExpression vector4(String glsl) {
        return new VfxExpression(glsl, 4);
    }

    public static VfxExpression literal(float value) {
        return scalar(floatText(value));
    }

    public static String floatText(float value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    public String asFloat() {
        return components == 1 ? glsl : "(" + glsl + ").x";
    }

    public String asVector3() {
        return switch (components) {
            case 1 -> "vec3(" + glsl + ")";
            case 3 -> glsl;
            case 4 -> "(" + glsl + ").xyz";
            default -> throw unsupported();
        };
    }

    public String asVector4() {
        return switch (components) {
            case 1 -> "vec4(vec3(" + glsl + "), 1.0)";
            case 3 -> "vec4(" + glsl + ", 1.0)";
            case 4 -> glsl;
            default -> throw unsupported();
        };
    }

    public String asComponents(int target) {
        return switch (target) {
            case 1 -> asFloat();
            case 3 -> asVector3();
            case 4 -> asVector4();
            default -> throw unsupported();
        };
    }

    public VfxExpression converted(int target) {
        return new VfxExpression(asComponents(target), target);
    }

    private EpysiaException unsupported() {
        return new EpysiaException("VFX expressions carry one, three or four components, not " + components + ".");
    }
}
