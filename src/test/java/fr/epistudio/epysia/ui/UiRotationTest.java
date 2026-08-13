package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiRotationTest {

    private static final float TOLERANCE = 1.0e-3f;
    private static final UiRect SCREEN = new UiRect(0.0f, 0.0f, 800.0f, 600.0f);

    @Test
    void anUnrotatedElementReportsItself() {
        UiButton panel = panel(0.0f);

        assertFalse(panel.rotated(), "zero rotation must not take the rotated path");
    }

    @Test
    void aQuarterTurnMapsCornerToCorner() {
        UiDrawList drawList = new UiDrawList();
        drawList.pushRotation(50.0f, 50.0f, (float) Math.toRadians(90.0));
        drawList.addQuad(new UiRect(0.0f, 0.0f, 100.0f, 100.0f), 0.0f, 0.0f, 1.0f, 1.0f, UiColor.rgb(1.0f, 1.0f, 1.0f));

        float[] corner = firstVertex(drawList);

        assertEquals(100.0f, corner[0], TOLERANCE,
                "a quarter turn about the centre sends the top left corner to the top right");
        assertEquals(0.0f, corner[1], TOLERANCE, "and keeps it on the top edge");
    }

    @Test
    void popRestoresThePreviousTransform() {
        UiDrawList drawList = new UiDrawList();
        drawList.pushRotation(50.0f, 50.0f, (float) Math.toRadians(90.0));
        drawList.popTransform();
        drawList.addQuad(new UiRect(0.0f, 0.0f, 100.0f, 100.0f), 0.0f, 0.0f, 1.0f, 1.0f, UiColor.rgb(1.0f, 1.0f, 1.0f));

        float[] corner = firstVertex(drawList);

        assertEquals(0.0f, corner[0], TOLERANCE, "after a pop the quad is axis aligned again");
        assertEquals(0.0f, corner[1], TOLERANCE, "on both axes");
    }

    @Test
    void clearingResetsTheTransform() {
        UiDrawList drawList = new UiDrawList();
        drawList.pushRotation(10.0f, 10.0f, 1.0f);
        drawList.clear();

        assertFalse(drawList.isRotated(),
                "a frame must never start with a transform left over from the last one");
    }

    @Test
    void aRotatedElementIsHitWhereItAppears() {
        UiButton panel = panel(90.0f);
        panel.setSize(0.0f, 200.0f, 0.0f, 40.0f);
        panel.layout(SCREEN);

        UiRect rect = panel.computedRect();
        float centerX = rect.x() + rect.width() * 0.5f;
        float centerY = rect.y() + rect.height() * 0.5f;

        UiHit alongRotatedLength = UiHitTest.topmost(panel, centerX, centerY + 80.0f, SCREEN);
        UiHit alongOriginalLength = UiHitTest.topmost(panel, centerX + 80.0f, centerY, SCREEN);

        assertNotNull(alongRotatedLength.element(),
                "a quarter turned bar is tall, so a point above its centre must hit");
        assertNull(alongOriginalLength.element(),
                "and a point out along its old width must now miss");
    }

    @Test
    void theCentreOfARotatedElementAlwaysHits() {
        for (float degrees : new float[]{0.0f, 30.0f, 90.0f, 180.0f, 250.0f}) {
            UiButton panel = panel(degrees);
            panel.layout(SCREEN);
            UiRect rect = panel.computedRect();
            UiHit hit = UiHitTest.topmost(panel,
                    rect.x() + rect.width() * 0.5f, rect.y() + rect.height() * 0.5f, SCREEN);

            assertNotNull(hit.element(), "the centre is invariant under rotation, at " + degrees);
        }
    }

    @Test
    void rotationSurvivesASceneRoundTripAsAnExportedField() {
        UiButton panel = panel(37.5f);

        assertEquals(37.5f, panel.rotationDegrees(), TOLERANCE, "the authored value is kept");
        assertTrue(panel.rotated(), "a non zero rotation takes the rotated path");
    }

    private static float[] firstVertex(UiDrawList drawList) {
        java.nio.ByteBuffer vertices = drawList.vertexData().order(java.nio.ByteOrder.nativeOrder());
        return new float[]{vertices.getFloat(0), vertices.getFloat(4)};
    }

    private static UiButton panel(float degrees) {
        GameObject object = new GameObject("panel");
        object.addComponent(new Transform3D());
        UiButton panel = object.addComponent(new UiButton());
        panel.setSize(0.0f, 100.0f, 0.0f, 100.0f);
        panel.setRotationDegrees(degrees);
        return panel;
    }
}
