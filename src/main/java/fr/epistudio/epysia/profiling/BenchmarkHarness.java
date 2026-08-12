package fr.epistudio.epysia.profiling;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BenchmarkHarness {
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final int warmupFrames;
    private final int measuredFrames;
    private final Map<String, Long> totals = new LinkedHashMap<>();
    private int seenFrames;
    private long measuredNanos;
    private long totalDrawCalls;
    private long totalTriangles;
    private long totalInstances;
    private long totalInstancedTriangles;

    private BenchmarkHarness(int warmupFrames, int measuredFrames) {
        this.warmupFrames = warmupFrames;
        this.measuredFrames = measuredFrames;
    }

    public static BenchmarkHarness fromSystemProperties() {
        int frames = Integer.getInteger("epysia.benchmark.frames", 0);
        if (frames <= 0) {
            return null;
        }
        return new BenchmarkHarness(Integer.getInteger("epysia.benchmark.warmup", 300), frames);
    }

    public boolean simulationFrozen() {
        return seenFrames >= warmupFrames;
    }

    public void recordFrame(long frameNanos, Map<String, Long> cpuSections, Map<String, Long> gpuSections,
                            int drawCalls, long triangles, long instances, long instancedTriangles) {
        seenFrames++;
        if (seenFrames <= warmupFrames) {
            return;
        }
        measuredNanos += frameNanos;
        totalDrawCalls += drawCalls;
        totalTriangles += triangles;
        totalInstances += instances;
        totalInstancedTriangles += instancedTriangles;
        accumulate("", cpuSections);
        accumulate("gpu.", gpuSections);
    }

    private void accumulate(String prefix, Map<String, Long> sections) {
        for (Map.Entry<String, Long> entry : sections.entrySet()) {
            totals.merge(prefix + entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    public boolean finished() {
        return seenFrames >= warmupFrames + measuredFrames;
    }

    public String report() {
        int frames = Math.max(1, seenFrames - warmupFrames);
        StringBuilder text = new StringBuilder(String.format(
                "[benchmark] %d frames apres %d de chauffe | %.3f ms/frame (%.0f fps)",
                frames, warmupFrames, measuredNanos / NANOS_PER_MILLI / frames,
                frames * 1_000_000_000.0 / Math.max(1L, measuredNanos)));
        long instancesPerFrame = totalInstances / frames;
        long instancedTris = totalInstancedTriangles / frames;
        long plainTris = totalTriangles / frames - instancedTris;
        text.append(String.format(" | draws %d | tris %d (instancies %d, simples %d) | instances %d | tris/instance %.1f",
                totalDrawCalls / frames, totalTriangles / frames, instancedTris, plainTris, instancesPerFrame,
                instancesPerFrame == 0L ? 0.0 : (double) instancedTris / instancesPerFrame));
        totals.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .forEach(entry -> text.append(String.format(" | %s %.3f", entry.getKey(),
                        entry.getValue() / NANOS_PER_MILLI / frames)));
        return text.toString();
    }
}
