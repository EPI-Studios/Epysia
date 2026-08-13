package fr.epistudio.epysia.scripting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledSequenceTest {

    private static final float STEP = 1.0f / 60.0f;

    @Test
    void stepsRunInOrderAndOnlyAfterTheirOwnDelay() {
        DefaultScheduler scheduler = new DefaultScheduler();
        List<String> seen = new ArrayList<>();
        scheduler.sequence()
                .then(0.1f, () -> seen.add("first"))
                .then(0.1f, () -> seen.add("second"))
                .start();

        advance(scheduler, 0.15f);
        assertEquals(List.of("first"), seen, "the second step must wait for its own delay");

        advance(scheduler, 0.15f);
        assertEquals(List.of("first", "second"), seen, "the steps must run in order");
    }

    @Test
    void aSequenceDoesNothingUntilItIsStarted() {
        DefaultScheduler scheduler = new DefaultScheduler();
        List<String> seen = new ArrayList<>();
        scheduler.sequence().then(0.01f, () -> seen.add("ran"));

        advance(scheduler, 0.5f);

        assertTrue(seen.isEmpty(), "a sequence that was never started must not run");
    }

    @Test
    void cancellingStopsTheStepsThatHaveNotRunYet() {
        DefaultScheduler scheduler = new DefaultScheduler();
        List<String> seen = new ArrayList<>();
        ScheduledSequence sequence = scheduler.sequence()
                .then(0.1f, () -> seen.add("first"))
                .then(0.1f, () -> seen.add("second"))
                .start();

        advance(scheduler, 0.15f);
        sequence.cancel();
        advance(scheduler, 0.5f);

        assertEquals(List.of("first"), seen, "cancelling must stop the remaining steps");
        assertFalse(sequence.isPending(), "a cancelled sequence must stop reporting itself pending");
    }

    @Test
    void aFinishedSequenceIsNoLongerPending() {
        DefaultScheduler scheduler = new DefaultScheduler();
        ScheduledSequence sequence = scheduler.sequence()
                .then(0.05f, () -> {
                })
                .start();

        advance(scheduler, 0.3f);

        assertFalse(sequence.isPending(), "a sequence that ran every step is finished");
    }

    private static void advance(DefaultScheduler scheduler, float seconds) {
        for (float elapsed = 0.0f; elapsed < seconds; elapsed += STEP) {
            scheduler.tick(STEP);
        }
    }
}
