package fr.epistudio.epysia.net.transport;

import fr.epistudio.epysia.net.protocol.NetReader;

public interface TransportListener {
    void onConnectionOpened(int connection);

    void onPacketReceived(int connection, NetChannel channel, NetReader reader);

    default void onPacketReceived(int connection, NetChannel channel, NetReader reader, long arrivalNanos) {
        onPacketReceived(connection, channel, reader);
    }

    void onConnectionClosed(int connection);
}
