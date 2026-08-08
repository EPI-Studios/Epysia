package fr.epistudio.epysia.net.voice;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

import java.nio.ShortBuffer;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class VoiceCapture {
    private static final int DEVICE_RING_FRAMES = 100;
    private static final int QUEUED_FRAME_LIMIT = 50;
    private static final long POLL_INTERVAL_MILLIS = 5L;

    private final BlockingQueue<short[]> readyFrames = new ArrayBlockingQueue<>(QUEUED_FRAME_LIMIT);
    private volatile long device;
    private volatile float latestLevel;
    private volatile boolean running;
    private volatile long droppedFrames;
    private Thread captureThread;

    public boolean open() {
        device = ALC11.alcCaptureOpenDevice((CharSequence) null, VoiceConfig.SAMPLE_RATE,
                AL10.AL_FORMAT_MONO16, VoiceConfig.FRAME_SAMPLES * DEVICE_RING_FRAMES);
        if (device == 0L) {
            return false;
        }
        ALC11.alcCaptureStart(device);
        running = true;
        captureThread = new Thread(this::pumpUntilStopped, "epysia-voice-capture");
        captureThread.setDaemon(true);
        captureThread.start();
        return true;
    }

    private void pumpUntilStopped() {
        ShortBuffer scratch = BufferUtils.createShortBuffer(VoiceConfig.FRAME_SAMPLES);
        while (running) {
            if (!drainOneFrame(scratch)) {
                sleepBriefly();
            }
        }
    }

    private boolean drainOneFrame(ShortBuffer scratch) {
        long handle = device;
        if (handle == 0L || availableSamples(handle) < VoiceConfig.FRAME_SAMPLES) {
            return false;
        }
        scratch.clear();
        ALC11.alcCaptureSamples(handle, scratch, VoiceConfig.FRAME_SAMPLES);
        scratch.position(0).limit(VoiceConfig.FRAME_SAMPLES);
        short[] frame = new short[VoiceConfig.FRAME_SAMPLES];
        scratch.get(frame, 0, VoiceConfig.FRAME_SAMPLES);
        latestLevel = rootMeanSquareOf(frame);
        offer(frame);
        return true;
    }

    private void offer(short[] frame) {
        while (!readyFrames.offer(frame)) {
            if (readyFrames.poll() != null) {
                droppedFrames++;
            }
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isOpen() {
        return device != 0L;
    }

    public float latestLevel() {
        return latestLevel;
    }

    public long droppedFrames() {
        return droppedFrames;
    }

    public Optional<short[]> readFrame() {
        return Optional.ofNullable(readyFrames.poll());
    }

    private static int availableSamples(long handle) {
        return ALC10.alcGetInteger(handle, ALC11.ALC_CAPTURE_SAMPLES);
    }

    private static float rootMeanSquareOf(short[] samples) {
        double total = 0.0;
        for (short sample : samples) {
            double normalized = sample / (double) Short.MAX_VALUE;
            total += normalized * normalized;
        }
        return (float) Math.sqrt(total / samples.length);
    }

    public void close() {
        running = false;
        joinCaptureThread();
        long handle = device;
        device = 0L;
        if (handle != 0L) {
            ALC11.alcCaptureStop(handle);
            ALC11.alcCaptureCloseDevice(handle);
        }
        readyFrames.clear();
        latestLevel = 0.0f;
    }

    private void joinCaptureThread() {
        if (captureThread == null) {
            return;
        }
        try {
            captureThread.join(500L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        captureThread = null;
    }
}
