package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.components.transforms.Transform3D;

import java.util.List;

public record ReconciliationRequest(
        Transform3D transform,
        PredictionBuffer buffer,
        InputRing inputs,
        PredictedTransform serverState,
        PredictedTransform predictedNow,
        List<PredictedMovement> movers,
        PredictedPhysics physics,
        int serverTick,
        float fixedTimestepSeconds
) {
    public ReconciliationRequest(Transform3D transform, PredictionBuffer buffer, InputRing inputs,
                                 PredictedTransform serverState, PredictedTransform predictedNow,
                                 List<PredictedMovement> movers, int serverTick,
                                 float fixedTimestepSeconds) {
        this(transform, buffer, inputs, serverState, predictedNow, movers,
                PredictedPhysics.NONE, serverTick, fixedTimestepSeconds);
    }
}
