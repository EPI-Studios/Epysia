package fr.epistudio.epysia.utils;

import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.components.transforms.TransformAscii;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathTypesTest {

    private static final float EPSILON = 1.0e-5f;

    @Test
    void vector2fSupportsBasicOperations() {
        Vector2f vector = new Vector2f(3.0f, 4.0f);

        assertEquals(5.0f, vector.length(), EPSILON);
        assertEquals(new Vector2f(4.0f, 5.0f), vector.add(1.0f));
        assertEquals(11.0f, vector.dot(new Vector2f(1.0f, 2.0f)), EPSILON);
        assertEquals(new Vector2i(3, 4), vector.normalize().multiply(5.0f).toVector2i());
    }

    @Test
    void vector3fCrossProductIsOrthogonal() {
        Vector3f cross = Vector3f.UNIT_X.cross(Vector3f.UNIT_Y);

        assertEquals(Vector3f.UNIT_Z, cross);
        assertEquals(0.0f, cross.dot(Vector3f.UNIT_X), EPSILON);
        assertEquals(0.0f, cross.dot(Vector3f.UNIT_Y), EPSILON);
    }

    @Test
    void matrix2fRotationRotatesUnitXToUnitY() {
        Vector2f rotated = Matrix2f.rotation((float) (Math.PI / 2.0)).multiply(Vector2f.UNIT_X);

        assertEquals(0.0f, rotated.getX(), EPSILON);
        assertEquals(1.0f, rotated.getY(), EPSILON);
    }

    @Test
    void matrix3fTransforms2dPoints() {
        Matrix3f transform = Matrix3f.trs(new Vector2f(5.0f, -2.0f), (float) (Math.PI / 2.0), new Vector2f(2.0f, 1.0f));
        Vector2f result = transform.transformPoint(new Vector2f(1.0f, 0.0f));

        assertEquals(5.0f, result.getX(), EPSILON);
        assertEquals(0.0f, result.getY(), EPSILON);
    }

    @Test
    void quaternionRotatesVectors() {
        Quaternionf rotation = Quaternionf.fromAxisAngle(Vector3f.UNIT_Z, (float) (Math.PI / 2.0));
        Vector3f result = rotation.rotate(Vector3f.UNIT_X);

        assertEquals(0.0f, result.getX(), EPSILON);
        assertEquals(1.0f, result.getY(), EPSILON);
        assertEquals(0.0f, result.getZ(), EPSILON);
    }

    @Test
    void matrix4fInverseRestoresOriginalPoint() {
        Matrix4f transform = Matrix4f.trs(
                new Vector3f(2.0f, -1.0f, 5.0f),
                Quaternionf.fromAxisAngle(Vector3f.UNIT_Y, (float) (Math.PI / 3.0)),
                new Vector3f(2.0f, 3.0f, 4.0f)
        );
        Vector3f source = new Vector3f(1.0f, 2.0f, 3.0f);

        Vector3f transformed = transform.transformPoint(source);
        Vector3f restored = transform.inverse().transformPoint(transformed);

        assertEquals(source.getX(), restored.getX(), EPSILON);
        assertEquals(source.getY(), restored.getY(), EPSILON);
        assertEquals(source.getZ(), restored.getZ(), EPSILON);
    }

    @Test
    void transform2dBuildsConsistentMatrices() {
        Transform2D transform = new Transform2D(new Vector2f(5.0f, 2.0f), new Vector2f(2.0f, 3.0f), 0.0f, 4);
        Vector2f point = transform.transformPoint(new Vector2f(1.0f, 1.0f));

        assertEquals(7.0f, point.getX(), EPSILON);
        assertEquals(5.0f, point.getY(), EPSILON);
        assertEquals(new Vector2f(1.0f, 1.0f), transform.inverseTransformPoint(point));
        assertEquals(4, transform.getZIndex());
    }

    @Test
    void transform3dRotatesAndTransformsPoints() {
        Transform3D transform = new Transform3D();
        transform.setPosition(new Vector3f(1.0f, 0.0f, 0.0f));
        transform.rotateAxisAngle(Vector3f.UNIT_Y, (float) (Math.PI / 2.0));

        Vector3f forward = transform.getForward();
        Vector3f transformedPoint = transform.transformPoint(new Vector3f(0.0f, 0.0f, 1.0f));

        assertEquals(1.0f, forward.getX(), EPSILON);
        assertEquals(0.0f, forward.getY(), EPSILON);
        assertEquals(0.0f, forward.getZ(), EPSILON);
        assertEquals(2.0f, transformedPoint.getX(), EPSILON);
        assertEquals(0.0f, transformedPoint.getY(), EPSILON);
        assertEquals(0.0f, transformedPoint.getZ(), EPSILON);
    }

    @Test
    void transformAsciiSeparatesCellsFromOffsets() {
        TransformAscii transform = new TransformAscii(new Vector2i(10, 3));
        transform.translateOffset(0.25f, 0.5f);
        transform.rotateClockwise();

        assertEquals(new Vector2f(10.25f, 3.5f), transform.getWorldPosition());
        assertEquals(1, transform.getRotationQuarterTurns());
        assertEquals((float) (Math.PI / 2.0), transform.getRotationRadians(), EPSILON);
    }

    @Test
    void gameObjectAttachmentIsPropagatedToComponents() {
        GameObject gameObject = new GameObject();
        Transform2D transform = new Transform2D();

        gameObject.addComponent(transform);

        assertTrue(transform.hasGameObject());
        assertSame(gameObject, transform.getGameObject());
    }
}
