package fr.epistudio.epysia.render.backend;

public final class DrawStatistics {

    private static final int TRIANGLE_VERTEX_COUNT = 3;
    private static final int STRIP_PRIMING_VERTEX_COUNT = 2;

    private int drawCalls;
    private int instancedDrawCalls;
    private int pipelineSwitches;
    private int bindingSetSwitches;
    private int passes;
    private long triangles;
    private long instances;

    public void reset() {
        drawCalls = 0;
        instancedDrawCalls = 0;
        pipelineSwitches = 0;
        bindingSetSwitches = 0;
        passes = 0;
        triangles = 0L;
        instances = 0L;
    }

    public void recordPass() {
        passes++;
    }

    public void recordPipelineSwitch() {
        pipelineSwitches++;
    }

    public void recordBindingSetSwitch() {
        bindingSetSwitches++;
    }

    public void recordDraw(Topology topology, int indexCount, int instanceCount, boolean instanced) {
        drawCalls++;
        if (instanced) {
            instancedDrawCalls++;
            instances += Math.max(1, instanceCount);
        }
        triangles += (long) primitiveCount(topology, indexCount) * Math.max(1, instanceCount);
    }

    private static int primitiveCount(Topology topology, int indexCount) {
        return switch (topology) {
            case TRIANGLES -> indexCount / TRIANGLE_VERTEX_COUNT;
            case TRIANGLE_STRIP -> Math.max(0, indexCount - STRIP_PRIMING_VERTEX_COUNT);
            case LINES -> 0;
        };
    }

    public int drawCalls() {
        return drawCalls;
    }

    public int instancedDrawCalls() {
        return instancedDrawCalls;
    }

    public int pipelineSwitches() {
        return pipelineSwitches;
    }

    public int bindingSetSwitches() {
        return bindingSetSwitches;
    }

    public int passes() {
        return passes;
    }

    public long triangles() {
        return triangles;
    }

    public long instances() {
        return instances;
    }
}
