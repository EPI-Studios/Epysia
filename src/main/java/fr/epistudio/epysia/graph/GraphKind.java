package fr.epistudio.epysia.graph;

public enum GraphKind {
    LOGIC,
    STATE_MACHINE,
    SHADER_SURFACE,
    SHADER_POST,
    VFX;

    public boolean isShader() {
        return this == SHADER_SURFACE || this == SHADER_POST;
    }

    public static GraphKind parse(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return LOGIC;
        }
    }
}
