package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.protocol.ValueCodec;
import fr.epistudio.epysia.reflection.ExportedProperty;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.lang.reflect.Field;
import java.util.Optional;

public final class ReplicatedField {
    private final Field field;
    private final ExportedProperty.Kind kind;
    private final ReplicationCondition condition;
    private final boolean interpolate;
    private final int sendRateHertz;
    private final float precision;

    private ReplicatedField(Field field, ExportedProperty.Kind kind, ReplicationCondition condition,
                            boolean interpolate, int sendRateHertz, float precision) {
        this.field = field;
        this.kind = kind;
        this.condition = condition;
        this.interpolate = interpolate;
        this.sendRateHertz = sendRateHertz;
        this.precision = precision;
    }

    public static Optional<ReplicatedField> of(Field field, ReplicationCondition condition,
                                               boolean interpolate, int sendRateHertz, float precision) {
        ExportedProperty.Kind kind = ValueCodec.kindOf(field.getType());
        if (!ValueCodec.isSupported(kind)) {
            return Optional.empty();
        }
        field.setAccessible(true);
        return Optional.of(new ReplicatedField(field, kind, condition, interpolate,
                Math.max(0, sendRateHertz), Math.max(0.0f, precision)));
    }

    public static boolean isSupported(ExportedProperty.Kind kind) {
        return ValueCodec.isSupported(kind);
    }

    public Class<?> declaringType() {
        return field.getDeclaringClass();
    }

    public String fieldName() {
        return field.getName();
    }

    public ExportedProperty.Kind kind() {
        return kind;
    }

    public ReplicationCondition condition() {
        return condition;
    }

    public boolean interpolate() {
        return interpolate;
    }

    public int sendRateHertz() {
        return sendRateHertz;
    }

    public float precision() {
        return precision;
    }

    public int sendIntervalTicks(int tickRate) {
        if (sendRateHertz <= 0 || sendRateHertz >= tickRate) {
            return 1;
        }
        return Math.max(1, tickRate / sendRateHertz);
    }

    public String identity() {
        return declaringType().getName() + "#" + fieldName() + ":" + kind.name()
                + ":" + sendRateHertz + ":" + precision;
    }

    public Object read(IComponent component) {
        try {
            return ValueCodec.copy(kind, field.get(component));
        } catch (IllegalAccessException denied) {
            throw new EpysiaException("Cannot read replicated field " + identity(), denied);
        }
    }

    public void write(IComponent component, Object value) {
        try {
            applyValue(component, value);
        } catch (IllegalAccessException denied) {
            throw new EpysiaException("Cannot write replicated field " + identity(), denied);
        }
    }

    private void applyValue(IComponent component, Object value) throws IllegalAccessException {
        switch (kind) {
            case VECTOR2 -> ((Vector2f) field.get(component)).set((Vector2fc) value);
            case VECTOR3 -> ((Vector3f) field.get(component)).set((Vector3fc) value);
            case VECTOR4 -> ((Vector4f) field.get(component)).set((Vector4fc) value);
            case QUATERNION -> ((Quaternionf) field.get(component)).set((Quaternionfc) value);
            default -> field.set(component, value);
        }
    }

    public void writeValue(NetWriter writer, Object value) {
        ValueCodec.write(writer, kind, value, precision);
    }

    public Object readValue(NetReader reader) {
        return ValueCodec.read(reader, kind, field.getType(), precision);
    }

    public Object blend(Object from, Object to, float alpha) {
        if (!interpolate || from == WorldState.ABSENT || to == WorldState.ABSENT) {
            return to;
        }
        return ValueCodec.blend(kind, from, to, alpha);
    }

    public boolean valuesEqual(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.equals(right);
    }
}
