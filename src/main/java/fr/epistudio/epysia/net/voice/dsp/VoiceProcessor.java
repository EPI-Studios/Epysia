package fr.epistudio.epysia.net.voice.dsp;

public final class VoiceProcessor {
    private static final float DEFAULT_SPEECH_THRESHOLD = 0.5f;

    private final HighPassFilter highPass;
    private final NoiseGate gate = new NoiseGate();
    private final GainControl gain;
    private final NoiseSuppression noiseSuppression = NoiseSuppressors.create();
    private float speechProbability;
    private final EchoSuppressor echoSuppressor = new EchoSuppressor();
    private final float[] working;
    private final float frameSeconds;
    private float outputLevel;
    private boolean destroyed;

    public VoiceProcessor(int sampleRate, int frameSamples) {
        this.gain = GainControls.create(frameSamples, sampleRate);
        this.highPass = new HighPassFilter(sampleRate);
        this.working = new float[frameSamples];
        this.frameSeconds = frameSamples / (float) sampleRate;
    }

    public boolean process(short[] frame, float playbackLevel, float holdSeconds, boolean gateEnabled) {
        return process(frame, playbackLevel, holdSeconds, gateEnabled, DEFAULT_SPEECH_THRESHOLD);
    }

    public boolean process(short[] frame, float playbackLevel, float holdSeconds, boolean gateEnabled,
                           float speechThreshold) {
        int count = Math.min(frame.length, working.length);
        boolean suppressing = echoSuppressor.update(playbackLevel, frameSeconds);
        toFloat(frame, count);
        highPass.process(working, count);
        float level = levelOf(count);
        toShort(frame, count);
        speechProbability = noiseSuppression.process(frame, count);
        boolean open = !gateEnabled || isSpeaking(frame, count, holdSeconds, speechThreshold);
        gain.process(frame, count);
        echoSuppressor.attenuate(frame, count);
        outputLevel = levelOf(frame, count);
        return open && !suppressing;
    }

    private boolean isSpeaking(short[] frame, int count, float holdSeconds, float speechThreshold) {
        if (speechThreshold > 0.0f && speechProbability != NoiseSuppression.NO_ESTIMATE) {
            return gate.updateFromProbability(speechProbability, speechThreshold, holdSeconds, frameSeconds);
        }
        return gate.update(levelOf(frame, count), holdSeconds, frameSeconds);
    }

    public float speechProbability() {
        return speechProbability;
    }

    public String noiseSuppressionName() {
        return noiseSuppression.name();
    }

    public String gainName() {
        return gain.name();
    }

    public void destroy() {
        gain.close();
        noiseSuppression.close();
        destroyed = true;
    }

    public boolean destroyed() {
        return destroyed;
    }

    private void toFloat(short[] frame, int count) {
        for (int index = 0; index < count; index++) {
            working[index] = frame[index] / (float) Short.MAX_VALUE;
        }
    }

    private void toShort(short[] frame, int count) {
        for (int index = 0; index < count; index++) {
            frame[index] = (short) Math.clamp(working[index] * Short.MAX_VALUE,
                    Short.MIN_VALUE, Short.MAX_VALUE);
        }
    }

    private float levelOf(int count) {
        double total = 0.0;
        for (int index = 0; index < count; index++) {
            total += working[index] * working[index];
        }
        return (float) Math.sqrt(total / Math.max(1, count));
    }

    private static float levelOf(short[] frame, int count) {
        double total = 0.0;
        for (int index = 0; index < count; index++) {
            double normalized = frame[index] / (double) Short.MAX_VALUE;
            total += normalized * normalized;
        }
        return (float) Math.sqrt(total / Math.max(1, count));
    }

    public float outputLevel() {
        return outputLevel;
    }

    public float noiseFloor() {
        return gate.noiseFloor();
    }

    public boolean suppressingEcho() {
        return echoSuppressor.suppressing();
    }

    public void reset() {
        highPass.reset();
        gate.reset();
        echoSuppressor.reset();
    }
}
