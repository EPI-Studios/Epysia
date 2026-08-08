package fr.epistudio.epysia.render.material;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

public enum UniformType {
    FLOAT(4, 4) {
        @Override
        public void write(ByteBuffer destination, int byteOffset, Object value) {
            destination.putFloat(byteOffset, (Float) value);
        }

        @Override
        public void read(ByteBuffer destination, int byteOffset, VarHandle accessor, Object instance) {
            destination.putFloat(byteOffset, (float) accessor.get(instance));
        }
    },
    INT(4, 4) {
        @Override
        public void write(ByteBuffer destination, int byteOffset, Object value) {
            destination.putInt(byteOffset, (Integer) value);
        }

        @Override
        public void read(ByteBuffer destination, int byteOffset, VarHandle accessor, Object instance) {
            destination.putInt(byteOffset, (int) accessor.get(instance));
        }
    },
    VECTOR2F(8, 8) {
        @Override
        public void write(ByteBuffer destination, int byteOffset, Object value) {
            Vector2f vector = (Vector2f) value;
            destination.putFloat(byteOffset, vector.x).putFloat(byteOffset + 4, vector.y);
        }
    },
    VECTOR3F(12, 16) {
        @Override
        public void write(ByteBuffer destination, int byteOffset, Object value) {
            Vector3f vector = (Vector3f) value;
            destination.putFloat(byteOffset, vector.x).putFloat(byteOffset + 4, vector.y).putFloat(byteOffset + 8, vector.z);
        }
    },
    VECTOR4F(16, 16) {
        @Override
        public void write(ByteBuffer destination, int byteOffset, Object value) {
            Vector4f vector = (Vector4f) value;
            destination.putFloat(byteOffset, vector.x).putFloat(byteOffset + 4, vector.y)
                    .putFloat(byteOffset + 8, vector.z).putFloat(byteOffset + 12, vector.w);
        }
    },
    MATRIX4F(64, 16) {
        @Override
        public void write(ByteBuffer destination, int byteOffset, Object value) {
            ((Matrix4f) value).get(byteOffset, destination);
        }
    };

    private final int byteSize;
    private final int byteAlignment;

    UniformType(int byteSize, int byteAlignment) {
        this.byteSize = byteSize;
        this.byteAlignment = byteAlignment;
    }

    public int byteSize() {
        return byteSize;
    }

    public int byteAlignment() {
        return byteAlignment;
    }

    public abstract void write(ByteBuffer destination, int byteOffset, Object value);

    public void read(ByteBuffer destination, int byteOffset, VarHandle accessor, Object instance) {
        Object value = accessor.get(instance);
        if (value != null) {
            write(destination, byteOffset, value);
        }
    }

    public static UniformType forField(Class<?> fieldType) {
        if (fieldType == float.class || fieldType == Float.class) return FLOAT;
        if (fieldType == int.class || fieldType == Integer.class) return INT;
        if (fieldType == Vector2f.class) return VECTOR2F;
        if (fieldType == Vector3f.class) return VECTOR3F;
        if (fieldType == Vector4f.class) return VECTOR4F;
        if (fieldType == Matrix4f.class) return MATRIX4F;
        throw new EpysiaException("Unsupported @Uniform field type: " + fieldType.getName());
    }
}
