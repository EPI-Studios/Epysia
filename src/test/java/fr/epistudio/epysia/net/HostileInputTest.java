package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.protocol.ValueCodec;
import fr.epistudio.epysia.net.session.NetworkPeer;
import fr.epistudio.epysia.reflection.ExportedProperty;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HostileInputTest {
    private static final int CAPACITY = 256;
    private static final int SERVER_TICK = 1_000;
    private static final int LEAD_WINDOW = 50;
    private static final float TOLERANCE = 1.0e-5f;

    @Test
    void anAxisBeyondFullDeflectionIsClampedNotObeyed() {
        InputSample decoded = roundTrip(new InputSample(1, 0L, new float[]{1_000.0f, -1_000.0f}));
        assertEquals(1.0f, decoded.axis(0), TOLERANCE, "the server must not act on a claimed 1000");
        assertEquals(-1.0f, decoded.axis(1), TOLERANCE);
    }

    @Test
    void aNonFiniteAxisBecomesZero() {
        InputSample decoded = roundTrip(new InputSample(1, 0L,
                new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}));
        assertEquals(0.0f, decoded.axis(0), TOLERANCE);
        assertEquals(0.0f, decoded.axis(1), TOLERANCE);
        assertEquals(0.0f, decoded.axis(2), TOLERANCE);
    }

    @Test
    void anHonestAxisPassesThroughUntouched() {
        InputSample decoded = roundTrip(new InputSample(1, 0L, new float[]{0.5f, -0.25f}));
        assertEquals(0.5f, decoded.axis(0), TOLERANCE);
        assertEquals(-0.25f, decoded.axis(1), TOLERANCE);
    }

    @Test
    void aTickFarOutsideThePlausibleWindowIsRefused() {
        NetworkPeer peer = new NetworkPeer(1, 1);
        assertTrue(peer.offerInput(InputSample.empty(SERVER_TICK + 2, 2), SERVER_TICK, LEAD_WINDOW),
                "a sample slightly ahead is normal and must be accepted");
        assertFalse(peer.offerInput(InputSample.empty(Integer.MAX_VALUE, 2), SERVER_TICK, LEAD_WINDOW));
        assertFalse(peer.offerInput(InputSample.empty(SERVER_TICK - LEAD_WINDOW - 1, 2),
                SERVER_TICK, LEAD_WINDOW));
    }

    @Test
    void aNonFiniteVectorArgumentBecomesZero() {
        NetWriter writer = NetWriter.allocate(CAPACITY);
        ValueCodec.write(writer, ExportedProperty.Kind.VECTOR3,
                new Vector3f(Float.NaN, Float.POSITIVE_INFINITY, 3.0f));
        Vector3f decoded = (Vector3f) ValueCodec.read(new NetReader(writer.flipped()),
                ExportedProperty.Kind.VECTOR3, Vector3f.class);
        assertEquals(0.0f, decoded.x, TOLERANCE);
        assertEquals(0.0f, decoded.y, TOLERANCE);
        assertEquals(3.0f, decoded.z, TOLERANCE);
    }

    @Test
    void aDegenerateRotationBecomesTheIdentity() {
        NetWriter writer = NetWriter.allocate(CAPACITY);
        ValueCodec.write(writer, ExportedProperty.Kind.QUATERNION, new Quaternionf(0.0f, 0.0f, 0.0f, 0.0f));
        Quaternionf decoded = (Quaternionf) ValueCodec.read(new NetReader(writer.flipped()),
                ExportedProperty.Kind.QUATERNION, Quaternionf.class);
        assertEquals(1.0f, decoded.w, TOLERANCE, "a zero rotation must not reach a transform");
    }

    @Test
    void anOversizedRotationIsNormalised() {
        NetWriter writer = NetWriter.allocate(CAPACITY);
        ValueCodec.write(writer, ExportedProperty.Kind.QUATERNION, new Quaternionf(0.0f, 0.0f, 0.0f, 50.0f));
        Quaternionf decoded = (Quaternionf) ValueCodec.read(new NetReader(writer.flipped()),
                ExportedProperty.Kind.QUATERNION, Quaternionf.class);
        assertEquals(1.0f, decoded.lengthSquared(), TOLERANCE, "a rotation must arrive unit length");
    }

    private static InputSample roundTrip(InputSample sample) {
        NetWriter writer = NetWriter.allocate(CAPACITY);
        sample.write(writer);
        return InputSample.read(new NetReader(writer.flipped()));
    }
}
