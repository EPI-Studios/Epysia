package fr.epistudio.epysia.render.backend;

public record PixelColor(float red, float green, float blue, float alpha) {

    public float brightest() {
        return Math.max(red, Math.max(green, blue));
    }
}
