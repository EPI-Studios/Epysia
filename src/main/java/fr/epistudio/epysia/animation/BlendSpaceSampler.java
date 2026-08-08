package fr.epistudio.epysia.animation;

import java.util.List;

public final class BlendSpaceSampler {
    private static final float MINIMUM_WEIGHT = 1.0e-3f;

    private final ClipSampler clipSampler;

    public BlendSpaceSampler(ClipSampler clipSampler) {
        this.clipSampler = clipSampler;
    }

    public boolean sample(List<BlendSample> samples, float[] weights, BindPose bindPose, float phase,
                          SkeletonPose out, SkeletonPose scratch) {
        float accumulated = 0.0f;
        for (int index = 0; index < samples.size(); index++) {
            accumulated = accumulateSample(samples.get(index), weights[index], bindPose, phase,
                    out, scratch, accumulated);
        }
        return accumulated > 0.0f;
    }

    private float accumulateSample(BlendSample sample, float weight, BindPose bindPose, float phase,
                                   SkeletonPose out, SkeletonPose scratch, float accumulated) {
        Clip clip = sample.resolvedClip().orElse(null);
        if (clip == null || weight < MINIMUM_WEIGHT
                || clip.skeletonChecksum() != bindPose.skeleton().nameChecksum()) {
            return accumulated;
        }
        if (accumulated <= 0.0f) {
            clipSampler.sample(clip, bindPose, phase * clip.durationSeconds(), out);
            return weight;
        }
        clipSampler.sample(clip, bindPose, phase * clip.durationSeconds(), scratch);
        out.blendFrom(scratch, accumulated / (accumulated + weight));
        return accumulated + weight;
    }
}
