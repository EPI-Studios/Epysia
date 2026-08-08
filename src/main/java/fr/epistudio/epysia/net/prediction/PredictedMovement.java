package fr.epistudio.epysia.net.prediction;

public interface PredictedMovement {
    void simulatePredictedStep(InputSample input, float deltaTimeSeconds);
}
