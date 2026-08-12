package fr.epistudio.epysia.debug;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugDrawTest {

    private static final int WHITE = 0xFFFFFF;
    private static final Vector3f ORIGIN = new Vector3f(0.0f, 0.0f, 0.0f);
    private static final Vector3f UNIT_Y = new Vector3f(0.0f, 1.0f, 0.0f);

    @Test
    void aOneFrameLineSurvivesUntilTheNextAdvance() {
        DebugDraw debugDraw = new DebugDraw();
        debugDraw.line(ORIGIN, UNIT_Y, WHITE);

        assertEquals(1, debugDraw.segmentCount(), "the line must be visible on the frame it was asked for");

        debugDraw.advance(1.0f / 60.0f);

        assertEquals(0, debugDraw.segmentCount(), "a one frame line must not survive a second tick");
    }

    @Test
    void aTimedLineOutlivesSeveralTicksAndThenExpires() {
        DebugDraw debugDraw = new DebugDraw();
        debugDraw.line(ORIGIN, UNIT_Y, WHITE, 0.1f);

        debugDraw.advance(0.04f);
        assertEquals(1, debugDraw.segmentCount(), "a timed line must survive while time remains");

        debugDraw.advance(0.04f);
        assertEquals(1, debugDraw.segmentCount(), "a timed line must still survive before its budget runs out");

        debugDraw.advance(0.04f);
        assertEquals(0, debugDraw.segmentCount(), "a timed line must expire once its budget is spent");
    }

    @Test
    void disablingDropsEverythingAndRefusesNewWork() {
        DebugDraw debugDraw = new DebugDraw();
        debugDraw.line(ORIGIN, UNIT_Y, WHITE, 10.0f);

        debugDraw.setEnabled(false);
        assertEquals(0, debugDraw.segmentCount(), "disabling must drop what was already queued");

        debugDraw.line(ORIGIN, UNIT_Y, WHITE, 10.0f);
        assertEquals(0, debugDraw.segmentCount(), "a disabled overlay must ignore new lines");
    }

    @Test
    void aBoxIsTwelveEdgesAndSurvivesGrowingTheBuffer() {
        DebugDraw debugDraw = new DebugDraw();
        debugDraw.box(ORIGIN, new Vector3f(1.0f, 1.0f, 1.0f), WHITE);

        assertEquals(12, debugDraw.segmentCount(), "a box is twelve edges");

        for (int index = 0; index < 4096; index++) {
            debugDraw.line(ORIGIN, UNIT_Y, WHITE);
        }

        assertEquals(12 + 4096, debugDraw.segmentCount(), "the segment store must grow instead of dropping work");
        assertTrue(debugDraw.endpoints().length >= debugDraw.segmentCount() * 6,
                "the endpoint store must stay large enough for every segment");
    }

    @Test
    void theDetachedOverlayNeverRecordsAnything() {
        DebugDraw.detached().line(ORIGIN, UNIT_Y, WHITE, 10.0f);

        assertEquals(0, DebugDraw.detached().segmentCount(),
                "the detached overlay stands in for headless services and must stay empty");
    }
}
