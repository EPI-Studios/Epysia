package fr.epistudio.epysia.audio;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;

@EpysiaComponent(name = "Audio Source", category = "Audio",
        description = "Plays a sound from this object's position.")
public final class AudioSourceComponent extends Component {

    private AudioBuffer buffer;
    @Export(label = "Gain", min = 0.0f, max = 4.0f, step = 0.01f)
    private float gain = 1.0f;
    @Export(label = "Pitch", min = 0.1f, max = 4.0f, step = 0.01f)
    private float pitch = 1.0f;
    @Export(label = "Looping")
    private boolean looping;
    @Export(label = "Spatial")
    private boolean spatial = true;
    @Export(label = "Play On Start")
    private boolean playOnStart;
    @Export(label = "Bus")
    private AudioBus bus = AudioBus.AMBIENT;
    @Export(label = "Reference Distance", min = 0.01f, max = 1000.0f, step = 0.1f)
    private float referenceDistance = 1.0f;
    @Export(label = "Max Distance", min = 0.01f, max = 10000.0f, step = 0.5f)
    private float maxDistance = 25.0f;
    @Export(label = "Rolloff", min = 0.0f, max = 10.0f, step = 0.05f)
    private float rolloffFactor = 1.0f;
    private AudioSource source;
    private boolean initialized;

    public AudioSourceComponent setBuffer(AudioBuffer buffer) {
        this.buffer = buffer;
        return this;
    }

    public AudioSourceComponent setGain(float gain) {
        this.gain = gain;
        return this;
    }

    public AudioSourceComponent setPitch(float pitch) {
        this.pitch = pitch;
        return this;
    }

    public AudioSourceComponent setLooping(boolean looping) {
        this.looping = looping;
        return this;
    }

    public AudioSourceComponent setSpatial(boolean spatial) {
        this.spatial = spatial;
        return this;
    }

    public AudioSourceComponent setPlayOnStart(boolean playOnStart) {
        this.playOnStart = playOnStart;
        return this;
    }

    public AudioSourceComponent setBus(AudioBus bus) {
        this.bus = bus;
        return this;
    }

    public AudioBus bus() {
        return bus;
    }

    public float gain() {
        return gain;
    }

    public AudioSourceComponent setDistanceFalloff(float referenceDistance, float maxDistance, float rolloffFactor) {
        this.referenceDistance = referenceDistance;
        this.maxDistance = maxDistance;
        this.rolloffFactor = rolloffFactor;
        return this;
    }

    public AudioBuffer buffer() {
        return buffer;
    }

    public boolean spatial() {
        return spatial;
    }

    public AudioSource source() {
        return source;
    }

    public boolean playOnStart() {
        return playOnStart;
    }

    public void prepare() {
        if (initialized || buffer == null) {
            return;
        }
        source = new AudioSource()
                .setBuffer(buffer)
                .setGain(gain)
                .setPitch(pitch)
                .setLooping(looping)
                .setSpatial(spatial)
                .setReferenceDistance(referenceDistance)
                .setMaxDistance(maxDistance)
                .setRolloffFactor(rolloffFactor);
        initialized = true;
    }

    public void play() {
        if (source != null) {
            source.play();
        }
    }

    public void destroy() {
        if (source != null) {
            source.destroy();
            source = null;
        }
        initialized = false;
    }
}
