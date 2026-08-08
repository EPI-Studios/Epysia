package fr.epistudio.epysia.net.diagnostics;

import java.util.List;
import java.util.Map;

public record MetricsSnapshot(
        boolean sessionActive,
        String role,
        int peerCount,
        int tick,
        long uptimeSeconds,
        Map<String, Long> counters,
        List<PeerMetrics> peers
) {
    public static final MetricsSnapshot OFFLINE = new MetricsSnapshot(false, "OFFLINE", 0, 0, 0L,
            Map.of(), List.of());

    public record PeerMetrics(int id, String displayName, float roundTripSeconds, float jitterSeconds) {
    }
}
