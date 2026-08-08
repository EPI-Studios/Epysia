package fr.epistudio.epysia.render.volumetric;

public enum VolumetricResolution {
    FULL(1),
    HALF(2),
    QUARTER(4);

    private final int divisor;

    VolumetricResolution(int divisor) {
        this.divisor = divisor;
    }

    public int divisor() {
        return divisor;
    }

    public int scale(int pixels) {
        return Math.max(1, (pixels + divisor - 1) / divisor);
    }
}
