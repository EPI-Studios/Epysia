package fr.epistudio.epysia.audio;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.openal.AL10;

public enum AudioFormat {
    MONO_16(1, AL10.AL_FORMAT_MONO16),
    STEREO_16(2, AL10.AL_FORMAT_STEREO16);

    private final int channelCount;
    private final int openAlFormat;

    AudioFormat(int channelCount, int openAlFormat) {
        this.channelCount = channelCount;
        this.openAlFormat = openAlFormat;
    }

    public int channelCount() {
        return channelCount;
    }

    public int openAlFormat() {
        return openAlFormat;
    }

    public static AudioFormat forChannelCount(int channels) {
        if (channels == 1) {
            return MONO_16;
        }
        if (channels == 2) {
            return STEREO_16;
        }
        throw new EpysiaException("Unsupported audio channel count: " + channels);
    }
}
