package fr.epistudio.epysia.render.shader;

import java.nio.ByteBuffer;

public final class ShaderUniformPacker {

    private ShaderUniformPacker() {
    }

    public static void pack(ByteBuffer destination, int offset, ShaderUniformDeclaration declaration,
                     ShaderUniformValue value) {
        if (declaration.isArray()) {
            packArray(destination, offset, declaration, value);
            return;
        }
        switch (declaration.kind()) {
            case FLOAT -> destination.putFloat(offset, asFloat(value));
            case INT -> destination.putInt(offset, asInt(value));
            case BOOL -> destination.putInt(offset, asInt(value) != 0 ? 1 : 0);
            case VECTOR2, VECTOR3, VECTOR4 -> packVector(destination, offset, value);
            case MATRIX4 -> packMatrix(destination, offset, value);
            case SAMPLER2D -> {
            }
        }
    }

    private static void packVector(ByteBuffer destination, int offset, ShaderUniformValue value) {
        float[] components = vectorComponents(value);
        for (int index = 0; index < components.length; index++) {
            destination.putFloat(offset + index * Float.BYTES, components[index]);
        }
    }

    private static float[] vectorComponents(ShaderUniformValue value) {
        return switch (value) {
            case ShaderUniformValue.Vector2Value vector -> new float[] {vector.x(), vector.y()};
            case ShaderUniformValue.Vector3Value vector -> new float[] {vector.x(), vector.y(), vector.z()};
            case ShaderUniformValue.Vector4Value vector -> new float[] {vector.x(), vector.y(), vector.z(), vector.w()};
            default -> new float[0];
        };
    }

    private static void packMatrix(ByteBuffer destination, int offset, ShaderUniformValue value) {
        if (!(value instanceof ShaderUniformValue.Matrix4Value matrix)) {
            return;
        }
        for (int index = 0; index < matrix.columnMajorElements().length; index++) {
            destination.putFloat(offset + index * Float.BYTES, matrix.columnMajorElements()[index]);
        }
    }

    private static void packArray(ByteBuffer destination, int offset, ShaderUniformDeclaration declaration,
                                  ShaderUniformValue value) {
        if (declaration.kind() == ShaderUniformKind.FLOAT
                && value instanceof ShaderUniformValue.FloatArrayValue floats) {
            int count = Math.min(declaration.arraySize(), floats.elements().length);
            for (int index = 0; index < count; index++) {
                destination.putFloat(offset + index * ShaderUniformKind.ARRAY_ELEMENT_STRIDE,
                        floats.elements()[index]);
            }
        } else if (declaration.kind() == ShaderUniformKind.VECTOR4
                && value instanceof ShaderUniformValue.Vector4ArrayValue vectors) {
            packVector4Array(destination, offset, declaration, vectors);
        }
    }

    private static void packVector4Array(ByteBuffer destination, int offset,
                                         ShaderUniformDeclaration declaration,
                                         ShaderUniformValue.Vector4ArrayValue vectors) {
        int count = Math.min(declaration.arraySize(), vectors.flattenedElements().length / 4);
        for (int index = 0; index < count; index++) {
            for (int component = 0; component < 4; component++) {
                destination.putFloat(offset + index * ShaderUniformKind.ARRAY_ELEMENT_STRIDE
                        + component * Float.BYTES, vectors.flattenedElements()[index * 4 + component]);
            }
        }
    }

    private static float asFloat(ShaderUniformValue value) {
        return switch (value) {
            case ShaderUniformValue.FloatValue number -> number.value();
            case ShaderUniformValue.IntValue number -> (float) number.value();
            case ShaderUniformValue.BoolValue flag -> flag.value() ? 1.0f : 0.0f;
            default -> 0.0f;
        };
    }

    private static int asInt(ShaderUniformValue value) {
        return switch (value) {
            case ShaderUniformValue.IntValue number -> number.value();
            case ShaderUniformValue.FloatValue number -> (int) number.value();
            case ShaderUniformValue.BoolValue flag -> flag.value() ? 1 : 0;
            default -> 0;
        };
    }
}
