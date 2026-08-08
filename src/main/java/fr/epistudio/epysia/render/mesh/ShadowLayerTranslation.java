package fr.epistudio.epysia.render.mesh;

record ShadowLayerTranslation(boolean reusable, int texelX, int texelY) {
    private static final ShadowLayerTranslation REBUILD = new ShadowLayerTranslation(false, 0, 0);

    static ShadowLayerTranslation rebuild() {
        return REBUILD;
    }

    static ShadowLayerTranslation of(int texelX, int texelY) {
        return new ShadowLayerTranslation(true, texelX, texelY);
    }

    boolean unchanged() {
        return reusable && texelX == 0 && texelY == 0;
    }
}
