package fr.epistudio.epysia.net.transport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ReliableChannel {
    static final int ACK_ONLY_SEQUENCE = -1;
    private static final int ACK_BIT_COUNT = 32;
    private static final float RESEND_INTERVAL_SECONDS = 0.1f;
    private static final int MAXIMUM_REORDER_BUFFER = 1024;

    private final Map<Integer, PendingPacket> unacknowledged = new LinkedHashMap<>();
    private final Map<Integer, byte[]> reorderBuffer = new HashMap<>();
    private final List<byte[]> readyPayloads = new ArrayList<>();
    private int nextOutgoingSequence;
    private int nextExpectedIncoming;

    int allocateSequence() {
        return nextOutgoingSequence++;
    }

    void recordSent(int sequence, byte[] datagram) {
        unacknowledged.put(sequence, new PendingPacket(datagram));
    }

    int ackUpTo() {
        return nextExpectedIncoming;
    }

    int ackBits() {
        int bits = 0;
        for (int offset = 0; offset < ACK_BIT_COUNT; offset++) {
            if (reorderBuffer.containsKey(nextExpectedIncoming + offset)) {
                bits |= 1 << offset;
            }
        }
        return bits;
    }

    void acknowledge(int ackUpTo, int ackBits) {
        unacknowledged.keySet().removeIf(sequence -> sequence < ackUpTo);
        for (int offset = 0; offset < ACK_BIT_COUNT; offset++) {
            if ((ackBits & (1 << offset)) != 0) {
                unacknowledged.remove(ackUpTo + offset);
            }
        }
    }

    boolean accept(int sequence, byte[] payload) {
        if (sequence < nextExpectedIncoming || reorderBuffer.containsKey(sequence)) {
            return false;
        }
        if (reorderBuffer.size() >= MAXIMUM_REORDER_BUFFER) {
            return false;
        }
        reorderBuffer.put(sequence, payload);
        return true;
    }

    List<byte[]> drainInOrder() {
        readyPayloads.clear();
        byte[] next = reorderBuffer.remove(nextExpectedIncoming);
        while (next != null) {
            readyPayloads.add(next);
            nextExpectedIncoming++;
            next = reorderBuffer.remove(nextExpectedIncoming);
        }
        return readyPayloads;
    }

    List<byte[]> datagramsDueForResend(float deltaTimeSeconds) {
        List<byte[]> due = new ArrayList<>();
        for (PendingPacket pending : unacknowledged.values()) {
            if (pending.advance(deltaTimeSeconds)) {
                due.add(pending.datagram());
            }
        }
        return due;
    }

    int unacknowledgedCount() {
        return unacknowledged.size();
    }

    private static final class PendingPacket {
        private final byte[] datagram;
        private float secondsSinceSend;

        private PendingPacket(byte[] datagram) {
            this.datagram = datagram;
        }

        private boolean advance(float deltaTimeSeconds) {
            secondsSinceSend += deltaTimeSeconds;
            if (secondsSinceSend < RESEND_INTERVAL_SECONDS) {
                return false;
            }
            secondsSinceSend = 0.0f;
            return true;
        }

        private byte[] datagram() {
            return datagram;
        }
    }
}
