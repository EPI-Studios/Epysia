package fr.epistudio.epysia.physics.api;

public record DynamicProperties(
        float mass,
        float gravityScale,
        float linearDamping,
        float angularDamping,
        boolean continuousCollisionDetection
) {

    public static DynamicProperties defaults() {
        return new DynamicProperties(1.0f, 1.0f, 0.0f, 0.0f, false);
    }
}
