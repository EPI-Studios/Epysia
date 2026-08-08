package fr.epistudio.epysia.net;

import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.net.diagnostics.DiagnosticsServer;
import fr.epistudio.epysia.net.diagnostics.MetricsSnapshot;
import fr.epistudio.epysia.net.diagnostics.NetworkStats;
import fr.epistudio.epysia.net.diagnostics.PrometheusFormatter;
import fr.epistudio.epysia.net.transport.NetChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsEndpointTest {
    private static final String HOST = "127.0.0.1";
    private static final int BASE_PORT = 45_701;

    private final DiagnosticsServer server = new DiagnosticsServer(new ConsoleLogger());

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void healthReportsDownUntilASessionIsRunning() throws IOException, InterruptedException {
        assertTrue(server.start(HOST, BASE_PORT));
        server.publish(MetricsSnapshot.OFFLINE);
        HttpResponse<String> response = get("/health", BASE_PORT);
        assertEquals(503, response.statusCode(),
                "a server with no session must not report itself healthy to a load balancer");
        assertTrue(response.body().contains("\"status\":\"down\""));
    }

    @Test
    void healthReportsOkOnceTheSessionIsUp() throws IOException, InterruptedException {
        assertTrue(server.start(HOST, BASE_PORT + 1));
        server.publish(runningSnapshot());
        HttpResponse<String> response = get("/health", BASE_PORT + 1);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"role\":\"SERVER\""));
        assertTrue(response.body().contains("\"peers\":2"));
    }

    @Test
    void metricsRenderEveryCounterAndEveryPeer() throws IOException, InterruptedException {
        assertTrue(server.start(HOST, BASE_PORT + 2));
        server.publish(runningSnapshot());
        String body = get("/metrics", BASE_PORT + 2).body();
        assertTrue(body.contains("epysia_net_up 1"));
        assertTrue(body.contains("epysia_net_snapshots_sent_total"));
        assertTrue(body.contains("peer=\"1\""), "each peer should appear as a label");
        assertTrue(body.contains("peer=\"2\""));
    }

    @Test
    void aLabelWithQuotesCannotBreakTheExpositionFormat() {
        MetricsSnapshot snapshot = new MetricsSnapshot(true, "SERVER", 1, 5, 10L, Map.of(),
                List.of(new MetricsSnapshot.PeerMetrics(1, "he said \"hi\"", 0.05f, 0.01f)));
        String rendered = PrometheusFormatter.render(snapshot);
        assertTrue(rendered.contains("\\\"hi\\\""), "quotes in a display name must be escaped");
    }

    @Test
    void everyStatisticReachesTheCounterMap() {
        NetworkStats stats = new NetworkStats();
        stats.recordSent(NetChannel.UNRELIABLE, 100);
        stats.recordSnapshot(50);
        stats.recordRejectedInput();
        Map<String, Long> counters = stats.counters();
        assertEquals(100L, counters.get("bytes_sent_total_unreliable"));
        assertEquals(1L, counters.get("snapshots_sent_total"));
        assertEquals(1L, counters.get("rejected_inputs_total"));
    }

    private static MetricsSnapshot runningSnapshot() {
        NetworkStats stats = new NetworkStats();
        stats.recordSnapshot(120);
        return new MetricsSnapshot(true, "SERVER", 2, 900, 42L, stats.counters(),
                List.of(new MetricsSnapshot.PeerMetrics(1, "alice", 0.05f, 0.01f),
                        new MetricsSnapshot.PeerMetrics(2, "bob", 0.09f, 0.02f)));
    }

    private static HttpResponse<String> get(String path, int port) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + HOST + ":" + port + path)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
