package fr.epistudio.epysia.profiling;

public record ProfileSpan(String name, int depth, long startNanos, long endNanos) {

    public long durationNanos() {
        return endNanos - startNanos;
    }
}
