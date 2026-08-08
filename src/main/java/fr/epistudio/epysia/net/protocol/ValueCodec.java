package fr.epistudio.epysia.net.protocol;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.reflection.ExportedProperty;
import fr.epistudio.epysia.reflection.Reflection;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class ValueCodec {
    public static final float NO_QUANTISATION = 0.0f;

    private ValueCodec() {
    }

    public static ExportedProperty.Kind kindOf(Class<?> type) {
        ExportedProperty.Kind direct = Reflection.kindOf(type);
        if (direct != ExportedProperty.Kind.UNKNOWN) {
            return direct;
        }
        return readOnlyViewKind(type);
    }

    private static ExportedProperty.Kind readOnlyViewKind(Class<?> type) {
        if (type == Vector2fc.class) {
            return ExportedProperty.Kind.VECTOR2;
        }
        if (type == Vector3fc.class) {
            return ExportedProperty.Kind.VECTOR3;
        }
        if (type == Vector4fc.class) {
            return ExportedProperty.Kind.VECTOR4;
        }
        if (type == Quaternionfc.class) {
            return ExportedProperty.Kind.QUATERNION;
        }
        return ExportedProperty.Kind.UNKNOWN;
    }

    public static boolean isSupported(ExportedProperty.Kind kind) {
        return switch (kind) {
            case FLOAT, INT, BOOLEAN, STRING, VECTOR2, VECTOR3, VECTOR4, QUATERNION, ENUM -> true;
            default -> false;
        };
    }

    public static void write(NetWriter writer, ExportedProperty.Kind kind, Object value) {
        write(writer, kind, value, NO_QUANTISATION);
    }

    public static void write(NetWriter writer, ExportedProperty.Kind kind, Object value, float precision) {
        switch (kind) {
            case FLOAT -> writeScalar(writer, (Float) value, precision);
            case INT -> writer.writeSignedVarInt((Integer) value);
            case BOOLEAN -> writer.writeBoolean((Boolean) value);
            case STRING -> writer.writeString(value == null ? "" : (String) value);
            case VECTOR2 -> writeVector2(writer, (Vector2fc) value, precision);
            case VECTOR3 -> writeVector3(writer, (Vector3fc) value, precision);
            case VECTOR4 -> writeVector4(writer, (Vector4fc) value, precision);
            case QUATERNION -> writeQuaternion(writer, (Quaternionfc) value, precision);
            case ENUM -> writer.writeVarInt(value == null ? 0 : ((Enum<?>) value).ordinal());
            default -> throw new EpysiaException("The network codec cannot write values of kind " + kind);
        }
    }

    private static void writeScalar(NetWriter writer, float value, float precision) {
        if (precision <= NO_QUANTISATION) {
            writer.writeFloat(value);
            return;
        }
        writer.writeQuantizedFloat(value, precision);
    }

    private static float readScalar(NetReader reader, float precision) {
        if (precision > NO_QUANTISATION) {
            return reader.readQuantizedFloat(precision);
        }
        return finiteOrZero(reader.readFloat());
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static void writeVector2(NetWriter writer, Vector2fc value, float precision) {
        writeScalar(writer, value.x(), precision);
        writeScalar(writer, value.y(), precision);
    }

    private static void writeVector3(NetWriter writer, Vector3fc value, float precision) {
        writeScalar(writer, value.x(), precision);
        writeScalar(writer, value.y(), precision);
        writeScalar(writer, value.z(), precision);
    }

    private static void writeVector4(NetWriter writer, Vector4fc value, float precision) {
        writeScalar(writer, value.x(), precision);
        writeScalar(writer, value.y(), precision);
        writeScalar(writer, value.z(), precision);
        writeScalar(writer, value.w(), precision);
    }

    private static void writeQuaternion(NetWriter writer, Quaternionfc value, float precision) {
        if (precision <= NO_QUANTISATION) {
            writer.writeFloat(value.x()).writeFloat(value.y()).writeFloat(value.z()).writeFloat(value.w());
            return;
        }
        writer.writeInt(SmallestThree.pack(value));
    }

    public static Object read(NetReader reader, ExportedProperty.Kind kind, Class<?> declaredType) {
        return read(reader, kind, declaredType, NO_QUANTISATION);
    }

    public static Object read(NetReader reader, ExportedProperty.Kind kind, Class<?> declaredType,
                              float precision) {
        return switch (kind) {
            case FLOAT -> readScalar(reader, precision);
            case INT -> reader.readSignedVarInt();
            case BOOLEAN -> reader.readBoolean();
            case STRING -> reader.readString();
            case VECTOR2 -> new Vector2f(readScalar(reader, precision), readScalar(reader, precision));
            case VECTOR3 -> new Vector3f(readScalar(reader, precision), readScalar(reader, precision),
                    readScalar(reader, precision));
            case VECTOR4 -> new Vector4f(readScalar(reader, precision), readScalar(reader, precision),
                    readScalar(reader, precision), readScalar(reader, precision));
            case QUATERNION -> normalised(readQuaternion(reader, precision));
            case ENUM -> enumConstantAt(declaredType, reader.readVarInt());
            default -> throw new EpysiaException("The network codec cannot read values of kind " + kind);
        };
    }

    private static Quaternionf normalised(Quaternionf rotation) {
        float lengthSquared = rotation.lengthSquared();
        if (lengthSquared < 1.0e-6f || !Float.isFinite(lengthSquared)) {
            return new Quaternionf();
        }
        return rotation.normalize();
    }

    private static Quaternionf readQuaternion(NetReader reader, float precision) {
        if (precision <= NO_QUANTISATION) {
            return new Quaternionf(reader.readFloat(), reader.readFloat(),
                    reader.readFloat(), reader.readFloat());
        }
        return SmallestThree.unpack(reader.readInt());
    }

    private static Object enumConstantAt(Class<?> declaredType, int ordinal) {
        Object[] constants = declaredType.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new EpysiaException("Expected an enum type but received " + declaredType.getName());
        }
        return constants[Math.clamp(ordinal, 0, constants.length - 1)];
    }

    public static Object copy(ExportedProperty.Kind kind, Object value) {
        return switch (kind) {
            case VECTOR2 -> new Vector2f((Vector2fc) value);
            case VECTOR3 -> new Vector3f((Vector3fc) value);
            case VECTOR4 -> new Vector4f((Vector4fc) value);
            case QUATERNION -> new Quaternionf((Quaternionfc) value);
            default -> value;
        };
    }

    public static Object blend(ExportedProperty.Kind kind, Object from, Object to, float alpha) {
        return switch (kind) {
            case FLOAT -> (Float) from + ((Float) to - (Float) from) * alpha;
            case VECTOR2 -> new Vector2f((Vector2fc) from).lerp((Vector2fc) to, alpha);
            case VECTOR3 -> new Vector3f((Vector3fc) from).lerp((Vector3fc) to, alpha);
            case VECTOR4 -> new Vector4f((Vector4fc) from).lerp((Vector4fc) to, alpha);
            case QUATERNION -> new Quaternionf((Quaternionfc) from).slerp((Quaternionfc) to, alpha);
            default -> to;
        };
    }
}
