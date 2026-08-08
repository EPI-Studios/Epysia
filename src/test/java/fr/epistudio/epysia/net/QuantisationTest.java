package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.protocol.SmallestThree;
import fr.epistudio.epysia.net.protocol.ValueCodec;
import fr.epistudio.epysia.reflection.ExportedProperty;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuantisationTest {
    private static final float MILLIMETRE = 0.001f;
    private static final float ANGLE_TOLERANCE_DEGREES = 0.5f;

    @Test
    void aQuantisedPositionSurvivesToTheMillimetre() {
        Vector3f original = new Vector3f(123.4567f, -8.9012f, 0.0004f);
        NetWriter writer = NetWriter.allocate(64);
        ValueCodec.write(writer, ExportedProperty.Kind.VECTOR3, original, MILLIMETRE);
        Vector3f decoded = (Vector3f) ValueCodec.read(new NetReader(writer.flipped()),
                ExportedProperty.Kind.VECTOR3, Vector3f.class, MILLIMETRE);
        assertEquals(original.x, decoded.x, MILLIMETRE);
        assertEquals(original.y, decoded.y, MILLIMETRE);
        assertEquals(original.z, decoded.z, MILLIMETRE);
    }

    @Test
    void aQuantisedPositionCostsFewerBytesThanRawFloats() {
        Vector3f original = new Vector3f(1.5f, -2.25f, 0.125f);
        NetWriter quantised = NetWriter.allocate(64);
        ValueCodec.write(quantised, ExportedProperty.Kind.VECTOR3, original, MILLIMETRE);
        NetWriter raw = NetWriter.allocate(64);
        ValueCodec.write(raw, ExportedProperty.Kind.VECTOR3, original, ValueCodec.NO_QUANTISATION);
        assertEquals(12, raw.position());
        assertTrue(quantised.position() < raw.position(),
                "quantised wrote " + quantised.position() + " bytes against " + raw.position());
    }

    @Test
    void aPackedRotationCostsFourBytesAndKeepsTheOrientation() {
        NetWriter writer = NetWriter.allocate(64);
        Quaternionf original = new Quaternionf().rotateXYZ(0.4f, -1.2f, 2.7f).normalize();
        ValueCodec.write(writer, ExportedProperty.Kind.QUATERNION, original, 1.0f);
        assertEquals(Integer.BYTES, writer.position());
        Quaternionf decoded = (Quaternionf) ValueCodec.read(new NetReader(writer.flipped()),
                ExportedProperty.Kind.QUATERNION, Quaternionf.class, 1.0f);
        assertOrientationMatches(original, decoded);
    }

    @Test
    void packedRotationsSurviveEveryDominantAxis() {
        Quaternionf[] rotations = {
                new Quaternionf(),
                new Quaternionf().rotateX((float) Math.PI * 0.99f),
                new Quaternionf().rotateY((float) Math.PI * 0.99f),
                new Quaternionf().rotateZ((float) Math.PI * 0.99f),
                new Quaternionf().rotateXYZ(1.1f, 2.2f, -0.7f)
        };
        for (Quaternionf rotation : rotations) {
            Quaternionf normalized = new Quaternionf(rotation).normalize();
            assertOrientationMatches(normalized, SmallestThree.unpack(SmallestThree.pack(normalized)));
        }
    }

    private static void assertOrientationMatches(Quaternionf expected, Quaternionf actual) {
        float dot = Math.abs(expected.x * actual.x + expected.y * actual.y
                + expected.z * actual.z + expected.w * actual.w);
        float angleDegrees = (float) Math.toDegrees(2.0 * Math.acos(Math.min(1.0f, dot)));
        assertTrue(angleDegrees < ANGLE_TOLERANCE_DEGREES,
                "orientation drifted by " + angleDegrees + " degrees");
    }
}
