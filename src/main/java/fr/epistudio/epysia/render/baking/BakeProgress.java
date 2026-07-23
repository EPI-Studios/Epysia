package fr.epistudio.epysia.render.baking;

public record BakeProgress(int completedSteps, int totalSteps, boolean finished) {

    public static BakeProgress idle() {
        return new BakeProgress(0, 0, true);
    }

    public static BakeProgress running(int completedSteps, int totalSteps) {
        return new BakeProgress(completedSteps, totalSteps, false);
    }

    public static BakeProgress done(int totalSteps) {
        return new BakeProgress(totalSteps, totalSteps, true);
    }
}
