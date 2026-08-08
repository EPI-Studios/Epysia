package fr.epistudio.epysia.net.diagnostics;

import java.util.Map;

public final class PrometheusFormatter {
    private static final String PREFIX = "epysia_net_";

    private PrometheusFormatter() {
    }

    public static String render(MetricsSnapshot snapshot) {
        StringBuilder output = new StringBuilder();
        gauge(output, "up", snapshot.sessionActive() ? 1L : 0L);
        gauge(output, "peers", snapshot.peerCount());
        gauge(output, "tick", snapshot.tick());
        gauge(output, "uptime_seconds", snapshot.uptimeSeconds());
        for (Map.Entry<String, Long> counter : snapshot.counters().entrySet()) {
            gauge(output, counter.getKey(), counter.getValue());
        }
        for (MetricsSnapshot.PeerMetrics peer : snapshot.peers()) {
            peerGauge(output, "peer_round_trip_seconds", peer, peer.roundTripSeconds());
            peerGauge(output, "peer_jitter_seconds", peer, peer.jitterSeconds());
        }
        return output.toString();
    }

    private static void gauge(StringBuilder output, String name, long value) {
        output.append(PREFIX).append(name).append(' ').append(value).append('\n');
    }

    private static void peerGauge(StringBuilder output, String name,
                                  MetricsSnapshot.PeerMetrics peer, float value) {
        output.append(PREFIX).append(name)
                .append("{peer=\"").append(peer.id())
                .append("\",name=\"").append(escape(peer.displayName()))
                .append("\"} ").append(value).append('\n');
    }

    private static String escape(String label) {
        return label.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
