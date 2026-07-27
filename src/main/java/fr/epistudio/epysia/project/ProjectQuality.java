package fr.epistudio.epysia.project;

public record ProjectQuality(float gravityX, float gravityY, float gravityZ,
                             int fixedTimestepHertz, int shadowMapSize, int cascadeCount,
                             String windowTitle, int windowWidth, int windowHeight,
                             boolean verticalSync, int maximumFrameRate,
                             boolean nearestTextureFilter, boolean depthPrepass,
                             int shadowFilterSamples, int filteredCascades) {

    public static final int MIN_FIXED_TIMESTEP_HERTZ = 10;
    public static final int MAX_FIXED_TIMESTEP_HERTZ = 480;
    public static final int MIN_SHADOW_MAP_SIZE = 256;
    public static final int MAX_SHADOW_MAP_SIZE = 8192;
    public static final int MIN_CASCADE_COUNT = 1;
    public static final int MAX_CASCADE_COUNT = 4;
    public static final int MIN_WINDOW_SIZE = 160;
    public static final int MAX_WINDOW_SIZE = 16384;
    public static final int MAX_FRAME_RATE_LIMIT = 1000;

    private static final float DEFAULT_GRAVITY_Y = -9.81f;
    private static final int DEFAULT_FIXED_TIMESTEP_HERTZ = 60;
    private static final int DEFAULT_SHADOW_MAP_SIZE = 1024;
    private static final int DEFAULT_CASCADE_COUNT = 3;
    private static final String DEFAULT_WINDOW_TITLE = "Epysia - Game";
    private static final int DEFAULT_WINDOW_WIDTH = 1280;
    private static final int DEFAULT_WINDOW_HEIGHT = 720;

    public static ProjectQuality defaults() {
        return new ProjectQuality(0.0f, DEFAULT_GRAVITY_Y, 0.0f, DEFAULT_FIXED_TIMESTEP_HERTZ,
                DEFAULT_SHADOW_MAP_SIZE, DEFAULT_CASCADE_COUNT, DEFAULT_WINDOW_TITLE,
                DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, true, 0, false, false, 4, 2);
    }

    public ProjectQuality clamped() {
        return new ProjectQuality(gravityX, gravityY, gravityZ,
                clampInt(fixedTimestepHertz, MIN_FIXED_TIMESTEP_HERTZ, MAX_FIXED_TIMESTEP_HERTZ),
                nextPowerOfTwo(clampInt(shadowMapSize, MIN_SHADOW_MAP_SIZE, MAX_SHADOW_MAP_SIZE)),
                clampInt(cascadeCount, MIN_CASCADE_COUNT, MAX_CASCADE_COUNT),
                windowTitle == null || windowTitle.isBlank() ? DEFAULT_WINDOW_TITLE : windowTitle,
                clampInt(windowWidth, MIN_WINDOW_SIZE, MAX_WINDOW_SIZE),
                clampInt(windowHeight, MIN_WINDOW_SIZE, MAX_WINDOW_SIZE),
                verticalSync, clampInt(maximumFrameRate, 0, MAX_FRAME_RATE_LIMIT),
                nearestTextureFilter, depthPrepass,
                clampInt(shadowFilterSamples, 1, 32), clampInt(filteredCascades, 0, MAX_CASCADE_COUNT));
    }

    public float fixedTimestepSeconds() {
        return 1.0f / clampInt(fixedTimestepHertz, MIN_FIXED_TIMESTEP_HERTZ, MAX_FIXED_TIMESTEP_HERTZ);
    }

    private static int clampInt(int value, int minimum, int maximum) {
        return Math.min(Math.max(value, minimum), maximum);
    }

    private static int nextPowerOfTwo(int value) {
        return Integer.highestOneBit(value) == value ? value : Integer.highestOneBit(value) << 1;
    }
}
