package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.animation.Clip;

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

    private Optional<Clip> activeClip = Optional.empty();
    private float activeTimeSeconds;
    private Optional<Clip> previousClip = Optional.empty();
    private float previousTimeSeconds;
    private float fadeDurationSeconds;
    private float fadeElapsedSeconds;

    @Override
    public void onLoad(EngineServices services) {
        if (clipPath.isEmpty()) {
            activeClip = Optional.empty();
            return;
        }
        activeClip = services.assets().resolve(Clip.class, clipPath);
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
        if (!playing || activeClip.isEmpty()) {
            return;
        }
        float duration = activeClip.get().durationSeconds();
        activeTimeSeconds += speed * deltaSeconds;
        if (looping) {
            activeTimeSeconds = wrap(activeTimeSeconds, duration);
        } else if (activeTimeSeconds >= duration) {
            activeTimeSeconds = duration;
            playing = false;
        }
        advanceFade(deltaSeconds);
    }

    private void advanceFade(float deltaSeconds) {
        if (previousClip.isEmpty()) {
            return;
        }
        previousTimeSeconds = wrap(previousTimeSeconds + speed * deltaSeconds, previousClip.get().durationSeconds());
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

    private static float wrap(float value, float duration) {
        float wrapped = value % duration;
        return wrapped < 0.0f ? wrapped + duration : wrapped;
    }
}
