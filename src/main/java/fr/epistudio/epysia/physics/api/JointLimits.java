package fr.epistudio.epysia.physics.api;

public record JointLimits(boolean enabled, float lower, float upper) {
    public static final JointLimits DISABLED = new JointLimits(false, 0.0f, 0.0f);

    public static JointLimits between(float lower, float upper) {
        return new JointLimits(true, Math.min(lower, upper), Math.max(lower, upper));
    }
}
