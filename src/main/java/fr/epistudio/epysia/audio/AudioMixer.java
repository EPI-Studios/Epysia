package fr.epistudio.epysia.audio;

import java.util.EnumMap;
import java.util.Map;

public final class AudioMixer {

    private final Map<AudioBus, Float> busGains = new EnumMap<>(AudioBus.class);
    private final Map<AudioBus, AudioFade> busFades = new EnumMap<>(AudioBus.class);

    public AudioMixer() {
        for (AudioBus bus : AudioBus.values()) {
            busGains.put(bus, 1.0f);
            busFades.put(bus, new AudioFade());
        }
    }

    public void setBusGain(AudioBus bus, float gain) {
        busGains.put(bus, Math.max(0.0f, gain));
    }

    public float busGain(AudioBus bus) {
        return busGains.get(bus);
    }

    public float effectiveGain(AudioBus bus) {
        if (bus == AudioBus.MASTER) {
            return busGains.get(AudioBus.MASTER);
        }
        return busGains.get(AudioBus.MASTER) * busGains.get(bus);
    }

    public void duck(AudioBus bus, float targetGain, float seconds) {
        if (bus == AudioBus.MASTER) {
            return;
        }
        busFades.get(bus).start(busGains.get(bus), targetGain, seconds, null);
    }

    public void restore(AudioBus bus, float seconds) {
        if (bus == AudioBus.MASTER) {
            return;
        }
        busFades.get(bus).start(busGains.get(bus), 1.0f, seconds, null);
    }

    public void update(float deltaTimeSeconds) {
        for (AudioBus bus : AudioBus.values()) {
            AudioFade fade = busFades.get(bus);
            if (fade.active()) {
                busGains.put(bus, fade.advance(deltaTimeSeconds));
            }
        }
    }
}
