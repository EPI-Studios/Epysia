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
    private final boolean[] staticLayerReady;

    private ShadowLayerFilter layerFilter = ShadowLayerFilter.ACCEPT_ALL;
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
        long staticSignature = ShadowSignatures.mix(viewSignature, casters.staticSignature());
        long sampledSignature = ShadowSignatures.mix(viewSignature, casters.combinedSignature());
        boolean sampledDirty = sampledCache.needsRender(target, sampledSignature, casters.dynamicAnimated());
        if (casters.dynamicCasters().isEmpty()) {
            renderSingleLayer(target, casters.staticCasters(), prepareLayer, sampledDirty);
            return;
        }
        ensureStaticLayer(target, staticSignature, casters.staticCasters(), prepareLayer);
        if (!sampledDirty) {
            statistics.recordTarget(false);
            return;
        }
        backend.copyTextureLayer(staticTexture, target, sampledTexture, target);
        statistics.recordDepthCopy();
        drawInto(sampledTargets[target], PassClear.none(), casters.dynamicCasters(), target, prepareLayer);
        statistics.recordTarget(true);
        statistics.recordDynamicCasters(casters.dynamicCasters().size());
    }

    private void renderSingleLayer(int target, List<ShadowCaster> staticCasters,
                                   IntConsumer prepareLayer, boolean sampledDirty) {
        if (!sampledDirty) {
            statistics.recordTarget(false);
            return;
        }
        drawInto(sampledTargets[target], PassClear.depthOnly(), staticCasters, target, prepareLayer);
        staticLayerReady[target] = false;
        statistics.recordTarget(true);
    }

    private void ensureStaticLayer(int target, long staticSignature,
                                   List<ShadowCaster> staticCasters, IntConsumer prepareLayer) {
        if (staticLayerReady[target] && cachedStaticSignatures[target] == staticSignature) {
            return;
        }
        createStaticResourcesIfAbsent();
        drawInto(staticTargets[target], PassClear.depthOnly(), staticCasters, target, prepareLayer);
        cachedStaticSignatures[target] = staticSignature;
        staticLayerReady[target] = true;
        statistics.recordStaticLayerRebuild();
    }

    private void drawInto(RenderTargetHandle target, PassClear clear, List<ShadowCaster> casters,
                          int layer, IntConsumer prepareLayer) {
        prepareLayer.accept(layer);
        backend.beginPass(target, clear);
        for (int i = 0; i < casters.size(); i++) {
            ShadowCaster caster = casters.get(i);
            if (!layerFilter.visibleInLayer(layer, caster)) {
                statistics.recordCulledCaster();
                continue;
            }
            backend.execute(caster.command());
        }
        backend.endPass();
    }

    private void createStaticResourcesIfAbsent() {
        if (staticTexture != null) {
            return;
        }
        staticTexture = backend.createTexture(
                TextureDescriptor.depthArray(size, layerCount, TextureUsage.SAMPLED_DEPTH_SHADOW));
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
