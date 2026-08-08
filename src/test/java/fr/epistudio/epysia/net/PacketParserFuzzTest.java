package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.net.protocol.MalformedPacketException;
import fr.epistudio.epysia.net.protocol.MessageType;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.protocol.ValueCodec;
import fr.epistudio.epysia.net.replication.ReplicationTable;
import fr.epistudio.epysia.net.replication.SnapshotReader;
import fr.epistudio.epysia.net.replication.WorldState;
import fr.epistudio.epysia.net.session.SessionIdentity;
import fr.epistudio.epysia.reflection.ExportedProperty;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class PacketParserFuzzTest {
    private static final int ITERATIONS = 20_000;
    private static final int MAXIMUM_PACKET_BYTES = 1_400;
    private static final long SEED = 0xE9451AL;

    private final ReplicationTable table = ReplicationTable.builder()
            .addComponentType(ReplicatedStats.class)
            .build();
    private final SnapshotReader snapshotReader = new SnapshotReader(table);

    @Test
    void randomBytesNeverEscapeAsAnythingButAMalformedPacket() {
        Random random = new Random(SEED);
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            byte[] packet = randomBytes(random, random.nextInt(MAXIMUM_PACKET_BYTES) + 1);
            parseDefensively(packet, "random bytes");
        }
    }

    @Test
    void truncatedValidMessagesNeverEscapeEither() {
        Random random = new Random(SEED + 1);
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            byte[] whole = plausibleMessage(random);
            int cut = whole.length <= 1 ? 0 : random.nextInt(whole.length);
            byte[] truncated = new byte[cut];
            System.arraycopy(whole, 0, truncated, 0, cut);
            parseDefensively(truncated, "truncated message");
        }
    }

    @Test
    void bitFlippedMessagesNeverEscapeEither() {
        Random random = new Random(SEED + 2);
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            byte[] packet = plausibleMessage(random);
            if (packet.length > 0) {
                packet[random.nextInt(packet.length)] ^= (byte) (1 << random.nextInt(8));
            }
            parseDefensively(packet, "bit flipped message");
        }
    }

    @Test
    void anAbsurdLengthFieldIsRefusedRatherThanAllocated() {
        NetWriter writer = NetWriter.allocate(64);
        writer.writeVarInt(Integer.MAX_VALUE);
        NetReader reader = new NetReader(writer.flipped());
        try {
            reader.readSizedBytes();
            fail("a length far beyond the packet must be refused");
        } catch (MalformedPacketException expected) {
            assertTrue(expected.getMessage().contains("remain"));
        }
    }

    private void parseDefensively(byte[] packet, String what) {
        try {
            parseOneOfEachShape(packet);
        } catch (MalformedPacketException expected) {
            return;
        } catch (RuntimeException escaped) {
            fail(what + " escaped as " + escaped.getClass().getName() + ": " + escaped.getMessage());
        }
    }

    private void parseOneOfEachShape(byte[] packet) {
        readerFor(packet).readMessageType();
        tryRead(() -> SessionIdentity.read(readerFor(packet)));
        tryRead(() -> InputSample.read(readerFor(packet)));
        tryRead(() -> ValueCodec.read(readerFor(packet), ExportedProperty.Kind.VECTOR3, Vector3f.class));
        tryRead(() -> snapshotReader.read(readerFor(packet), tick -> Optional.of(new WorldState())));
    }

    private static void tryRead(Runnable read) {
        try {
            read.run();
        } catch (MalformedPacketException tolerated) {
            return;
        }
    }

    private static NetReader readerFor(byte[] packet) {
        return NetReader.wrapping(packet, 0, packet.length);
    }

    private static byte[] plausibleMessage(Random random) {
        NetWriter writer = NetWriter.allocate(MAXIMUM_PACKET_BYTES);
        MessageType[] types = MessageType.values();
        writer.writeMessageType(types[random.nextInt(types.length)]);
        int payload = random.nextInt(80);
        for (int index = 0; index < payload; index++) {
            writer.writeByte(random.nextInt(256));
        }
        return writer.toByteArray();
    }

    private static byte[] randomBytes(Random random, int length) {
        byte[] packet = new byte[length];
        random.nextBytes(packet);
        return packet;
    }
}
