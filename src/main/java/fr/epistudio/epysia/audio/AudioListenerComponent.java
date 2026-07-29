package fr.epistudio.epysia.audio;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;

@EpysiaComponent(name = "Audio Listener", category = "Audio")
@RequiresComponent(Transform3D.class)
public final class AudioListenerComponent extends Component {

    @Export(label = "Gain", min = 0.0f, max = 4.0f, step = 0.01f)
    private float gain = 1.0f;

    public AudioListenerComponent setGain(float gain) {
        this.gain = gain;
        return this;
    }

    public float gain() {
        return gain;
    }
}
