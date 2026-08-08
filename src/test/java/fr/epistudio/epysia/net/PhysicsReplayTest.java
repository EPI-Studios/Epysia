package fr.epistudio.epysia.net;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.net.prediction.InputRing;
import fr.epistudio.epysia.net.prediction.InputSample;
import fr.epistudio.epysia.net.prediction.PredictedMovement;
import fr.epistudio.epysia.net.prediction.PredictedPhysics;
import fr.epistudio.epysia.net.prediction.PredictedTransform;
import fr.epistudio.epysia.net.prediction.PredictionBuffer;
import fr.epistudio.epysia.net.prediction.ReconciliationRequest;
import fr.epistudio.epysia.net.prediction.Reconciler;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhysicsReplayTest {
    private static final float FIXED_STEP = 1.0f / 60.0f;
    private static final int UNACKED_TICKS = 6;
    private static final int SERVER_TICK = 10;
    private static final float FAR_ENOUGH_TO_SNAP = 5.0f;

    private final InputRing inputs = new InputRing();
    private final PredictionBuffer buffer = new PredictionBuffer();
    private final Reconciler reconciler = new Reconciler();
    private final RecordingPhysics physics = new RecordingPhysics();

    @Test
    void aHardCorrectionStepsPhysicsOncePerReplayedInput() {
        Transform3D transform = seedPredictionAndInputs();
        int replayed = reconciler.reconcile(request(transform, new Vector3f(FAR_ENOUGH_TO_SNAP, 0.0f, 0.0f)));
        assertEquals(UNACKED_TICKS, replayed);
        assertEquals(UNACKED_TICKS, physics.steps);
        assertEquals(1, physics.begins);
        assertEquals(1, physics.ends);
    }

    @Test
    void replayLeavesTheReplayWindowClosedEvenIfAMoverThrows() {
        Transform3D transform = seedPredictionAndInputs();
        List<PredictedMovement> throwing = List.of((input, delta) -> {
            throw new IllegalStateException("mover failed");
        });
        try {
            reconciler.reconcile(new ReconciliationRequest(transform, buffer, inputs,
                    new PredictedTransform(new Vector3f(FAR_ENOUGH_TO_SNAP, 0.0f, 0.0f), new Quaternionf()),
                    PredictedTransform.capturedFrom(transform), throwing, physics, SERVER_TICK, FIXED_STEP));
        } catch (IllegalStateException expected) {
            assertEquals(1, physics.ends, "the replay window must close even when a mover throws");
            return;
        }
        assertTrue(false, "the mover was expected to throw");
    }

    @Test
    void aSmallErrorNeverTouchesPhysics() {
        Transform3D transform = seedPredictionAndInputs();
        reconciler.reconcile(request(transform, new Vector3f(0.01f, 0.0f, 0.0f)));
        assertEquals(0, physics.steps);
        assertEquals(0, physics.begins);
    }

    private Transform3D seedPredictionAndInputs() {
        Transform3D transform = new Transform3D();
        buffer.record(SERVER_TICK, PredictedTransform.capturedFrom(transform));
        for (int offset = 1; offset <= UNACKED_TICKS; offset++) {
            inputs.push(InputSample.empty(SERVER_TICK + offset, 2));
        }
        return transform;
    }

    private ReconciliationRequest request(Transform3D transform, Vector3f serverPosition) {
        return new ReconciliationRequest(transform, buffer, inputs,
                new PredictedTransform(serverPosition, new Quaternionf()),
                PredictedTransform.capturedFrom(transform), new ArrayList<>(), physics,
                SERVER_TICK, FIXED_STEP);
    }

    private static final class RecordingPhysics implements PredictedPhysics {
        private int begins;
        private int steps;
        private int ends;

        @Override
        public void beginReplay() {
            begins++;
        }

        @Override
        public void stepReplay(float deltaTimeSeconds) {
            steps++;
        }

        @Override
        public void endReplay() {
            ends++;
        }
    }
}
