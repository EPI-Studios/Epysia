package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.animation.BlendSpaceSampler;
import fr.epistudio.epysia.animation.ClipSampler;
import fr.epistudio.epysia.animation.PoseLayerBlend;
import org.joml.Vector3f;

final class PoseSamplingContext {
    private final ClipSampler clipSampler = new ClipSampler();
    private final BlendSpaceSampler blendSpaceSampler = new BlendSpaceSampler(clipSampler);
    private final PoseLayerBlend poseLayerBlend = new PoseLayerBlend();
    private final Vector3f scratchCorner = new Vector3f();

    static PoseSamplingContext[] create(int count) {
        PoseSamplingContext[] contexts = new PoseSamplingContext[Math.max(1, count)];
        for (int index = 0; index < contexts.length; index++) {
            contexts[index] = new PoseSamplingContext();
        }
        return contexts;
    }

    ClipSampler clipSampler() {
        return clipSampler;
    }

    BlendSpaceSampler blendSpaceSampler() {
        return blendSpaceSampler;
    }

    PoseLayerBlend poseLayerBlend() {
        return poseLayerBlend;
    }

    Vector3f scratchCorner() {
        return scratchCorner;
    }
}
