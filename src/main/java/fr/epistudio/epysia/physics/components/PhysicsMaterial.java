package fr.epistudio.epysia.physics.components;

public record PhysicsMaterial(
        float dynamicFriction,
        float staticFriction,
        float restitution,
        CombineMode frictionCombine,
        CombineMode bounceCombine
) {

    public enum CombineMode {
        AVERAGE,
        MIN,
        MULTIPLY,
        MAX
    }

    public static final PhysicsMaterial DEFAULT =
            new PhysicsMaterial(0.5f, 0.5f, 0.0f, CombineMode.AVERAGE, CombineMode.AVERAGE);

    public int frictionCombineOrdinal() {
        return frictionCombine.ordinal();
    }

    public int bounceCombineOrdinal() {
        return bounceCombine.ordinal();
    }
}
