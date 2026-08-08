package fr.epistudio.epysia.physics;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformCarryTest {
    private final Matrix3x2f scratch = new Matrix3x2f();
    private final Vector2f carry = new Vector2f();

    @Test
    void translationCarriesTheRiderByTheSameAmount() {
        Matrix3x2f before = new Matrix3x2f().translate(4.0f, 1.0f);
        Matrix3x2f after = new Matrix3x2f().translate(6.5f, 1.75f);
        PlatformCarry.delta(before, after, 10.0f, -3.0f, scratch, carry);
        assertEquals(2.5f, carry.x, 1.0e-5f);
        assertEquals(0.75f, carry.y, 1.0e-5f);
    }

    @Test
    void rotationCarriesTheRiderAroundThePlatformOrigin() {
        Matrix3x2f before = new Matrix3x2f();
        Matrix3x2f after = new Matrix3x2f().rotate((float) Math.PI * 0.5f);
        PlatformCarry.delta(before, after, 2.0f, 0.0f, scratch, carry);
        assertEquals(-2.0f, carry.x, 1.0e-5f);
        assertEquals(2.0f, carry.y, 1.0e-5f);
    }

    @Test
    void staticPlatformProducesNoCarry() {
        Matrix3x2f pose = new Matrix3x2f().translate(3.0f, 9.0f).rotate(0.4f);
        PlatformCarry.delta(pose, new Matrix3x2f(pose), 1.0f, 2.0f, scratch, carry);
        assertEquals(0.0f, carry.x, 0.0f);
        assertEquals(0.0f, carry.y, 0.0f);
    }
}
