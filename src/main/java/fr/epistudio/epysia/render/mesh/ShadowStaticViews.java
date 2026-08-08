package fr.epistudio.epysia.render.mesh;

interface ShadowStaticViews {
    ShadowStaticViews FIXED = layer -> ShadowLayerTranslation.rebuild();

    ShadowLayerTranslation translationSinceBake(int layer);

    default void markBaked(int layer) {
    }

    default boolean casterTouchesExposedRegion(int layer, ShadowLayerTranslation translation, ShadowCaster caster) {
        return true;
    }
}
