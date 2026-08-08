package fr.epistudio.epysia.net.voice.dsp;

import de.maxhenkel.speex4j.AutomaticGainControl;

import java.util.Optional;

final class SpeexGainControl implements GainControl {
    private final AutomaticGainControl speex;
    private boolean closed;

    private SpeexGainControl(AutomaticGainControl speex) {
        this.speex = speex;
    }

    static Optional<GainControl> tryCreate(int frameSamples, int sampleRate) {
        try {
            return Optional.of(new SpeexGainControl(new AutomaticGainControl(frameSamples, sampleRate)));
        } catch (Exception | UnsatisfiedLinkError unavailable) {
            return Optional.empty();
        }
    }

    @Override
    public String name() {
        return "speex";
    }

    @Override
    public void process(short[] frame, int count) {
        if (closed) {
            return;
        }
        speex.agc(frame);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        speex.close();
    }
}
