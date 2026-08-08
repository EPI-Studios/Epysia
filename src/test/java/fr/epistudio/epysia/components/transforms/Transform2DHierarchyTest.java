package fr.epistudio.epysia.components.transforms;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Transform2DHierarchyTest {
    @Test
    void childFollowsParentRotationAndTranslation() {
        Transform2D parent = new Transform2D().setPosition(10.0f, 0.0f);
        Transform2D child = new Transform2D().setPosition(2.0f, 0.0f);
        child.setParent(parent);
        parent.setRotationRadians((float) Math.PI * 0.5f);
        Vector2f world = child.worldPosition(new Vector2f());
        assertEquals(10.0f, world.x, 1.0e-5f);
        assertEquals(2.0f, world.y, 1.0e-5f);
    }

    @Test
    void worldPositionRoundTripsThroughSetWorldPosition() {
        Transform2D parent = new Transform2D().setPosition(-4.0f, 7.0f).setRotationRadians(0.6f);
        parent.setScale(2.0f, 2.0f);
        Transform2D child = new Transform2D();
        child.setParent(parent);
        child.setWorldPosition(3.5f, -1.25f);
        Vector2f world = child.worldPosition(new Vector2f());
        assertEquals(3.5f, world.x, 1.0e-4f);
        assertEquals(-1.25f, world.y, 1.0e-4f);
    }

    @Test
    void setLocalMatrixReproducesTheMatrixItWasGiven() {
        Transform2D transform = new Transform2D().setPivot(0.5f, 0.25f);
        Matrix3x2f target = new Matrix3x2f().translate(5.0f, -2.0f).rotate(0.9f).scale(1.5f, 3.0f)
                .translate(-0.5f, -0.25f);
        transform.setLocalMatrix(target);
        Matrix3x2f rebuilt = transform.localMatrix();
        assertEquals(target.m00(), rebuilt.m00(), 1.0e-4f);
        assertEquals(target.m01(), rebuilt.m01(), 1.0e-4f);
        assertEquals(target.m10(), rebuilt.m10(), 1.0e-4f);
        assertEquals(target.m11(), rebuilt.m11(), 1.0e-4f);
        assertEquals(target.m20(), rebuilt.m20(), 1.0e-4f);
        assertEquals(target.m21(), rebuilt.m21(), 1.0e-4f);
    }

    @Test
    void worldOriginFollowsThePivotWhileWorldPositionDoesNot() {
        Transform2D transform = new Transform2D().setPosition(4.0f, 1.0f).setPivot(0.5f, 0.25f);
        transform.setRotationRadians((float) Math.PI * 0.5f);
        Vector2f position = transform.worldPosition(new Vector2f());
        Vector2f origin = transform.worldOrigin(new Vector2f());
        assertEquals(4.0f, position.x, 1.0e-5f);
        assertEquals(1.0f, position.y, 1.0e-5f);
        assertEquals(4.25f, origin.x, 1.0e-5f);
        assertEquals(0.5f, origin.y, 1.0e-5f);
    }

    @Test
    void setWorldOriginRoundTripsUnderPivotRotationAndScale() {
        Transform2D parent = new Transform2D().setPosition(-3.0f, 2.0f).setRotationRadians(0.4f);
        Transform2D transform = new Transform2D().setPivot(0.75f, -0.5f).setRotationRadians(1.1f);
        transform.setScale(2.0f, 0.5f);
        transform.setParent(parent);
        transform.setWorldOrigin(6.5f, -2.25f);
        Vector2f origin = transform.worldOrigin(new Vector2f());
        assertEquals(6.5f, origin.x, 1.0e-4f);
        assertEquals(-2.25f, origin.y, 1.0e-4f);
    }

    @Test
    void parentingCannotCreateACycle() {
        Transform2D root = new Transform2D();
        Transform2D child = new Transform2D();
        assertTrue(child.setParent(root));
        assertFalse(root.setParent(child));
    }
}
