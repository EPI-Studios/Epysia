package fr.epistudio.epysia.assets.procedural;

public final class ProceduralNoise {

    private static final int HASH_PRIME_X = 374761393;
    private static final int HASH_PRIME_Y = 668265263;
    private static final int HASH_PRIME_SEED = 1274126177;
    private static final int HASH_SHIFT = 13;
    private static final int HASH_MULTIPLIER = 1274126177;
    private static final float INVERSE_INT_RANGE = 1.0f / Integer.MAX_VALUE;

    private ProceduralNoise() {
    }

    public static float value(int seed, float x, float y, int period) {
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(y);
        float fractionX = x - cellX;
        float fractionY = y - cellY;
        float smoothX = smooth(fractionX);
        float smoothY = smooth(fractionY);
        float topLeft = hashed(seed, cellX, cellY, period);
        float topRight = hashed(seed, cellX + 1, cellY, period);
        float bottomLeft = hashed(seed, cellX, cellY + 1, period);
        float bottomRight = hashed(seed, cellX + 1, cellY + 1, period);
        float top = topLeft + (topRight - topLeft) * smoothX;
        float bottom = bottomLeft + (bottomRight - bottomLeft) * smoothX;
        return top + (bottom - top) * smoothY;
    }

    public static float fractal(int seed, float x, float y, int octaves, float lacunarity,
                                float gain, int period) {
        float sum = 0.0f;
        float amplitude = 1.0f;
        float total = 0.0f;
        float frequency = 1.0f;
        int wrap = Math.max(1, period);
        for (int octave = 0; octave < Math.max(1, octaves); octave++) {
            sum += value(seed + octave, x * frequency, y * frequency, wrap) * amplitude;
            total += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
            wrap = Math.max(1, Math.round(wrap * lacunarity));
        }
        return total <= 0.0f ? 0.0f : sum / total;
    }

    public static float cellular(int seed, float x, float y, int period) {
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(y);
        float nearest = Float.MAX_VALUE;
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                nearest = Math.min(nearest, distanceToFeature(seed, x, y, cellX + offsetX,
                        cellY + offsetY, period));
            }
        }
        return Math.clamp((float) Math.sqrt(nearest), 0.0f, 1.0f);
    }

    private static float distanceToFeature(int seed, float x, float y, int cellX, int cellY, int period) {
        float featureX = cellX + hashed(seed, cellX, cellY, period);
        float featureY = cellY + hashed(seed + 1, cellX, cellY, period);
        float deltaX = featureX - x;
        float deltaY = featureY - y;
        return deltaX * deltaX + deltaY * deltaY;
    }

    private static float smooth(float value) {
        return value * value * (3.0f - 2.0f * value);
    }

    private static float hashed(int seed, int x, int y, int period) {
        int wrappedX = Math.floorMod(x, Math.max(1, period));
        int wrappedY = Math.floorMod(y, Math.max(1, period));
        int hash = wrappedX * HASH_PRIME_X ^ wrappedY * HASH_PRIME_Y ^ seed * HASH_PRIME_SEED;
        hash = hash ^ hash >>> HASH_SHIFT;
        hash *= HASH_MULTIPLIER;
        hash = hash ^ hash >>> HASH_SHIFT;
        return Math.abs(hash) * INVERSE_INT_RANGE;
    }
}
