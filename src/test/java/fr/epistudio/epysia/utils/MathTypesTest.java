package fr.epistudio.epysia.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
