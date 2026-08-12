package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;

public record InputSample(int tick, long downMask, long pressedMask, float yaw, float pitch,
                          float[] axisValues) {
    public static final int MINIMUM_ENCODED_BYTES = Integer.BYTES + 2 * Long.BYTES + 2 * Float.BYTES + 1;

    private static final float HALF_TURN = (float) Math.PI;
    private static final float FULL_TURN = (float) (Math.PI * 2.0);
    private static final float MAXIMUM_PITCH = (float) (Math.PI * 0.5);

    public InputSample(int tick, long downMask, float[] axisValues) {
        this(tick, downMask, 0L, 0.0f, 0.0f, axisValues);
    }

    public static InputSample empty(int tick, int actionCount) {
        return new InputSample(tick, 0L, new float[actionCount]);
    }

    public boolean isDown(int actionIndex) {
        return isSet(downMask, actionIndex);
    }

    public boolean wasPressed(int actionIndex) {
        return isSet(pressedMask, actionIndex);
    }

    private static boolean isSet(long mask, int actionIndex) {
        return actionIndex >= 0 && actionIndex < Long.SIZE && (mask & (1L << actionIndex)) != 0L;
    }

    public float axis(int actionIndex) {
        if (actionIndex < 0 || actionIndex >= axisValues.length) {
            return 0.0f;
        }
        return axisValues[actionIndex];
    }

    public InputSample retimed(int newTick) {
        return new InputSample(newTick, downMask, 0L, yaw, pitch, axisValues);
    }

    public InputSample looking(float newYaw, float newPitch) {
        return new InputSample(tick, downMask, pressedMask, wrapYaw(newYaw), clampPitch(newPitch), axisValues);
    }

    public void write(NetWriter writer) {
        writer.writeInt(tick);
        writer.writeLong(downMask);
        writer.writeLong(pressedMask);
        writer.writeFloat(yaw);
        writer.writeFloat(pitch);
        writer.writeVarInt(axisValues.length);
        for (float value : axisValues) {
            writer.writeFloat(value);
        }
    }

    public static InputSample read(NetReader reader) {
        int tick = reader.readInt();
        long downMask = reader.readLong();
        long pressedMask = reader.readLong() & downMask;
        float yaw = wrapYaw(reader.readFloat());
        float pitch = clampPitch(reader.readFloat());
        float[] axisValues = new float[reader.requireCount(reader.readVarInt(), Float.BYTES)];
        for (int index = 0; index < axisValues.length; index++) {
            axisValues[index] = clampAxis(reader.readFloat());
        }
        return new InputSample(tick, downMask, pressedMask, yaw, pitch, axisValues);
    }

    private static float clampAxis(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.clamp(value, -1.0f, 1.0f);
    }

    private static float wrapYaw(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        float wrapped = (value + HALF_TURN) % FULL_TURN;
        return (wrapped < 0.0f ? wrapped + FULL_TURN : wrapped) - HALF_TURN;
    }

    private static float clampPitch(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.clamp(value, -MAXIMUM_PITCH, MAXIMUM_PITCH);
    }
}
