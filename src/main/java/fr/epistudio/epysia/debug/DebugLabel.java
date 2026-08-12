package fr.epistudio.epysia.debug;

public final class DebugLabel {

    private final float x;
    private final float y;
    private final float z;
    private final String content;
    private final int color;
    private float remainingSeconds;

    DebugLabel(float x, float y, float z, String content, int color, float remainingSeconds) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.content = content;
        this.color = color;
        this.remainingSeconds = remainingSeconds;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }

    public String content() {
        return content;
    }

    public int color() {
        return color;
    }

    boolean advance(float deltaSeconds) {
        remainingSeconds -= deltaSeconds;
        return remainingSeconds > 0.0f;
    }
}
