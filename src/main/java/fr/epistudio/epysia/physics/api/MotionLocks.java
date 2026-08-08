package fr.epistudio.epysia.physics.api;

public record MotionLocks(
        boolean linearX,
        boolean linearY,
        boolean linearZ,
        boolean angularX,
        boolean angularY,
        boolean angularZ
) {
    public static final MotionLocks NONE = new MotionLocks(false, false, false, false, false, false);

    public static MotionLocks planeXY(boolean freezeRotation) {
        return new MotionLocks(false, false, true, true, true, freezeRotation);
    }
}
