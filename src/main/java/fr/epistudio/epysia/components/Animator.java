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

    private Optional<Clip> clip = Optional.empty();
    private float timeSeconds;

    @Override
    public void onLoad(EngineServices services) {
        if (clipPath.isEmpty()) {
            clip = Optional.empty();
            return;
        }
        clip = services.assets().resolve(Clip.class, clipPath);
    }

    public Optional<Clip> resolvedClip() {
        return clip;
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
        clip = Optional.empty();
        timeSeconds = 0.0f;
        return this;
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
        return timeSeconds;
    }

    public void advance(float deltaSeconds, float durationSeconds) {
        if (!playing) {
            return;
        }
        timeSeconds += speed * deltaSeconds;
        if (looping) {
            timeSeconds = wrap(timeSeconds, durationSeconds);
        } else if (timeSeconds >= durationSeconds) {
            timeSeconds = durationSeconds;
            playing = false;
        }
    }

    private static float wrap(float value, float duration) {
        float wrapped = value % duration;
        return wrapped < 0.0f ? wrapped + duration : wrapped;
    }
}
