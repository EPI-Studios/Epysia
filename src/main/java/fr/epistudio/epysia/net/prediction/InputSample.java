package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;

public record InputSample(int tick, long downMask, float[] axisValues) {
    public static final int MINIMUM_ENCODED_BYTES = Integer.BYTES + Long.BYTES + 1;

    public static InputSample empty(int tick, int actionCount) {
        return new InputSample(tick, 0L, new float[actionCount]);
    }

    public boolean isDown(int actionIndex) {
        return actionIndex >= 0 && actionIndex < Long.SIZE && (downMask & (1L << actionIndex)) != 0L;
    }

    public float axis(int actionIndex) {
        if (actionIndex < 0 || actionIndex >= axisValues.length) {
            return 0.0f;
        }
        return axisValues[actionIndex];
    }

    public InputSample retimed(int newTick) {
        return new InputSample(newTick, downMask, axisValues);
    }

    public void write(NetWriter writer) {
        writer.writeInt(tick);
        writer.writeLong(downMask);
        writer.writeVarInt(axisValues.length);
        for (float value : axisValues) {
            writer.writeFloat(value);
        }
    }

    public static InputSample read(NetReader reader) {
        int tick = reader.readInt();
        long downMask = reader.readLong();
        float[] axisValues = new float[reader.requireCount(reader.readVarInt(), Float.BYTES)];
        for (int index = 0; index < axisValues.length; index++) {
            axisValues[index] = clampAxis(reader.readFloat());
        }
        return new InputSample(tick, downMask, axisValues);
    }

    private static float clampAxis(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.clamp(value, -1.0f, 1.0f);
    }
}
