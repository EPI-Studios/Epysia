package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.components.Export;

import java.util.Optional;

public final class AnimationLayer {

    @Export(label = "Clip", assetExtensions = {".epyclip"})
    private String clipPath = "";
    @Export(label = "Blend Mode")
    private AnimationBlendMode blendMode = AnimationBlendMode.ADDITIVE;
    @Export(label = "Weight", min = 0.0f, max = 1.0f, step = 0.01f)
    private float weight = 1.0f;
    @Export(label = "Mask Root Joint")
    private String maskRootJoint = "";
    @Export(label = "Playing")
    private boolean playing = true;
    @Export(label = "Looping")
    private boolean looping = true;
    @Export(label = "Speed", min = 0.0f, max = 8.0f, step = 0.1f)
    private float speed = 1.0f;

    private Optional<Clip> clip = Optional.empty();
    private float timeSeconds;
    private JointMask mask = JointMask.full();
    private String maskSource = "";
    private long maskSkeletonChecksum;

    public String clipPath() {
        return clipPath;
    }

    public AnimationLayer setClipPath(String path) {
        clipPath = path == null ? "" : path;
        clip = Optional.empty();
        timeSeconds = 0.0f;
        return this;
    }

    public AnimationLayer assignClip(String path, Clip resolvedClip) {
        clipPath = path;
        clip = Optional.of(resolvedClip);
        timeSeconds = 0.0f;
        return this;
    }

    public Optional<Clip> resolvedClip() {
        return clip;
    }

    public AnimationBlendMode blendMode() {
        return blendMode;
    }

    public AnimationLayer setBlendMode(AnimationBlendMode mode) {
        blendMode = mode;
        return this;
    }

    public float weight() {
        return weight;
    }

    public AnimationLayer setWeight(float value) {
        weight = Math.clamp(value, 0.0f, 1.0f);
        return this;
    }

    public String maskRootJoint() {
        return maskRootJoint;
    }

    public AnimationLayer setMaskRootJoint(String jointName) {
        maskRootJoint = jointName == null ? "" : jointName;
        return this;
    }

    public boolean isPlaying() {
        return playing;
    }

    public AnimationLayer setPlaying(boolean value) {
        playing = value;
        return this;
    }

    public AnimationLayer setLooping(boolean value) {
        looping = value;
        return this;
    }

    public AnimationLayer setSpeed(float value) {
        speed = value;
        return this;
    }

    public float currentTimeSeconds() {
        return timeSeconds;
    }

    public boolean contributes() {
        return playing && weight > 0.0f && clip.isPresent();
    }

    public JointMask maskFor(Skeleton skeleton) {
        if (maskSkeletonChecksum == skeleton.nameChecksum() && maskSource.equals(maskRootJoint)) {
            return mask;
        }
        maskSkeletonChecksum = skeleton.nameChecksum();
        maskSource = maskRootJoint;
        mask = maskRootJoint.isEmpty() ? JointMask.full() : JointMask.subtree(skeleton, maskRootJoint);
        return mask;
    }

    public boolean maskRootIsMissing(Skeleton skeleton) {
        return !maskRootJoint.isEmpty() && skeleton.indexOfJoint(maskRootJoint) < 0;
    }

    public void advance(float deltaSeconds) {
        if (!playing || clip.isEmpty()) {
            return;
        }
        Clip active = clip.get();
        timeSeconds += speed * deltaSeconds;
        if (looping) {
            timeSeconds = active.wrapTime(timeSeconds);
        } else if (timeSeconds >= active.durationSeconds()) {
            timeSeconds = active.durationSeconds();
            playing = false;
        }
    }
}
