package fr.epistudio.epysia.physics.api;

public record SleepState(boolean awake, float sleepTimeSeconds) {
    public static final SleepState AWAKE = new SleepState(true, 0.0f);
}
