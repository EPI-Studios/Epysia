package fr.epistudio.epysia.net.voice.dsp;

import de.maxhenkel.rnnoise4j.Denoiser;

import java.util.Arrays;
import java.util.Optional;

final class RnnoiseSuppression implements NoiseSuppression {
    private final Denoiser denoiser;
    private final short[] chunk;
    private final int frameSize;
    private boolean closed;

    private RnnoiseSuppression(Denoiser denoiser) {
        this.denoiser = denoiser;
        this.frameSize = denoiser.getFrameSize();
        this.chunk = new short[frameSize];
    }

    static Optional<NoiseSuppression> tryCreate() {
        try {
            return Optional.of(new RnnoiseSuppression(new Denoiser()));
        } catch (Exception | UnsatisfiedLinkError unavailable) {
            return Optional.empty();
        }
    }

    @Override
    public String name() {
        return "rnnoise";
    }

    @Override
    public float process(short[] frame, int count) {
        if (closed || frameSize <= 0) {
            return NO_ESTIMATE;
        }
        float highest = 0.0f;
        for (int offset = 0; offset + frameSize <= count; offset += frameSize) {
            System.arraycopy(frame, offset, chunk, 0, frameSize);
            highest = Math.max(highest, denoiser.denoiseInPlace(chunk));
            System.arraycopy(chunk, 0, frame, offset, frameSize);
        }
        return highest;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        denoiser.close();
    }
}
