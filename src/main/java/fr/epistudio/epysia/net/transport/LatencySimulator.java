package fr.epistudio.epysia.net.transport;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class LatencySimulator implements Transport {
    private final Transport delegate;
    private final Random random;
    private final List<DelayedSend> pending = new ArrayList<>();
    private float delaySeconds;
    private float jitterSeconds;
    private float lossProbability;
    private float clockSeconds;
    private long droppedPackets;

    public LatencySimulator(Transport delegate, long randomSeed) {
        this.delegate = delegate;
        this.random = new Random(randomSeed);
    }

    public LatencySimulator configure(float delaySeconds, float jitterSeconds, float lossProbability) {
        this.delaySeconds = Math.max(0.0f, delaySeconds);
        this.jitterSeconds = Math.max(0.0f, jitterSeconds);
        this.lossProbability = Math.clamp(lossProbability, 0.0f, 1.0f);
        return this;
    }

    public long droppedPackets() {
        return droppedPackets;
    }

    @Override
    public void listen(int port) {
        delegate.listen(port);
    }

    @Override
    public int connect(String host, int port) {
        return delegate.connect(host, port);
    }

    @Override
    public void send(int connection, NetChannel channel, ByteBuffer payload) {
        if (lossProbability > 0.0f && random.nextFloat() < lossProbability) {
            droppedPackets++;
            return;
        }
        byte[] copy = new byte[payload.remaining()];
        payload.duplicate().get(copy);
        pending.add(new DelayedSend(connection, channel, copy, clockSeconds + sampleDelaySeconds()));
    }

    private float sampleDelaySeconds() {
        if (jitterSeconds <= 0.0f) {
            return delaySeconds;
        }
        return Math.max(0.0f, delaySeconds + (random.nextFloat() * 2.0f - 1.0f) * jitterSeconds);
    }

    @Override
    public void poll(TransportListener listener, float deltaTimeSeconds) {
        clockSeconds += deltaTimeSeconds;
        flushDueSends();
        delegate.poll(listener, deltaTimeSeconds);
    }

    private void flushDueSends() {
        Iterator<DelayedSend> iterator = pending.iterator();
        while (iterator.hasNext()) {
            DelayedSend delayed = iterator.next();
            if (delayed.releaseSeconds() > clockSeconds) {
                continue;
            }
            delegate.send(delayed.connection(), delayed.channel(), ByteBuffer.wrap(delayed.payload()));
            iterator.remove();
        }
    }

    @Override
    public void disconnect(int connection) {
        delegate.disconnect(connection);
    }

    @Override
    public boolean isConnectionAlive(int connection) {
        return delegate.isConnectionAlive(connection);
    }

    @Override
    public String addressOf(int connection) {
        return delegate.addressOf(connection);
    }

    @Override
    public void close() {
        pending.clear();
        delegate.close();
    }

    private record DelayedSend(int connection, NetChannel channel, byte[] payload, float releaseSeconds) {
    }
}
