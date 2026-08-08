package fr.epistudio.epysia.net.transport;

import java.nio.ByteBuffer;

public interface Transport {
    void listen(int port);

    int connect(String host, int port);

    void send(int connection, NetChannel channel, ByteBuffer payload);

    void poll(TransportListener listener, float deltaTimeSeconds);

    void disconnect(int connection);

    boolean isConnectionAlive(int connection);

    String addressOf(int connection);

    void close();
}
