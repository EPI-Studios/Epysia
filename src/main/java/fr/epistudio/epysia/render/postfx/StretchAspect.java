package fr.epistudio.epysia.render.postfx;

public enum StretchAspect {
    IGNORE,
    KEEP,
    KEEP_WIDTH,
    KEEP_HEIGHT,
    EXPAND;

    public static StretchAspect fromId(String id) {
        for (StretchAspect aspect : values()) {
            if (aspect.name().equalsIgnoreCase(id)) {
                return aspect;
            }
        }
        return KEEP;
    }
}
