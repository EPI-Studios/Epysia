package fr.epistudio.epysia.net.voice;

import java.util.TreeMap;

public final class VoiceJitterBuffer {
    private static final int SEQUENCE_HALF = VoiceFrame.SEQUENCE_MODULUS / 2;
    private static final int MAXIMUM_PENDING = 32;

    private final TreeMap<Long, byte[]> pendingBySequence = new TreeMap<>();
    private final int targetFrames;
    private long nextSequence;
    private long lastUnwrapped;
    private int lastSequence;
    private boolean primed;
    private boolean seenAny;
    private long lateFrames;

    public VoiceJitterBuffer(int targetFrames) {
        this.targetFrames = Math.max(1, targetFrames);
    }

    public boolean push(int sequence, byte[] payload) {
        long unwrapped = unwrap(sequence);
        if (primed && unwrapped < nextSequence) {
            lateFrames++;
            return false;
        }
        pendingBySequence.put(unwrapped, payload);
        while (pendingBySequence.size() > MAXIMUM_PENDING) {
            pendingBySequence.pollFirstEntry();
        }
        return true;
    }

    private long unwrap(int sequence) {
        if (!seenAny) {
            seenAny = true;
            lastSequence = sequence;
            lastUnwrapped = sequence;
            return lastUnwrapped;
        }
        int delta = ((sequence - lastSequence + SEQUENCE_HALF) & (VoiceFrame.SEQUENCE_MODULUS - 1)) - SEQUENCE_HALF;
        lastSequence = sequence;
        lastUnwrapped += delta;
        return lastUnwrapped;
    }

    public Outcome pop() {
        if (!primed) {
            return prime();
        }
        byte[] ready = pendingBySequence.remove(nextSequence);
        if (ready != null) {
            nextSequence++;
            return Outcome.play(ready);
        }
        if (pendingBySequence.isEmpty()) {
            primed = false;
            return Outcome.silence();
        }
        nextSequence++;
        return Outcome.conceal();
    }

    private Outcome prime() {
        if (pendingBySequence.size() < targetFrames) {
            return Outcome.silence();
        }
        primed = true;
        nextSequence = pendingBySequence.firstKey();
        return pop();
    }

    public int depth() {
        return pendingBySequence.size();
    }

    public long lateFrames() {
        return lateFrames;
    }

    public void clear() {
        pendingBySequence.clear();
        primed = false;
        seenAny = false;
    }

    public enum Kind {
        PLAY,
        CONCEAL,
        SILENCE
    }

    public record Outcome(Kind kind, byte[] payload) {
        private static final byte[] NO_PAYLOAD = new byte[0];

        static Outcome play(byte[] payload) {
            return new Outcome(Kind.PLAY, payload);
        }

        static Outcome conceal() {
            return new Outcome(Kind.CONCEAL, NO_PAYLOAD);
        }

        static Outcome silence() {
            return new Outcome(Kind.SILENCE, NO_PAYLOAD);
        }
    }
}
