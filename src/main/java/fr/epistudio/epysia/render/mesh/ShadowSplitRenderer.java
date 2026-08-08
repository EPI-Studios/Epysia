package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

final class ShadowSplitRenderer {
    private final ShadowStatistics statistics;
    private final int size;
    private final int layerCount;
    private final ShadowTargetCache sampledCache;
    private final long[] cachedStaticSignatures;
    private final long[] cachedCasterSignatures;
    private final boolean[] staticLayerReady;

    private ShadowLayerFilter layerFilter = ShadowLayerFilter.ACCEPT_ALL;
    private ShadowStaticViews views = ShadowStaticViews.FIXED;
    private RenderBackend backend;
    private TextureHandle sampledTexture;
    private RenderTargetHandle[] sampledTargets;
    private TextureHandle staticTexture;
    private RenderTargetHandle[] staticTargets;

    ShadowSplitRenderer(ShadowStatistics statistics, int size, int layerCount) {
        this.statistics = statistics;
        this.size = size;
        this.layerCount = layerCount;
        this.sampledCache = new ShadowTargetCache(layerCount);
        this.cachedStaticSignatures = new long[layerCount];
        this.cachedCasterSignatures = new long[layerCount];
        this.staticLayerReady = new boolean[layerCount];
    }

    void initialize(RenderBackend renderBackend, TextureHandle texture, RenderTargetHandle[] targets) {
        this.backend = renderBackend;
        this.sampledTexture = texture;
        this.sampledTargets = targets;
        invalidateAll();
    }

    void setLayerFilter(ShadowLayerFilter filter) {
        this.layerFilter = filter;
    }

    void setStaticViews(ShadowStaticViews staticViews) {
        this.views = staticViews;
    }

    void setCachingEnabled(boolean enabled) {
        sampledCache.setEnabled(enabled);
    }

    void invalidateAll() {
        sampledCache.invalidateAll();
        Arrays.fill(staticLayerReady, false);
    }

    long staticVideoMemoryBytes() {
        return staticTexture == null ? 0L : (long) size * size * layerCount * Float.BYTES;
    }

    void renderTarget(int target, long viewSignature, ShadowCasterSet casters, IntConsumer prepareLayer) {
        long sampledSignature = ShadowSignatures.mix(viewSignature, casters.combinedSignature());
        boolean sampledDirty = sampledCache.needsRender(target, sampledSignature, casters.dynamicAnimated());
        if (!sampledDirty) {
            statistics.recordTarget(false);
            return;
        }
        if (casters.dynamicCasters().isEmpty()) {
            renderSingleLayer(target, casters.staticCasters(), prepareLayer);
            return;
        }
        ShadowLayerTranslation translation = ensureStaticLayer(target, viewSignature, casters, prepareLayer);
        composeSampledLayer(target, casters, translation, prepareLayer);
        statistics.recordTarget(true);
        statistics.recordDynamicCasters(casters.dynamicCasters().size());
    }

    private void renderSingleLayer(int target, List<ShadowCaster> staticCasters, IntConsumer prepareLayer) {
        drawInto(sampledTargets[target], PassClear.depthOnly(), staticCasters, target, prepareLayer);
        staticLayerReady[target] = false;
        statistics.recordTarget(true);
    }

    private ShadowLayerTranslation ensureStaticLayer(int target, long viewSignature, ShadowCasterSet casters,
                                                     IntConsumer prepareLayer) {
        long casterSignature = casters.staticSignature();
        long signature = ShadowSignatures.mix(viewSignature, casterSignature);
        if (staticLayerReady[target] && cachedStaticSignatures[target] == signature) {
            return ShadowLayerTranslation.of(0, 0);
        }
        ShadowLayerTranslation translation = translationFor(target, casterSignature);
        if (translation.reusable()) {
            statistics.recordStaticLayerScroll();
            return translation;
        }
        createStaticResourcesIfAbsent();
        drawInto(staticTargets[target], PassClear.depthOnly(), casters.staticCasters(), target, prepareLayer);
        cachedStaticSignatures[target] = signature;
        cachedCasterSignatures[target] = casterSignature;
        staticLayerReady[target] = true;
        views.markBaked(target);
        statistics.recordStaticLayerRebuild();
        return ShadowLayerTranslation.of(0, 0);
    }

    private ShadowLayerTranslation translationFor(int target, long casterSignature) {
        if (!staticLayerReady[target] || cachedCasterSignatures[target] != casterSignature) {
            return ShadowLayerTranslation.rebuild();
        }
        return views.translationSinceBake(target);
    }

    private void composeSampledLayer(int target, ShadowCasterSet casters,
                                     ShadowLayerTranslation translation, IntConsumer prepareLayer) {
        backend.beginPass(sampledTargets[target], PassClear.depthOnly());
        backend.endPass();
        copyRetainedRegion(target, translation);
        statistics.recordDepthCopy();
        prepareLayer.accept(target);
        backend.beginPass(sampledTargets[target], PassClear.none());
        drawExposedRegion(target, casters.staticCasters(), translation);
        drawCasters(target, casters.dynamicCasters());
        backend.endPass();
    }

    private void copyRetainedRegion(int layer, ShadowLayerTranslation translation) {
        int offsetX = translation.texelX();
        int offsetY = translation.texelY();
        backend.copyTextureRegion(
                staticTexture, layer, Math.max(0, -offsetX), Math.max(0, -offsetY),
                sampledTexture, layer, Math.max(0, offsetX), Math.max(0, offsetY),
                size - Math.abs(offsetX), size - Math.abs(offsetY));
    }

    private void drawExposedRegion(int layer, List<ShadowCaster> staticCasters,
                                   ShadowLayerTranslation translation) {
        if (translation.unchanged()) {
            return;
        }
        for (int i = 0; i < staticCasters.size(); i++) {
            ShadowCaster caster = staticCasters.get(i);
            if (!layerFilter.visibleInLayer(layer, caster)
                    || !views.casterTouchesExposedRegion(layer, translation, caster)) {
                continue;
            }
            backend.execute(caster.command());
            statistics.recordScrolledCaster();
        }
    }

    private void drawInto(RenderTargetHandle target, PassClear clear, List<ShadowCaster> casters,
                          int layer, IntConsumer prepareLayer) {
        prepareLayer.accept(layer);
        backend.beginPass(target, clear);
        drawCasters(layer, casters);
        backend.endPass();
    }

    private void drawCasters(int layer, List<ShadowCaster> casters) {
        for (int i = 0; i < casters.size(); i++) {
            ShadowCaster caster = casters.get(i);
            if (!layerFilter.visibleInLayer(layer, caster)) {
                statistics.recordCulledCaster();
                continue;
            }
            backend.execute(caster.command());
        }
    }

    private void createStaticResourcesIfAbsent() {
        if (staticTexture != null) {
            return;
        }
        staticTexture = backend.createTexture(
                TextureDescriptor.depthArray(size, layerCount, TextureUsage.SAMPLED_DEPTH_ATTACHMENT));
        staticTargets = new RenderTargetHandle[layerCount];
        for (int layer = 0; layer < layerCount; layer++) {
            staticTargets[layer] = backend.createRenderTarget(
                    RenderTargetDescriptor.depthArrayLayer(size, staticTexture, layer));
        }
    }

    void shutdown() {
        if (backend == null || staticTexture == null) {
            return;
        }
        for (RenderTargetHandle target : staticTargets) {
            backend.destroy(target);
        }
        backend.destroy(staticTexture);
        staticTargets = null;
        staticTexture = null;
    }
}
