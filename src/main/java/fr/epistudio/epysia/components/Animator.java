package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.animation.AnimationLayer;
import fr.epistudio.epysia.animation.BlendSample;
import fr.epistudio.epysia.animation.BlendSpaceShape;
import fr.epistudio.epysia.animation.BlendSpaceWeights;
import fr.epistudio.epysia.animation.Clip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@EpysiaComponent(name = "Animator", category = "Animation")
public final class Animator extends Component {
    @Export(label = "Clip")
    private String clipPath = "";
    @Export(label = "Playing")
    private boolean playing = true;
    @Export(label = "Looping")
    private boolean looping = true;
    @Export(label = "Speed", min = 0.0f, max = 8.0f, step = 0.1f)
    private float speed = 1.0f;
    @Export(label = "Layers")
    @HiddenInEditor
    private final List<AnimationLayer> layers = new ArrayList<>();
    @Export(label = "Blend Samples")
    @HiddenInEditor
    private final List<BlendSample> blendSamples = new ArrayList<>();
    @Export(label = "Blend Shape")
    private BlendSpaceShape blendShape = BlendSpaceShape.LINE;
    @Export(label = "Blend X", min = -64.0f, max = 64.0f, step = 0.01f)
    private float blendPositionX;
    @Export(label = "Blend Y", min = -64.0f, max = 64.0f, step = 0.01f)
    private float blendPositionY;

    private Optional<Clip> activeClip = Optional.empty();
    private final BlendSpaceWeights blendWeights = new BlendSpaceWeights();
    private float blendPhase;
    private float activeTimeSeconds;
    private Optional<Clip> previousClip = Optional.empty();
    private float previousTimeSeconds;
    private float fadeDurationSeconds;
    private float fadeElapsedSeconds;

    @Override
    public void onLoad(EngineServices services) {
        activeClip = clipPath.isEmpty() ? Optional.empty() : services.assets().resolve(Clip.class, clipPath);
        for (AnimationLayer layer : layers) {
            resolveLayerClip(services, layer);
        }
        for (BlendSample sample : blendSamples) {
            resolveSampleClip(services, sample);
        }
    }

    private static void resolveSampleClip(EngineServices services, BlendSample sample) {
        if (sample.clipPath().isEmpty()) {
            return;
        }
        services.assets().resolve(Clip.class, sample.clipPath())
                .ifPresent(clip -> sample.assignClip(sample.clipPath(), clip));
    }

    public List<BlendSample> blendSamples() {
        return Collections.unmodifiableList(blendSamples);
    }

    public BlendSample addBlendSample() {
        BlendSample sample = new BlendSample();
        blendSamples.add(sample);
        return sample;
    }

    public Animator removeBlendSample(int sampleIndex) {
        if (sampleIndex >= 0 && sampleIndex < blendSamples.size()) {
            blendSamples.remove(sampleIndex);
        }
        return this;
    }

    public boolean blendSpaceActive() {
        return !blendSamples.isEmpty();
    }

    public BlendSpaceShape blendShape() {
        return blendShape;
    }

    public Animator setBlendShape(BlendSpaceShape shape) {
        blendShape = shape;
        return this;
    }

    public float blendPositionX() {
        return blendPositionX;
    }

    public Animator setBlendPositionX(float value) {
        blendPositionX = value;
        return this;
    }

    public float blendPositionY() {
        return blendPositionY;
    }

    public Animator setBlendPositionY(float value) {
        blendPositionY = value;
        return this;
    }

    public float blendPhase() {
        return blendPhase;
    }

    public float[] currentBlendWeights() {
        return blendWeights.compute(blendSamples, blendShape, blendPositionX, blendPositionY);
    }

    private void advanceBlendPhase(float deltaSeconds) {
        float duration = BlendSpaceWeights.weightedDuration(blendSamples, currentBlendWeights());
        if (duration <= 0.0f) {
            return;
        }
        blendPhase += speed * deltaSeconds / duration;
        blendPhase -= (float) Math.floor(blendPhase);
    }

    private static void resolveLayerClip(EngineServices services, AnimationLayer layer) {
        if (layer.clipPath().isEmpty()) {
            return;
        }
        services.assets().resolve(Clip.class, layer.clipPath())
                .ifPresent(clip -> layer.assignClip(layer.clipPath(), clip));
    }

    public List<AnimationLayer> layers() {
        return Collections.unmodifiableList(layers);
    }

    public AnimationLayer addLayer() {
        AnimationLayer layer = new AnimationLayer();
        layers.add(layer);
        return layer;
    }

    public Animator removeLayer(int layerIndex) {
        if (layerIndex >= 0 && layerIndex < layers.size()) {
            layers.remove(layerIndex);
        }
        return this;
    }

    public Optional<Clip> resolvedClip() {
        return activeClip;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void play() {
        playing = true;
    }

    public void pause() {
        playing = false;
    }

    public Animator setClipPath(String path) {
        clipPath = path;
        activeClip = Optional.empty();
        activeTimeSeconds = 0.0f;
        clearFade();
        return this;
    }

    public String clipPath() {
        return clipPath;
    }

    public Animator assignClip(String path, Clip resolvedClip) {
        clipPath = path;
        activeClip = Optional.of(resolvedClip);
        activeTimeSeconds = 0.0f;
        clearFade();
        return this;
    }

    public Animator crossFadeTo(String path, Clip resolvedClip, float fadeSeconds) {
        if (activeClip.isEmpty() || fadeSeconds <= 0.0f) {
            return assignClip(path, resolvedClip);
        }
        previousClip = activeClip;
        previousTimeSeconds = activeTimeSeconds;
        activeClip = Optional.of(resolvedClip);
        activeTimeSeconds = 0.0f;
        clipPath = path;
        fadeDurationSeconds = fadeSeconds;
        fadeElapsedSeconds = 0.0f;
        playing = true;
        return this;
    }

    public Animator crossFadeTo(EngineServices services, String path, float fadeSeconds) {
        Optional<Clip> resolved = services.assets().resolve(Clip.class, path);
        return resolved.map(clip -> crossFadeTo(path, clip, fadeSeconds)).orElseGet(() -> setClipPath(path));
    }

    public Animator setLooping(boolean value) {
        looping = value;
        return this;
    }

    public Animator setSpeed(float value) {
        speed = value;
        return this;
    }

    public float currentTimeSeconds() {
        return activeTimeSeconds;
    }

    public Optional<Clip> previousClip() {
        return previousClip;
    }

    public float previousTimeSeconds() {
        return previousTimeSeconds;
    }

    public boolean isFading() {
        return previousClip.isPresent() && fadeDurationSeconds > 0.0f && fadeElapsedSeconds < fadeDurationSeconds;
    }

    public float fadeAlpha() {
        if (fadeDurationSeconds <= 0.0f) {
            return 1.0f;
        }
        return Math.min(1.0f, fadeElapsedSeconds / fadeDurationSeconds);
    }

    public void advance(float deltaSeconds) {
        for (AnimationLayer layer : layers) {
            layer.advance(deltaSeconds);
        }
        if (playing && blendSpaceActive()) {
            advanceBlendPhase(deltaSeconds);
        }
        if (!playing || activeClip.isEmpty()) {
            return;
        }
        Clip clip = activeClip.get();
        activeTimeSeconds += speed * deltaSeconds;
        if (looping) {
            activeTimeSeconds = clip.wrapTime(activeTimeSeconds);
        } else if (activeTimeSeconds >= clip.durationSeconds()) {
            activeTimeSeconds = clip.durationSeconds();
            playing = false;
        }
        advanceFade(deltaSeconds);
    }

    private void advanceFade(float deltaSeconds) {
        if (previousClip.isEmpty()) {
            return;
        }
        previousTimeSeconds = previousClip.get().wrapTime(previousTimeSeconds + speed * deltaSeconds);
        fadeElapsedSeconds += deltaSeconds;
        if (fadeElapsedSeconds >= fadeDurationSeconds) {
            clearFade();
        }
    }

    private void clearFade() {
        previousClip = Optional.empty();
        previousTimeSeconds = 0.0f;
        fadeDurationSeconds = 0.0f;
        fadeElapsedSeconds = 0.0f;
    }
}
