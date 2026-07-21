package fr.epistudio.epysia.render.mesh;

@FunctionalInterface
interface ShadowLayerFilter {

    ShadowLayerFilter ACCEPT_ALL = (layer, caster) -> true;

    boolean visibleInLayer(int layer, ShadowCaster caster);
}
