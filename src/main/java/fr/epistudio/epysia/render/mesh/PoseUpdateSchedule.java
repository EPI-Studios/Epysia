package fr.epistudio.epysia.render.mesh;

final class PoseUpdateSchedule {
    private static final int[] CADENCE_BY_TIER = {1, 2, 4, 8};

    private boolean cullingEnabled =
            Boolean.parseBoolean(System.getProperty("epysia.animation.culling", "true"));
    private float fullRateDistance =
            Float.parseFloat(System.getProperty("epysia.animation.fullRateDistance", "15.0"));

    private int culledThisFrame;
    private int cadencedThisFrame;

    void beginFrame() {
        culledThisFrame = 0;
        cadencedThisFrame = 0;
    }

    boolean samplesThisFrame(boolean relevant, float distanceSquared, long frameCounter, int phase) {
        if (!cullingEnabled) {
            return true;
        }
        if (!relevant) {
            culledThisFrame++;
            return false;
        }
        int cadence = cadenceFor(distanceSquared);
        if (cadence == 1 || Math.floorMod(frameCounter + phase, cadence) == 0) {
            return true;
        }
        cadencedThisFrame++;
        return false;
    }

    private int cadenceFor(float distanceSquared) {
        if (fullRateDistance <= 0.0f) {
            return 1;
        }
        float tierDistance = fullRateDistance;
        for (int tier = 0; tier < CADENCE_BY_TIER.length - 1; tier++) {
            if (distanceSquared <= tierDistance * tierDistance) {
                return CADENCE_BY_TIER[tier];
            }
            tierDistance *= 2.0f;
        }
        return CADENCE_BY_TIER[CADENCE_BY_TIER.length - 1];
    }

    void setCullingEnabled(boolean value) {
        cullingEnabled = value;
    }

    boolean cullingEnabled() {
        return cullingEnabled;
    }

    void setFullRateDistance(float value) {
        fullRateDistance = value;
    }

    float fullRateDistance() {
        return fullRateDistance;
    }

    int culledThisFrame() {
        return culledThisFrame;
    }

    int cadencedThisFrame() {
        return cadencedThisFrame;
    }
}
