package fr.epistudio.epysia.editor.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphCanvasZoomTest {

    private static final float POSITION_TOLERANCE = 0.01f;
    private static final float PIXEL_TOLERANCE = 0.05f;
    private static final float[] AUTHORED_POSITIONS = {-1840.0f, -320.5f, 0.0f, 47.25f, 960.0f, 4200.0f};

    @Test
    void authoredPositionsSurviveZoomInZoomOutAndSave() {
        GraphCanvasZoom zoom = new GraphCanvasZoom();
        float[] authored = AUTHORED_POSITIONS.clone();
        float[] stored = authored.clone();
        for (int step = 0; step < 12; step++) {
            zoom.stepBy(1.0f);
            stored = roundTrip(zoom, stored);
        }
        for (int step = 0; step < 24; step++) {
            zoom.stepBy(-1.0f);
            stored = roundTrip(zoom, stored);
        }
        for (int index = 0; index < authored.length; index++) {
            assertEquals(authored[index], stored[index], POSITION_TOLERANCE);
        }
    }

    @Test
    void resetReturnsToFullScaleAfterClampedZooming() {
        GraphCanvasZoom zoom = new GraphCanvasZoom();
        zoom.stepBy(200.0f);
        assertEquals(GraphCanvasZoom.MAXIMUM_FACTOR, zoom.factor(), 1.0e-5f);
        zoom.stepBy(-400.0f);
        assertEquals(GraphCanvasZoom.MINIMUM_FACTOR, zoom.factor(), 1.0e-5f);
        zoom.reset();
        assertTrue(zoom.atDefault());
        assertEquals(100, zoom.percentage());
    }

    @Test
    void anchoringKeepsTheGraphPointUnderTheCursorFixed() {
        float origin = 220.0f;
        float panning = -145.0f;
        float cursor = 613.0f;
        float previousFactor = 1.0f;
        float newFactor = 0.42f;
        float logical = GraphCanvasZoom.logicalFromScreen(cursor, origin, panning, previousFactor);
        float anchored = GraphCanvasZoom.anchoredPanning(cursor, origin, panning,
                previousFactor, newFactor);
        assertEquals(cursor, GraphCanvasZoom.screenFromLogical(logical, origin, anchored, newFactor),
                PIXEL_TOLERANCE);
    }

    @Test
    void anchoringIsReversibleAcrossRepeatedNotches() {
        GraphCanvasZoom zoom = new GraphCanvasZoom();
        float origin = -40.0f;
        float cursor = 512.0f;
        float panning = 96.0f;
        float logical = GraphCanvasZoom.logicalFromScreen(cursor, origin, panning, zoom.factor());
        for (int step = 0; step < 9; step++) {
            float previousFactor = zoom.factor();
            zoom.stepBy(step % 2 == 0 ? 1.0f : -2.0f);
            panning = GraphCanvasZoom.anchoredPanning(cursor, origin, panning,
                    previousFactor, zoom.factor());
        }
        assertEquals(cursor, GraphCanvasZoom.screenFromLogical(logical, origin, panning, zoom.factor()),
                PIXEL_TOLERANCE);
    }

    @Test
    void screenToGraphConversionLandsOnTheExpectedLogicalPoint() {
        float origin = 100.0f;
        float panning = 50.0f;
        float factor = 0.5f;
        assertEquals(200.0f, GraphCanvasZoom.logicalFromScreen(250.0f, origin, panning, factor),
                PIXEL_TOLERANCE);
        assertEquals(250.0f, GraphCanvasZoom.screenFromLogical(200.0f, origin, panning, factor),
                PIXEL_TOLERANCE);
        assertEquals(-100.0f, GraphCanvasZoom.logicalFromScreen(100.0f, origin, panning, factor),
                PIXEL_TOLERANCE);
    }

    @Test
    void aPaletteDropLandsUnderTheCursorAtNonUnitZoom() {
        GraphCanvasZoom zoom = new GraphCanvasZoom();
        zoom.setFactor(0.65f);
        float origin = 310.0f;
        float panning = -880.0f;
        float dropX = 742.0f;
        float authored = GraphCanvasZoom.logicalFromScreen(dropX, origin, panning, zoom.factor());
        float pushedGridPosition = zoom.scaled(authored);
        assertEquals(dropX, origin + panning + pushedGridPosition, PIXEL_TOLERANCE);
        assertEquals(authored, zoom.unscaled(pushedGridPosition), POSITION_TOLERANCE);
    }

    @Test
    void fittingCentersTheBoundsAndKeepsThemInsideTheViewport() {
        float viewportWidth = 900.0f;
        float viewportHeight = 600.0f;
        float minimumX = -400.0f;
        float maximumX = 1200.0f;
        float minimumY = 120.0f;
        float maximumY = 980.0f;
        float factor = GraphCanvasZoom.fitFactor(maximumX - minimumX, maximumY - minimumY,
                viewportWidth, viewportHeight);
        float panningX = GraphCanvasZoom.centeringPanning((minimumX + maximumX) * 0.5f,
                viewportWidth, factor);
        float panningY = GraphCanvasZoom.centeringPanning((minimumY + maximumY) * 0.5f,
                viewportHeight, factor);
        assertScreenSpan(minimumX, maximumX, panningX, factor, viewportWidth);
        assertScreenSpan(minimumY, maximumY, panningY, factor, viewportHeight);
    }

    @Test
    void fittingATinyGraphNeverExceedsTheMaximumFactor() {
        float factor = GraphCanvasZoom.fitFactor(4.0f, 4.0f, 1600.0f, 900.0f);
        assertEquals(GraphCanvasZoom.MAXIMUM_FACTOR, factor, 1.0e-5f);
    }

    private static void assertScreenSpan(float minimum, float maximum, float panning,
                                         float factor, float viewportExtent) {
        float low = GraphCanvasZoom.screenFromLogical(minimum, 0.0f, panning, factor);
        float high = GraphCanvasZoom.screenFromLogical(maximum, 0.0f, panning, factor);
        assertTrue(low >= 0.0f, "low=" + low);
        assertTrue(high <= viewportExtent, "high=" + high);
        assertEquals(viewportExtent * 0.5f, (low + high) * 0.5f, PIXEL_TOLERANCE);
    }

    private static float[] roundTrip(GraphCanvasZoom zoom, float[] positions) {
        float[] result = new float[positions.length];
        for (int index = 0; index < positions.length; index++) {
            result[index] = zoom.unscaled(zoom.scaled(positions[index]));
        }
        return result;
    }
}
