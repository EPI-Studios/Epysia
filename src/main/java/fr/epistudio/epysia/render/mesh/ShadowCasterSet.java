package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.DrawCommand;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

final class ShadowCasterSet {

    private final List<ShadowCaster> submitted = new ArrayList<>(256);
    private final List<ShadowCaster> staticCasters = new ArrayList<>(256);
    private final List<ShadowCaster> dynamicCasters = new ArrayList<>(32);
    private final LongLongHashMap bakedSignatures = new LongLongHashMap(256);
    private final LongLongHashMap previousSignatures = new LongLongHashMap(256);
    private final LongHashSet submittedIdentities = new LongHashSet(256);
    private final LongPredicate submittedContains = submittedIdentities::contains;

    private long staticSignature = ShadowSignatures.seed();
    private long combinedSignature = ShadowSignatures.seed();
    private boolean anyAnimated;
    private boolean rebuildRequested = true;
    private boolean splitEnabled = true;

    void setSplitEnabled(boolean enabled) {
        if (splitEnabled != enabled) {
            splitEnabled = enabled;
            requestRebuild();
        }
    }

    void requestRebuild() {
        rebuildRequested = true;
    }

    void beginFrame() {
        submitted.clear();
    }

    void submit(DrawCommand command, long identity, long signature, boolean animated) {
        submitted.add(ShadowCaster.unbounded(command, identity, signature, animated));
    }

    void submit(DrawCommand command, long identity, long signature, boolean animated,
                Vector3f worldMin, Vector3f worldMax) {
        submitted.add(new ShadowCaster(command, identity, signature, animated,
                worldMin.x, worldMin.y, worldMin.z, worldMax.x, worldMax.y, worldMax.z));
    }

    boolean isEmpty() {
        return submitted.isEmpty();
    }

    int submittedCount() {
        return submitted.size();
    }

    List<ShadowCaster> staticCasters() {
        return staticCasters;
    }

    List<ShadowCaster> dynamicCasters() {
        return dynamicCasters;
    }

    long staticSignature() {
        return staticSignature;
    }

    long combinedSignature() {
        return combinedSignature;
    }

    boolean dynamicAnimated() {
        return anyAnimated;
    }

    void classify() {
        collectSubmittedIdentities();
        if (rebuildRequested || bakedCasterDisappeared() || settledCasterOutsideBakedLayer()) {
            rebakeStaticLayer();
        } else {
            partitionAgainstBakedLayer();
        }
        rebuildRequested = false;
        rememberSignatures();
        staticSignature = digestOf(staticCasters);
        combinedSignature = digestOf(submitted);
        anyAnimated = containsAnimated(submitted);
    }

    private void collectSubmittedIdentities() {
        submittedIdentities.clear();
        for (ShadowCaster caster : submitted) {
            submittedIdentities.add(caster.identity());
        }
    }

    private boolean bakedCasterDisappeared() {
        return !bakedSignatures.allKeysMatch(submittedContains);
    }

    private boolean settledCasterOutsideBakedLayer() {
        for (ShadowCaster caster : submitted) {
            if (cacheable(caster) && !bakedMatches(caster) && signatureSettled(caster)) {
                return true;
            }
        }
        return false;
    }

    private boolean cacheable(ShadowCaster caster) {
        return !splitEnabled || !caster.animated();
    }

    private boolean bakedMatches(ShadowCaster caster) {
        return bakedSignatures.containsEntry(caster.identity(), caster.signature());
    }

    private boolean signatureSettled(ShadowCaster caster) {
        return previousSignatures.containsEntry(caster.identity(), caster.signature());
    }

    private void rebakeStaticLayer() {
        staticCasters.clear();
        dynamicCasters.clear();
        bakedSignatures.clear();
        for (ShadowCaster caster : submitted) {
            if (!cacheable(caster)) {
                dynamicCasters.add(caster);
                continue;
            }
            staticCasters.add(caster);
            bakedSignatures.put(caster.identity(), caster.signature());
        }
    }

    private void partitionAgainstBakedLayer() {
        staticCasters.clear();
        dynamicCasters.clear();
        for (ShadowCaster caster : submitted) {
            if (cacheable(caster) && bakedMatches(caster)) {
                staticCasters.add(caster);
            } else {
                dynamicCasters.add(caster);
            }
        }
    }

    private void rememberSignatures() {
        previousSignatures.clear();
        for (ShadowCaster caster : submitted) {
            previousSignatures.put(caster.identity(), caster.signature());
        }
    }

    private static long digestOf(List<ShadowCaster> casters) {
        long hash = ShadowSignatures.seed();
        for (ShadowCaster caster : casters) {
            hash = ShadowSignatures.mix(hash, caster.signature());
        }
        return ShadowSignatures.mix(hash, casters.size());
    }

    private static boolean containsAnimated(List<ShadowCaster> casters) {
        for (ShadowCaster caster : casters) {
            if (caster.animated()) {
                return true;
            }
        }
        return false;
    }
}
