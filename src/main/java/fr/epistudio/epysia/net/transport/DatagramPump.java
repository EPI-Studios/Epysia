package fr.epistudio.epysia.net.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class DatagramPump {
    public record Inbound(SocketAddress source, byte[] payload, long arrivalNanos) {
    }

    private record Outbound(SocketAddress target, byte[] payload) {
    }

    private static final int RECEIVE_BUFFER_BYTES = 2048;
    private static final int INBOUND_CEILING = 4096;
    private static final long SELECT_TIMEOUT_MILLIS = 2L;

    private final Queue<Inbound> inbound = new ConcurrentLinkedQueue<>();
    private final Queue<Outbound> outbound = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inboundSize = new AtomicInteger();
    private final DatagramChannel channel;
    private final Selector selector;
    private final Thread thread;

    private volatile boolean running = true;
    private volatile IOException failure;
    private long droppedInbound;

    public DatagramPump(InetSocketAddress bindAddress, String threadName) {
        try {
            channel = DatagramChannel.open();
            channel.configureBlocking(false);
            channel.bind(bindAddress);
            selector = Selector.open();
            channel.register(selector, SelectionKey.OP_READ);
        } catch (IOException error) {
            throw new TransportException("Cannot open datagram channel on " + bindAddress, error);
        }
        thread = new Thread(this::pump, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    public void send(SocketAddress target, ByteBuffer datagram) {
        byte[] copy = new byte[datagram.remaining()];
        datagram.duplicate().get(copy);
        outbound.add(new Outbound(target, copy));
        selector.wakeup();
    }

    public Inbound receive() {
        Inbound next = inbound.poll();
        if (next != null) {
            inboundSize.decrementAndGet();
        }
        return next;
    }

    public void rethrowFailure() {
        IOException error = failure;
        if (error != null) {
            failure = null;
            throw new TransportException("Datagram pump failed", error);
        }
    }

    public long droppedInbound() {
        return droppedInbound;
    }

    public void close() {
        running = false;
        selector.wakeup();
        try {
            thread.join(500L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        closeQuietly();
    }

    private void pump() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(RECEIVE_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        while (running) {
            try {
                selector.select(SELECT_TIMEOUT_MILLIS);
                selector.selectedKeys().clear();
                drainSocket(buffer);
                drainOutbound();
            } catch (IOException error) {
                if (running) {
                    failure = error;
                }
                return;
            }
        }
    }

    private void drainSocket(ByteBuffer buffer) throws IOException {
        while (running) {
            buffer.clear();
            SocketAddress source = channel.receive(buffer);
            if (source == null) {
                return;
            }
            buffer.flip();
            acceptInbound(source, buffer);
        }
    }

    private void acceptInbound(SocketAddress source, ByteBuffer buffer) {
        if (inboundSize.get() >= INBOUND_CEILING) {
            droppedInbound++;
            return;
        }
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        inbound.add(new Inbound(source, payload, System.nanoTime()));
        inboundSize.incrementAndGet();
    }

    private void drainOutbound() throws IOException {
        Outbound next = outbound.poll();
        while (next != null) {
            channel.send(ByteBuffer.wrap(next.payload()), next.target());
            next = outbound.poll();
        }
    }

    private void closeQuietly() {
        try {
            selector.close();
        } catch (IOException ignored) {
        }
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }
}
