package fr.epistudio.epysia.audio;

import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.EXTEfx;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AudioSystem implements GameSystem {

    private static final Vector3f LOCAL_FORWARD = new Vector3f(0.0f, 0.0f, 1.0f);
    private static final Vector3f LOCAL_UP = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final int POOL_CAPACITY = 48;

    private final Logger logger;
    private long device;
    private long context;
    private boolean efxAvailable;
    private final AudioMixer mixer = new AudioMixer();
    private AudioSourcePool pool;
    private AudioReverbZone reverbZone;
    private final List<AudioPlayback> playbacks = new ArrayList<>();
    private final List<AudioStream> streams = new ArrayList<>();
    private final float[] listenerOrientation = new float[6];
    private final Vector3f scratchForward = new Vector3f();
    private final Vector3f scratchUp = new Vector3f();
    private final Vector3f scratchPosition = new Vector3f();
    private final Set<AudioSourceComponent> startedComponents = new HashSet<>();

    public AudioSystem(Logger logger) {
        this.logger = logger;
    }

    public AudioMixer mixer() {
        return mixer;
    }

    public boolean efxAvailable() {
        return efxAvailable;
    }

    @Override
    public void initialize(fr.epistudio.epysia.EngineServices services) {
        openDevice();
        AL10.alDistanceModel(AL10.AL_INVERSE_DISTANCE_CLAMPED);
        pool = new AudioSourcePool(POOL_CAPACITY);
        if (efxAvailable) {
            reverbZone = new AudioReverbZone();
        }
        logger.info("Audio device initialized: " + ALC10.alcGetString(device, ALC10.ALC_DEVICE_SPECIFIER));
        logger.info("Audio EFX " + (efxAvailable ? "enabled" : "unavailable"));
    }

    private void openDevice() {
        device = ALC10.alcOpenDevice((java.nio.ByteBuffer) null);
        if (device == 0L) {
            throw new EpysiaException("Failed to open default OpenAL device.");
        }
        ALCCapabilities alcCapabilities = ALC.createCapabilities(device);
        efxAvailable = alcCapabilities.ALC_EXT_EFX;
        context = ALC10.alcCreateContext(device, (IntBuffer) null);
        if (context == 0L) {
            ALC10.alcCloseDevice(device);
            throw new EpysiaException("Failed to create OpenAL context.");
        }
        ALC10.alcMakeContextCurrent(context);
        AL.createCapabilities(alcCapabilities);
    }

    public void setReverbPreset(AudioReverbPreset preset) {
        if (reverbZone == null) {
            return;
        }
        reverbZone.applyPreset(preset);
    }

    public Optional<AudioPlayback> playOneShot(OneShotRequest request) {
        if (request.buffer() == null) {
            return Optional.empty();
        }
        AudioSource source = pool.acquire();
        if (source == null) {
            return Optional.empty();
        }
        configureSource(source, request);
        AudioPlayback playback = new AudioPlayback(source, request.bus(), request.gain(), request.spatial());
        playback.setOnFinished(request.onFinished());
        if (request.fadeInSeconds() > 0.0f) {
            playback.fadeTo(1.0f, request.fadeInSeconds());
            playback.fade().start(0.0f, 1.0f, request.fadeInSeconds(), null);
        }
        applyGain(playback, playback.fade().active() ? 0.0f : 1.0f);
        source.play();
        playbacks.add(playback);
        return Optional.of(playback);
    }

    private void configureSource(AudioSource source, OneShotRequest request) {
        source.setBuffer(request.buffer());
        source.setPitch(request.pitch());
        source.setSpatial(request.spatial());
        if (request.spatial()) {
            source.setPosition(request.positionX(), request.positionY(), request.positionZ());
            source.setReferenceDistance(request.referenceDistance());
            source.setMaxDistance(request.maxDistance());
            source.setRolloffFactor(request.rolloffFactor());
        }
        if (request.useReverb() && reverbZone != null) {
            source.setAuxiliaryEffectSlot(reverbZone.slotId());
        }
    }

    public Optional<AudioStream> playStream(StreamingAudioSource streaming, AudioBus bus, float gain, boolean looping, float fadeInSeconds) {
        AudioSource source = pool.acquire();
        if (source == null) {
            streaming.destroy();
            return Optional.empty();
        }
        source.setSpatial(false);
        if (reverbZone != null) {
            source.setAuxiliaryEffectSlot(reverbZone.slotId());
        }
        streaming.setLooping(looping);
        if (!streaming.primeBuffers(source)) {
            pool.release(source);
            streaming.destroy();
            return Optional.empty();
        }
        AudioStream stream = new AudioStream(source, streaming, bus, gain);
        if (fadeInSeconds > 0.0f) {
            stream.fade().start(0.0f, 1.0f, fadeInSeconds, null);
        }
        applyStreamGain(stream, stream.fade().active() ? 0.0f : 1.0f);
        source.play();
        streams.add(stream);
        return Optional.of(stream);
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        mixer.update(deltaTimeSeconds);
        updateListener(scene);
        updateComponentSources(scene);
        updatePlaybacks(deltaTimeSeconds);
        updateStreams(deltaTimeSeconds);
    }

    private void updateListener(Scene scene) {
        AudioListenerComponent listener = findListener(scene);
        float listenerGain = listener == null ? 1.0f : listener.gain();
        AL10.alListenerf(AL10.AL_GAIN, mixer.busGain(AudioBus.MASTER) * listenerGain);
        if (listener == null) {
            return;
        }
        Transform3D transform = listener.owner()
                .flatMap(owner -> owner.getComponent(Transform3D.class))
                .orElse(null);
        if (transform == null) {
            return;
        }
        Vector3f position = transform.position();
        AL10.alListener3f(AL10.AL_POSITION, position.x, position.y, position.z);
        writeListenerOrientation(transform);
    }

    private void writeListenerOrientation(Transform3D transform) {
        transform.rotation().transform(LOCAL_FORWARD, scratchForward);
        transform.rotation().transform(LOCAL_UP, scratchUp);
        listenerOrientation[0] = scratchForward.x;
        listenerOrientation[1] = scratchForward.y;
        listenerOrientation[2] = scratchForward.z;
        listenerOrientation[3] = scratchUp.x;
        listenerOrientation[4] = scratchUp.y;
        listenerOrientation[5] = scratchUp.z;
        AL10.alListenerfv(AL10.AL_ORIENTATION, listenerOrientation);
    }

    private AudioListenerComponent findListener(Scene scene) {
        for (AudioListenerComponent listener : scene.componentsOf(AudioListenerComponent.class)) {
            return listener;
        }
        return null;
    }

    private void updateComponentSources(Scene scene) {
        for (AudioSourceComponent component : scene.componentsOf(AudioSourceComponent.class)) {
            GameObject owner = component.ownerOrNull();
            updateComponent(component, owner == null ? null : owner.getComponentOrNull(Transform3D.class));
        }
    }

    private void updateComponent(AudioSourceComponent component, Transform3D transform) {
        component.prepare();
        AudioSource source = component.source();
        if (source == null) {
            return;
        }
        if (component.spatial() && transform != null) {
            scratchPosition.set(transform.position());
            source.setPosition(scratchPosition.x, scratchPosition.y, scratchPosition.z);
        }
        float busGain = mixer.effectiveGain(component.bus());
        source.setGain(component.gain() * busGain);
        if (component.playOnStart() && startedComponents.add(component)) {
            if (reverbZone != null) {
                source.setAuxiliaryEffectSlot(reverbZone.slotId());
            }
            source.play();
        }
    }

    private void updatePlaybacks(float deltaTimeSeconds) {
        Iterator<AudioPlayback> iterator = playbacks.iterator();
        while (iterator.hasNext()) {
            AudioPlayback playback = iterator.next();
            float fadeGain = advanceFade(playback.fade(), deltaTimeSeconds);
            applyGain(playback, fadeGain);
            if (shouldRetirePlayback(playback, fadeGain)) {
                retirePlayback(playback);
                iterator.remove();
            }
        }
    }

    private boolean shouldRetirePlayback(AudioPlayback playback, float fadeGain) {
        if (playback.pendingStop() && !playback.fade().active() && fadeGain <= 0.0001f) {
            return true;
        }
        return !playback.source().isPlaying();
    }

    private void retirePlayback(AudioPlayback playback) {
        AudioSource source = playback.source();
        pool.release(source);
        if (playback.onFinished() != null) {
            playback.onFinished().run();
        }
        playback.markReleased();
    }

    private void applyGain(AudioPlayback playback, float fadeGain) {
        if (!playback.isAlive()) {
            return;
        }
        float busGain = mixer.effectiveGain(playback.bus());
        playback.source().setGain(playback.baseGain() * busGain * fadeGain);
    }

    private void updateStreams(float deltaTimeSeconds) {
        Iterator<AudioStream> iterator = streams.iterator();
        while (iterator.hasNext()) {
            AudioStream stream = iterator.next();
            stream.streaming().pump(stream.source());
            float fadeGain = advanceFade(stream.fade(), deltaTimeSeconds);
            applyStreamGain(stream, fadeGain);
            if (shouldRetireStream(stream, fadeGain)) {
                retireStream(stream);
                iterator.remove();
            }
        }
    }

    private boolean shouldRetireStream(AudioStream stream, float fadeGain) {
        if (stream.pendingStop() && !stream.fade().active() && fadeGain <= 0.0001f) {
            return true;
        }
        return stream.streaming().finished() && !stream.source().isPlaying();
    }

    private void retireStream(AudioStream stream) {
        stream.source().stop();
        pool.release(stream.source());
        stream.streaming().destroy();
        if (stream.onFinished() != null) {
            stream.onFinished().run();
        }
        stream.markReleased();
    }

    private void applyStreamGain(AudioStream stream, float fadeGain) {
        if (!stream.isAlive()) {
            return;
        }
        float busGain = mixer.effectiveGain(stream.bus());
        stream.source().setGain(stream.baseGain() * busGain * fadeGain);
    }

    private float advanceFade(AudioFade fade, float deltaTimeSeconds) {
        if (!fade.active()) {
            return fade.targetGain();
        }
        return fade.advance(deltaTimeSeconds);
    }

    public void resetForPlaySession() {
        for (AudioPlayback playback : playbacks) {
            playback.stop();
        }
        playbacks.clear();
        for (AudioStream stream : streams) {
            stream.streaming().destroy();
        }
        streams.clear();
        startedComponents.clear();
    }

    @Override
    public void shutdown() {
        for (AudioStream stream : streams) {
            stream.streaming().destroy();
        }
        streams.clear();
        playbacks.clear();
        if (pool != null) {
            pool.destroy();
        }
        if (reverbZone != null) {
            reverbZone.destroy();
        }
        if (context != 0L) {
            ALC10.alcMakeContextCurrent(0L);
            ALC10.alcDestroyContext(context);
            context = 0L;
        }
        if (device != 0L) {
            ALC10.alcCloseDevice(device);
            device = 0L;
        }
    }
}
