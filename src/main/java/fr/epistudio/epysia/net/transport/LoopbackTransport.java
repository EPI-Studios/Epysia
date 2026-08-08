package fr.epistudio.epysia.net.transport;

import fr.epistudio.epysia.net.protocol.NetReader;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class LoopbackTransport implements Transport {
    private static final Map<Integer, LoopbackTransport> LISTENERS = new ConcurrentHashMap<>();
    private static final byte[] NO_PAYLOAD = new byte[0];

    private final AtomicInteger nextConnection = new AtomicInteger(1);
    private final Map<Integer, Endpoint> endpoints = new ConcurrentHashMap<>();
    private final Deque<LoopbackEvent> inbox = new ArrayDeque<>();
    private int listeningPort = -1;

    @Override
    public void listen(int port) {
        if (LISTENERS.putIfAbsent(port, this) != null) {
            throw new TransportException("Loopback port " + port + " is already listening");
        }
        listeningPort = port;
    }

    @Override
    public int connect(String host, int port) {
        LoopbackTransport server = LISTENERS.get(port);
        if (server == null) {
            throw new TransportException("No loopback transport listening on port " + port);
        }
        int localConnection = nextConnection.getAndIncrement();
        int remoteConnection = server.nextConnection.getAndIncrement();
        endpoints.put(localConnection, new Endpoint(server, remoteConnection));
        server.endpoints.put(remoteConnection, new Endpoint(this, localConnection));
        server.enqueue(new LoopbackEvent(EventKind.OPENED, remoteConnection, NetChannel.RELIABLE, NO_PAYLOAD));
        return localConnection;
    }

    @Override
    public void send(int connection, NetChannel channel, ByteBuffer payload) {
        Endpoint endpoint = endpoints.get(connection);
        if (endpoint == null) {
            return;
        }
        byte[] copy = new byte[payload.remaining()];
        payload.duplicate().get(copy);
        endpoint.transport().enqueue(
                new LoopbackEvent(EventKind.PACKET, endpoint.remoteConnection(), channel, copy));
    }

    @Override
    public void poll(TransportListener listener, float deltaTimeSeconds) {
        int pending = drainSize();
        for (int index = 0; index < pending; index++) {
            LoopbackEvent event = pollEvent();
            if (event != null) {
                dispatch(listener, event);
            }
        }
    }

    private void dispatch(TransportListener listener, LoopbackEvent event) {
        switch (event.kind()) {
            case OPENED -> listener.onConnectionOpened(event.connection());
            case CLOSED -> listener.onConnectionClosed(event.connection());
            case PACKET -> listener.onPacketReceived(event.connection(), event.channel(),
                    NetReader.wrapping(event.payload(), 0, event.payload().length));
        }
    }

    @Override
    public void disconnect(int connection) {
        Endpoint endpoint = endpoints.remove(connection);
        if (endpoint == null) {
            return;
        }
        endpoint.transport().endpoints.remove(endpoint.remoteConnection());
        endpoint.transport().enqueue(
                new LoopbackEvent(EventKind.CLOSED, endpoint.remoteConnection(), NetChannel.RELIABLE, NO_PAYLOAD));
    }

    @Override
    public boolean isConnectionAlive(int connection) {
        return endpoints.containsKey(connection);
    }

    @Override
    public String addressOf(int connection) {
        return "loopback:" + connection;
    }

    @Override
    public void close() {
        for (int connection : endpoints.keySet().stream().toList()) {
            disconnect(connection);
        }
        if (listeningPort >= 0) {
            LISTENERS.remove(listeningPort, this);
            listeningPort = -1;
        }
        synchronized (inbox) {
            inbox.clear();
        }
    }

    private void enqueue(LoopbackEvent event) {
        synchronized (inbox) {
            inbox.add(event);
        }
    }

    private int drainSize() {
        synchronized (inbox) {
            return inbox.size();
        }
    }

    private LoopbackEvent pollEvent() {
        synchronized (inbox) {
            return inbox.poll();
        }
    }

    private enum EventKind {
        OPENED,
        PACKET,
        CLOSED
    }

    private record Endpoint(LoopbackTransport transport, int remoteConnection) {
    }

    private record LoopbackEvent(EventKind kind, int connection, NetChannel channel, byte[] payload) {
    }
}
