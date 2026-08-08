package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.transport.LoopbackTransport;
import fr.epistudio.epysia.net.transport.NetChannel;
import fr.epistudio.epysia.net.transport.TransportListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LoopbackTransportTest {
    private static final int PORT = 45_501;

    private final LoopbackTransport server = new LoopbackTransport();
    private final LoopbackTransport client = new LoopbackTransport();

    @AfterEach
    void closeTransports() {
        client.close();
        server.close();
    }

    @Test
    void aPayloadSentByTheClientArrivesAtTheServerIntact() {
        server.listen(PORT);
        int connection = client.connect("localhost", PORT);
        client.send(connection, NetChannel.RELIABLE, ByteBuffer.wrap(new byte[]{7, 8, 9}));
        RecordingListener listener = new RecordingListener();
        server.poll(listener, 0.016f);
        assertEquals(1, listener.openedConnections.size());
        assertEquals(List.of("7,8,9"), listener.payloads);
    }

    @Test
    void disconnectingClosesTheRemoteSide() {
        server.listen(PORT + 1);
        int connection = client.connect("localhost", PORT + 1);
        RecordingListener listener = new RecordingListener();
        server.poll(listener, 0.016f);
        client.disconnect(connection);
        server.poll(listener, 0.016f);
        assertEquals(1, listener.closedConnections.size());
    }

    private static final class RecordingListener implements TransportListener {
        private final List<Integer> openedConnections = new ArrayList<>();
        private final List<Integer> closedConnections = new ArrayList<>();
        private final List<String> payloads = new ArrayList<>();

        @Override
        public void onConnectionOpened(int connection) {
            openedConnections.add(connection);
        }

        @Override
        public void onPacketReceived(int connection, NetChannel channel, NetReader reader) {
            StringBuilder text = new StringBuilder();
            while (reader.hasRemaining()) {
                text.append(text.isEmpty() ? "" : ",").append(reader.readByte());
            }
            payloads.add(text.toString());
        }

        @Override
        public void onConnectionClosed(int connection) {
            closedConnections.add(connection);
        }
    }
}
