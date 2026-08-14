package fr.epistudio.epysia.profiling;

import java.util.List;

public record ProfileFrame(List<ProfileNode> roots, List<ProfileSpan> spans, int droppedSpans,
                           long startNanos, long endNanos) {

    public static final ProfileFrame EMPTY =
            new ProfileFrame(List.of(), List.of(), 0, 0L, 0L);

    public long spanNanos() {
        return endNanos - startNanos;
    }

    public long totalNanos() {
        long total = 0L;
        for (ProfileNode root : roots) {
            total += root.totalNanos();
        }
        return total;
    }
}
