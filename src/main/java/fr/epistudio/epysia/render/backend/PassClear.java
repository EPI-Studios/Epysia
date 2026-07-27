package fr.epistudio.epysia.render.backend;

public record PassClear(
        boolean clearColor,
        boolean clearDepth,
        float red,
        float green,
        float blue,
        float alpha,
        float depth
) {

    public static PassClear color(float red, float green, float blue) {
        return new PassClear(true, true, red, green, blue, 1.0f, 1.0f);
    }

    public static PassClear transparent() {
        return new PassClear(true, false, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    public static PassClear depthOnly() {
        return new PassClear(false, true, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    public static PassClear none() {
        return new PassClear(false, false, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    }
}
