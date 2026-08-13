package fr.epistudio.epysia.editor.ui;

import com.sun.management.ThreadMXBean;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public final class AllocationMeter {

    private static final double BYTES_PER_MEGABYTE = 1024.0 * 1024.0;
    private static final double SMOOTHING = 0.05;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long DISCONTINUITY_NANOS = 100_000_000L;

    private final ThreadMXBean threads = resolveThreadBean();
    private final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
    private final long threadId = Thread.currentThread().threadId();

    private long previousBytes = -1L;
    private long previousNanos;
    private long baselineCollections = -1L;
    private long baselineCollectionMillis;
    private double smoothedBytesPerFrame;
    private double smoothedMegabytesPerSecond;

    private static ThreadMXBean resolveThreadBean() {
        try {
            java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            return bean instanceof ThreadMXBean detailed ? detailed : null;
        } catch (LinkageError withoutJdkManagement) {
            return null;
        }
    }

    public void sample() {
        if (threads == null) {
            return;
        }
        long bytes = threads.getThreadAllocatedBytes(threadId);
        long nanos = System.nanoTime();
        long elapsed = nanos - previousNanos;
        if (previousBytes >= 0L && elapsed > 0L && elapsed < DISCONTINUITY_NANOS) {
            accumulate(bytes - previousBytes, elapsed);
        }
        previousBytes = bytes;
        previousNanos = nanos;
        captureCollectionBaseline();
    }

    private void accumulate(long deltaBytes, long deltaNanos) {
        double perSecond = deltaBytes * (double) NANOS_PER_SECOND / deltaNanos / BYTES_PER_MEGABYTE;
        smoothedBytesPerFrame += (deltaBytes - smoothedBytesPerFrame) * SMOOTHING;
        smoothedMegabytesPerSecond += (perSecond - smoothedMegabytesPerSecond) * SMOOTHING;
    }

    private void captureCollectionBaseline() {
        if (baselineCollections < 0L) {
            baselineCollections = totalCollections();
            baselineCollectionMillis = totalCollectionMillis();
        }
    }

    public boolean available() {
        return threads != null;
    }

    public double bytesPerFrame() {
        return smoothedBytesPerFrame;
    }

    public double megabytesPerSecond() {
        return smoothedMegabytesPerSecond;
    }

    public long collectionsSinceStart() {
        return baselineCollections < 0L ? 0L : totalCollections() - baselineCollections;
    }

    public long collectionMillisSinceStart() {
        return baselineCollections < 0L ? 0L : totalCollectionMillis() - baselineCollectionMillis;
    }

    private long totalCollections() {
        long total = 0L;
        for (GarbageCollectorMXBean collector : collectors) {
            total += Math.max(collector.getCollectionCount(), 0L);
        }
        return total;
    }

    private long totalCollectionMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean collector : collectors) {
            total += Math.max(collector.getCollectionTime(), 0L);
        }
        return total;
    }
}
