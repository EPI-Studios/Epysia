package fr.epistudio.epysia.render.backend;

public record ScissorRect(boolean enabled, int x, int y, int width, int height) {
    private static final ScissorRect DISABLED = new ScissorRect(false, 0, 0, 0, 0);

    public static ScissorRect disabled() {
        return DISABLED;
    }

    public static ScissorRect of(int x, int y, int width, int height) {
        return new ScissorRect(true, x, y, Math.max(0, width), Math.max(0, height));
    }
}
