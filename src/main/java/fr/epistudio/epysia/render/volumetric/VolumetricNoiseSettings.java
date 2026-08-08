package fr.epistudio.epysia.render.volumetric;

public record VolumetricNoiseSettings(int seed, int octaves, int cellSize, int axisCellCount,
                                      float amplitude, float warp, float bias, boolean inverted) {
    public static final int RESOLUTION = 128;
    public static final int LOCAL_SIZE = 8;

    public static VolumetricNoiseSettings defaults() {
        return new VolumetricNoiseSettings(0, 1, 16, 4, 1.0f, 0.0f, 0.0f, false);
    }

    public int groupCount() {
        return RESOLUTION / LOCAL_SIZE;
    }
}
