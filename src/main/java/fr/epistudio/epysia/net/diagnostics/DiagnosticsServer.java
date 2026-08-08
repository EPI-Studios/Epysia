package fr.epistudio.epysia.net.diagnostics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.epistudio.epysia.logging.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class DiagnosticsServer {
    private static final String HEALTH_PATH = "/health";
    private static final String METRICS_PATH = "/metrics";
    private static final int STOP_DELAY_SECONDS = 0;
    private static final int HEALTHY = 200;
    private static final int UNHEALTHY = 503;

    private final AtomicReference<MetricsSnapshot> latest = new AtomicReference<>(MetricsSnapshot.OFFLINE);
    private final Logger logger;
    private HttpServer server;

    public DiagnosticsServer(Logger logger) {
        this.logger = logger;
    }

    public void publish(MetricsSnapshot snapshot) {
        latest.set(snapshot);
    }

    public boolean start(String host, int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext(HEALTH_PATH, this::serveHealth);
            server.createContext(METRICS_PATH, this::serveMetrics);
            server.start();
            logger.info("[net.diagnostics] health and metrics on http://" + host + ":" + port + HEALTH_PATH);
            return true;
        } catch (IOException failure) {
            logger.error("[net.diagnostics] could not bind " + host + ":" + port, failure);
            return false;
        }
    }

    private void serveHealth(HttpExchange exchange) throws IOException {
        MetricsSnapshot snapshot = latest.get();
        String body = "{\"status\":\"" + (snapshot.sessionActive() ? "ok" : "down")
                + "\",\"role\":\"" + snapshot.role()
                + "\",\"peers\":" + snapshot.peerCount()
                + ",\"tick\":" + snapshot.tick()
                + ",\"uptimeSeconds\":" + snapshot.uptimeSeconds() + "}";
        respond(exchange, snapshot.sessionActive() ? HEALTHY : UNHEALTHY, "application/json", body);
    }

    private void serveMetrics(HttpExchange exchange) throws IOException {
        respond(exchange, HEALTHY, "text/plain; version=0.0.4",
                PrometheusFormatter.render(latest.get()));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    public void stop() {
        if (server == null) {
            return;
        }
        server.stop(STOP_DELAY_SECONDS);
        server = null;
    }
}
