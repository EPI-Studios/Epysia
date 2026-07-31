package fr.epistudio.epysia.components.transforms;

import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TransformInterpolationResetTest {

    private static final float TOLERANCE = 1.0e-4f;

    private static Transform3D attachedTransform(String name) {
        GameObject owner = new GameObject(name);
        return owner.addComponent(new Transform3D());
    }

    @Test
    void aMoveWithoutResetIsBlendedHalfway() {
        Transform3D transform = attachedTransform("Mover");
        transform.setPosition(0.0f, 0.0f, 0.0f);
        transform.captureInterpolationSnapshot();
        transform.setPosition(10.0f, 0.0f, 0.0f);

        Vector3f midFrame = transform.worldMatrix(0.5f).getTranslation(new Vector3f());

        assertEquals(5.0f, midFrame.x, TOLERANCE, "ordinary motion must still interpolate");
    }

    @Test
    void aTeleportFollowedByResetShowsNoIntermediatePosition() {
        Transform3D transform = attachedTransform("Teleporter");
        transform.setPosition(0.0f, 0.0f, 0.0f);
        transform.captureInterpolationSnapshot();
        transform.setPosition(10.0f, 0.0f, 0.0f);
        transform.resetInterpolation();

        Vector3f midFrame = transform.worldMatrix(0.5f).getTranslation(new Vector3f());

        assertEquals(10.0f, midFrame.x, TOLERANCE,
                "a teleported transform must not be smeared across the frame");
        assertTrue(transform.worldMatrixStable(0.5f));
    }

    @Test
    void aChildFollowsTheResetParentWithoutSmearing() {
        Transform3D parent = attachedTransform("Parent");
        Transform3D child = attachedTransform("Child");
        child.setParent(parent);
        child.setPosition(0.0f, 1.0f, 0.0f);
        parent.setPosition(0.0f, 0.0f, 0.0f);
        parent.captureInterpolationSnapshot();
        child.captureInterpolationSnapshot();

        parent.setPosition(10.0f, 0.0f, 0.0f);
        parent.resetInterpolation();

        Vector3f midFrame = child.worldMatrix(0.5f).getTranslation(new Vector3f());

        assertEquals(10.0f, midFrame.x, TOLERANCE);
        assertEquals(1.0f, midFrame.y, TOLERANCE);
    }
}
