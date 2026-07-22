package fr.epistudio.epysia.graph.vfx;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.graph.GraphNode;

final class VfxMathEmitter {

    private final VfxExpressionEmitter values;

    VfxMathEmitter(VfxExpressionEmitter values) {
        this.values = values;
    }

    VfxExpression emit(GraphNode node, String outputPin) {
        return switch (node.typeKey()) {
            case VfxNodes.MATH_ADD -> componentwise(node, "(%s + %s)", 0.0f);
            case VfxNodes.MATH_SUBTRACT -> componentwise(node, "(%s - %s)", 0.0f);
            case VfxNodes.MATH_MULTIPLY -> componentwise(node, "(%s * %s)", 1.0f);
            case VfxNodes.MATH_DIVIDE -> emitDivide(node);
            case VfxNodes.MATH_DOT -> emitDot(node);
            case VfxNodes.MATH_CROSS -> emitCross(node);
            case VfxNodes.MATH_LERP -> emitLerp(node);
            case VfxNodes.MATH_CLAMP -> emitClamp(node);
            case VfxNodes.MATH_REMAP -> emitRemap(node);
            case VfxNodes.MATH_ONE_MINUS -> unary(node, "(1.0 - %s)");
            case VfxNodes.MATH_NORMALIZE -> unary(node, "normalize(%s)");
            case VfxNodes.MATH_SINE -> unary(node, "sin(%s)");
            case VfxNodes.MATH_LENGTH -> VfxExpression.scalar("length(%s)".formatted(value(node).glsl()));
            case VfxNodes.MATH_VECTOR3 -> emitVector3(node);
            case VfxNodes.MATH_VECTOR4 -> emitVector4(node);
            case VfxNodes.MATH_SPLIT_VECTOR3 -> emitSplit(node, 3, outputPin);
            case VfxNodes.MATH_SPLIT_VECTOR4 -> emitSplit(node, 4, outputPin);
            default -> throw new EpysiaException(
                    "VFX graphs do not support node " + node.typeKey() + " yet.");
        };
    }

    private record Operands(VfxExpression left, VfxExpression right, int components) {
    }

    private Operands operands(GraphNode node, float fallback) {
        VfxExpression left = values.inputOf(node, VfxNodes.A_PIN, VfxExpression.literal(fallback));
        VfxExpression right = values.inputOf(node, VfxNodes.B_PIN, VfxExpression.literal(fallback));
        int components = Math.max(left.components(), right.components());
        return new Operands(left.converted(components), right.converted(components), components);
    }

    private VfxExpression componentwise(GraphNode node, String pattern, float fallback) {
        Operands pair = operands(node, fallback);
        return new VfxExpression(pattern.formatted(pair.left().glsl(), pair.right().glsl()), pair.components());
    }

    private VfxExpression emitDivide(GraphNode node) {
        Operands pair = operands(node, 1.0f);
        return new VfxExpression(divide(pair.left().glsl(), pair.right().glsl()), pair.components());
    }

    private String divide(String numerator, String denominator) {
        if (values.stage() == VfxStage.RENDER) {
            return "(%s / %s)".formatted(numerator, denominator);
        }
        return "safeDivide(%s, %s)".formatted(numerator, denominator);
    }

    private VfxExpression emitDot(GraphNode node) {
        Operands pair = operands(node, 0.0f);
        return VfxExpression.scalar("dot(%s, %s)".formatted(pair.left().glsl(), pair.right().glsl()));
    }

    private VfxExpression emitCross(GraphNode node) {
        VfxExpression left = values.inputOf(node, VfxNodes.A_PIN, VfxExpression.vector3("vec3(0.0)"));
        VfxExpression right = values.inputOf(node, VfxNodes.B_PIN, VfxExpression.vector3("vec3(0.0)"));
        return VfxExpression.vector3("cross(%s, %s)".formatted(left.asVector3(), right.asVector3()));
    }

    private VfxExpression emitLerp(GraphNode node) {
        Operands pair = operands(node, 0.0f);
        String progress = values.inputOf(node, VfxNodes.T_PIN, VfxExpression.literal(0.5f)).asFloat();
        return new VfxExpression("mix(%s, %s, %s)".formatted(
                pair.left().glsl(), pair.right().glsl(), progress), pair.components());
    }

    private VfxExpression emitClamp(GraphNode node) {
        VfxExpression value = value(node);
        String minimum = values.inputOf(node, VfxNodes.MINIMUM_PIN, VfxExpression.literal(0.0f)).asFloat();
        String maximum = values.inputOf(node, VfxNodes.MAXIMUM_PIN, VfxExpression.literal(1.0f)).asFloat();
        return new VfxExpression("clamp(%s, %s, %s)".formatted(value.glsl(), minimum, maximum),
                value.components());
    }

    private VfxExpression emitRemap(GraphNode node) {
        VfxExpression value = value(node);
        String fromMinimum = bound(node, VfxNodes.FROM_MINIMUM_PIN, 0.0f);
        String fromMaximum = bound(node, VfxNodes.FROM_MAXIMUM_PIN, 1.0f);
        String toMinimum = bound(node, VfxNodes.TO_MINIMUM_PIN, 0.0f);
        String toMaximum = bound(node, VfxNodes.TO_MAXIMUM_PIN, 1.0f);
        String scale = divide("(%s - %s)".formatted(toMaximum, toMinimum),
                "(%s - %s)".formatted(fromMaximum, fromMinimum));
        return new VfxExpression("(%s + (%s - %s) * %s)".formatted(
                toMinimum, value.glsl(), fromMinimum, scale), value.components());
    }

    private String bound(GraphNode node, String pin, float fallback) {
        return values.inputOf(node, pin, VfxExpression.literal(fallback)).asFloat();
    }

    private VfxExpression unary(GraphNode node, String pattern) {
        VfxExpression value = value(node);
        return new VfxExpression(pattern.formatted(value.glsl()), value.components());
    }

    private VfxExpression value(GraphNode node) {
        return values.inputOf(node, VfxNodes.VALUE_PIN, VfxExpression.literal(0.0f));
    }

    private VfxExpression emitVector3(GraphNode node) {
        return VfxExpression.vector3("vec3(%s, %s, %s)".formatted(
                component(node, VfxNodes.X_PIN, 0.0f),
                component(node, VfxNodes.Y_PIN, 0.0f),
                component(node, VfxNodes.Z_PIN, 0.0f)));
    }

    private VfxExpression emitVector4(GraphNode node) {
        return VfxExpression.vector4("vec4(%s, %s, %s, %s)".formatted(
                component(node, VfxNodes.X_PIN, 0.0f),
                component(node, VfxNodes.Y_PIN, 0.0f),
                component(node, VfxNodes.Z_PIN, 0.0f),
                component(node, VfxNodes.W_PIN, 1.0f)));
    }

    private String component(GraphNode node, String pin, float fallback) {
        return values.inputOf(node, pin, VfxExpression.literal(fallback)).asFloat();
    }

    private VfxExpression emitSplit(GraphNode node, int components, String outputPin) {
        VfxExpression value = values.inputOf(node, VfxNodes.VALUE_PIN,
                components == 4 ? VfxExpression.vector4("vec4(0.0)") : VfxExpression.vector3("vec3(0.0)"));
        return VfxExpression.scalar("(%s).%s".formatted(
                value.asComponents(components), swizzle(outputPin)));
    }

    private static String swizzle(String outputPin) {
        return switch (outputPin) {
            case VfxNodes.Y_PIN -> "y";
            case VfxNodes.Z_PIN -> "z";
            case VfxNodes.W_PIN -> "w";
            default -> "x";
        };
    }
}
