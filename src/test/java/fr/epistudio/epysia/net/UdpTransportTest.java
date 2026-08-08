package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.transport.NetChannel;
import fr.epistudio.epysia.net.transport.TransportListener;
import fr.epistudio.epysia.net.transport.UdpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UdpTransportTest {
    private static final int PORT = 45_611;
    private static final int TRANSMISSION_UNIT = 600;
    private static final float STEP_SECONDS = 0.005f;
    private static final int MAXIMUM_POLLS = 400;

    private final UdpTransport server = new UdpTransport(TRANSMISSION_UNIT);
    private final UdpTransport client = new UdpTransport(TRANSMISSION_UNIT);
    private final RecordingListener serverListener = new RecordingListener();
    private final RecordingListener clientListener = new RecordingListener();

    @AfterEach
    void closeTransports() {
        client.close();
        server.close();
    }

    @Test
    void aReliablePayloadLargerThanTheTransmissionUnitIsReassembledIntact() {
        server.listen(PORT);
        int connection = client.connect("127.0.0.1", PORT);
        byte[] payload = rampOfLength(3_000);
        client.send(connection, NetChannel.RELIABLE, ByteBuffer.wrap(payload));
        pumpUntilReceived(1);
        assertArrayEquals(payload, serverListener.payloads.getFirst());
    }

    @Test
    void reliablePayloadsArriveInTheOrderTheyWereSent() {
        server.listen(PORT + 1);
        int connection = client.connect("127.0.0.1", PORT + 1);
        for (int index = 0; index < 8; index++) {
            client.send(connection, NetChannel.RELIABLE, ByteBuffer.wrap(new byte[]{(byte) index}));
        }
        pumpUntilReceived(8);
        for (int index = 0; index < 8; index++) {
            assertArrayEquals(new byte[]{(byte) index}, serverListener.payloads.get(index));
        }
    }

    private void pumpUntilReceived(int expectedCount) {
        for (int poll = 0; poll < MAXIMUM_POLLS && serverListener.payloads.size() < expectedCount; poll++) {
            server.poll(serverListener, STEP_SECONDS);
            client.poll(clientListener, STEP_SECONDS);
            pause();
        }
        assertTrue(serverListener.payloads.size() >= expectedCount,
                "expected " + expectedCount + " payloads but received " + serverListener.payloads.size());
    }

    private static void pause() {
        try {
            Thread.sleep(2L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] rampOfLength(int length) {
        byte[] payload = new byte[length];
        for (int index = 0; index < length; index++) {
            payload[index] = (byte) (index % 251);
        }
        return payload;
    }

    private static final class RecordingListener implements TransportListener {
        private final List<byte[]> payloads = new ArrayList<>();

        @Override
        public void onConnectionOpened(int connection) {
        }

        @Override
        public void onPacketReceived(int connection, NetChannel channel, NetReader reader) {
            byte[] payload = new byte[reader.remaining()];
            for (int index = 0; index < payload.length; index++) {
                payload[index] = (byte) reader.readByte();
            }
            payloads.add(payload);
        }

        @Override
        public void onConnectionClosed(int connection) {
        }
    }
}
