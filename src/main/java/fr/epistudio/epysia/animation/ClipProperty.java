package fr.epistudio.epysia.animation;

public enum ClipProperty {

    TRANSLATION(3),
    ROTATION(4),
    SCALE(3);

    private final int componentCount;

    ClipProperty(int componentCount) {
        this.componentCount = componentCount;
    }

    public int componentCount() {
        return componentCount;
    }
}
