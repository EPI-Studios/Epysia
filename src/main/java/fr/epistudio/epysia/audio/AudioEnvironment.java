package fr.epistudio.epysia.audio;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;

@EpysiaComponent(name = "Audio Environment", category = "Audio")
public final class AudioEnvironment extends Component {

    public enum Space {
        GENERIC,
        FOREST,
        CAVE,
        SMALL_ROOM
    }

    @Export(label = "Space")
    private Space space = Space.FOREST;

    @Export(label = "Master Volume", min = 0.0f, max = 2.0f, step = 0.01f)
    private float masterVolume = 1.0f;

    @Export(label = "Effects Volume", min = 0.0f, max = 2.0f, step = 0.01f)
    private float effectsVolume = 1.0f;

    @Export(label = "Ambient Volume", min = 0.0f, max = 2.0f, step = 0.01f)
    private float ambientVolume = 1.0f;

    public Space space() {
        return space;
    }

    public AudioEnvironment setSpace(Space value) {
        space = value;
        return this;
    }

    @Override
    public void onLoad(EngineServices services) {
        services.audio().ifPresent(this::applyTo);
    }

    public void applyTo(AudioSystem audio) {
        audio.setReverbPreset(presetOf(space));
        audio.mixer().setBusGain(AudioBus.MASTER, masterVolume);
        audio.mixer().setBusGain(AudioBus.SFX, effectsVolume);
        audio.mixer().setBusGain(AudioBus.AMBIENT, ambientVolume);
    }

    private static AudioReverbPreset presetOf(Space space) {
        return switch (space) {
            case GENERIC -> AudioReverbPreset.GENERIC;
            case FOREST -> AudioReverbPreset.FOREST;
            case CAVE -> AudioReverbPreset.CAVE;
            case SMALL_ROOM -> AudioReverbPreset.SMALL_ROOM;
        };
    }
}
