package fr.epistudio.epysia.audio;

import fr.epistudio.epysia.components.Component;

public final class AudioListenerComponent extends Component {

    private float gain = 1.0f;

    public AudioListenerComponent setGain(float gain) {
        this.gain = gain;
        return this;
    }

    public float gain() {
        return gain;
    }
}
