package fr.epistudio.epysia.render;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObliqueCullingTest {

    private static Camera3D cameraAtOrigin() {
        GameObject owner = new GameObject("Camera");
        owner.addComponent(new Transform3D());
        Camera3D camera = owner.addComponent(new Camera3D());
        camera.setNearFar(0.1f, 100.0f);
        camera.setAspectRatio(16.0f / 9.0f);
        return camera;
    }

    @Test
    void cullingKeepsTheDerivedFrustumWhenTheProjectionIsOverridden() {
        Camera3D camera = cameraAtOrigin();
        Matrix4f derived = new Matrix4f(camera.cullingViewProjection(1.0f));

        Vector4f portalPlane = new Vector4f(0.0f, 0.0f, 1.0f, 5.0f);
        camera.setProjection(ObliqueProjection.withObliqueNearPlane(
                camera.projection(), portalPlane, new Matrix4f()));

        assertNotEquals(camera.projection(), camera.cullingProjection());
        assertTrue(derived.equals(camera.cullingViewProjection(1.0f), 1.0e-5f),
                "culling must keep using the unmodified frustum");
    }

    @Test
    void theObliqueFrustumDiscardsGeometryInFrontOfThePortalPlane() {
        Camera3D camera = cameraAtOrigin();
        Vector4f portalPlane = new Vector4f(0.0f, 0.0f, 1.0f, 5.0f);
        Matrix4f oblique = ObliqueProjection.withObliqueNearPlane(
                camera.projection(), portalPlane, new Matrix4f());

        FrustumIntersection derivedFrustum =
                new FrustumIntersection(new Matrix4f(camera.cullingViewProjection(1.0f)));
        FrustumIntersection obliqueFrustum =
                new FrustumIntersection(new Matrix4f(oblique).mul(camera.view(1.0f)));

        assertTrue(derivedFrustum.testAab(-0.5f, -0.5f, -2.0f, 0.5f, 0.5f, -1.0f));
        assertFalse(obliqueFrustum.testAab(-0.5f, -0.5f, -2.0f, 0.5f, 0.5f, -1.0f),
                "the two frustums genuinely differ, so which one culling reads is a real decision");
        assertTrue(derivedFrustum.testAab(-0.5f, -0.5f, -21.0f, 0.5f, 0.5f, -20.0f));
        assertTrue(obliqueFrustum.testAab(-0.5f, -0.5f, -21.0f, 0.5f, 0.5f, -20.0f),
                "beyond the portal plane both agree, so the far plane is not the failure mode");
    }
}
