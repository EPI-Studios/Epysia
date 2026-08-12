package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.components.transforms.Transform3D;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

public final class Reconciler {
    private static final float IGNORED_ERROR_METERS = 0.002f;
    private static final float SOFT_CORRECTION_METERS = 0.25f;
    private static final float EASE_FRACTION_PER_TICK = 0.25f;
    private static final int MAXIMUM_SKEW_TICKS = 3;

    private final Vector3f correction = new Vector3f();

    public int reconcile(ReconciliationRequest request) {
        Optional<PredictedTransform> predicted = request.buffer().at(request.serverTick())
                .or(() -> request.buffer().nearestAtOrBefore(request.serverTick(), MAXIMUM_SKEW_TICKS));
        if (predicted.isEmpty()) {
            float driftNow = request.predictedNow().distanceTo(request.serverState());
            if (driftNow <= SOFT_CORRECTION_METERS) {
                return 0;
            }
            return applyServerState(request);
        }
        float error = predicted.get().distanceTo(request.serverState());
        if (error <= IGNORED_ERROR_METERS) {
            request.buffer().forgetThrough(request.serverTick());
            return 0;
        }
        if (error <= SOFT_CORRECTION_METERS) {
            easeTowards(request);
            return 0;
        }
        return applyServerState(request);
    }

    private void easeTowards(ReconciliationRequest request) {
        Transform3D transform = request.transform();
        request.serverState().position().sub(request.predictedNow().position(), correction);
        correction.mul(EASE_FRACTION_PER_TICK);
        transform.translate(correction.x, correction.y, correction.z);
        request.buffer().forgetThrough(request.serverTick());
    }

    private int applyServerState(ReconciliationRequest request) {
        request.serverState().applyTo(request.transform());
        List<InputSample> pending = request.inputs().after(request.serverTick());
        request.physics().beginReplay();
        try {
            for (InputSample sample : pending) {
                replayOne(request, sample);
            }
        } finally {
            request.physics().endReplay();
        }
        request.buffer().forgetThrough(request.serverTick());
        return pending.size();
    }

    private void replayOne(ReconciliationRequest request, InputSample sample) {
        for (PredictedMovement mover : request.movers()) {
            mover.simulatePredictedStep(sample, request.fixedTimestepSeconds());
        }
        request.physics().stepReplay(request.fixedTimestepSeconds());
        request.buffer().record(sample.tick(), PredictedTransform.capturedFrom(request.transform()));
    }
}
