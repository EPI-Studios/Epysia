package fr.epistudio.epysia.render.mesh;

public final class ShadowStatistics {
    private int targetsRendered;
    private int targetsSkipped;
    private int castersSubmitted;
    private int animatedCasters;
    private int culledCasters;
    private int staticLayersRebuilt;
    private int staticLayersScrolled;
    private int scrolledCastersDrawn;
    private int dynamicCastersDrawn;
    private int depthCopies;

    void beginFrame() {
        targetsRendered = 0;
        targetsSkipped = 0;
        castersSubmitted = 0;
        animatedCasters = 0;
        culledCasters = 0;
        staticLayersRebuilt = 0;
        staticLayersScrolled = 0;
        scrolledCastersDrawn = 0;
        dynamicCastersDrawn = 0;
        depthCopies = 0;
    }

    void recordStaticLayerRebuild() {
        staticLayersRebuilt++;
    }

    void recordStaticLayerScroll() {
        staticLayersScrolled++;
    }

    void recordScrolledCaster() {
        scrolledCastersDrawn++;
    }

    public int staticLayersScrolled() {
        return staticLayersScrolled;
    }

    public int scrolledCastersDrawn() {
        return scrolledCastersDrawn;
    }

    void recordDynamicCasters(int count) {
        dynamicCastersDrawn += count;
    }

    void recordDepthCopy() {
        depthCopies++;
    }

    public int staticLayersRebuilt() {
        return staticLayersRebuilt;
    }

    public int dynamicCastersDrawn() {
        return dynamicCastersDrawn;
    }

    public int depthCopies() {
        return depthCopies;
    }

    void recordTarget(boolean rendered) {
        if (rendered) {
            targetsRendered++;
        } else {
            targetsSkipped++;
        }
    }

    void recordCasters(int count) {
        castersSubmitted += count;
    }

    void recordAnimatedCaster() {
        animatedCasters++;
    }

    void recordCulledCaster() {
        culledCasters++;
    }

    public int culledCasters() {
        return culledCasters;
    }

    public int targetsRendered() {
        return targetsRendered;
    }

    public int targetsSkipped() {
        return targetsSkipped;
    }

    public int castersSubmitted() {
        return castersSubmitted;
    }

    public int animatedCasters() {
        return animatedCasters;
    }
}
