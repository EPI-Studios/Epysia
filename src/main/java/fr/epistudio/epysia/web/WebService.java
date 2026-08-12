package fr.epistudio.epysia.web;

import fr.epistudio.epysia.concurrent.BackgroundTask;
import fr.epistudio.epysia.concurrent.BackgroundTasks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class WebService {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static HttpClient sharedClient;

    private final BackgroundTasks tasks;

    public WebService(BackgroundTasks tasks) {
        this.tasks = tasks;
    }

    public BackgroundTask<WebResponse> get(String url, Consumer<WebResponse> onCompleted) {
        return send(WebRequest.get(url), onCompleted);
    }

    public BackgroundTask<WebResponse> post(String url, String body, Consumer<WebResponse> onCompleted) {
        return send(WebRequest.post(url).json(body), onCompleted);
    }

    public BackgroundTask<WebResponse> send(WebRequest request, Consumer<WebResponse> onCompleted) {
        return tasks.submit(() -> execute(request), onCompleted);
    }

    private static WebResponse execute(WebRequest request) {
        try {
            HttpResponse<String> response = client().send(build(request),
                    HttpResponse.BodyHandlers.ofString());
            return new WebResponse(response.statusCode(), response.body(),
                    response.headers().map(), Optional.empty());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return WebResponse.failed("request interrupted");
        } catch (RuntimeException | java.io.IOException failure) {
            return WebResponse.failed(String.valueOf(failure.getMessage()));
        }
    }

    private static HttpRequest build(WebRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url()))
                .timeout(Duration.ofNanos((long) (request.timeout() * NANOS_PER_SECOND)))
                .method(request.method(), bodyPublisher(request));
        for (Map.Entry<String, String> header : request.headerMap().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    private static HttpRequest.BodyPublisher bodyPublisher(WebRequest request) {
        return request.bodyText().isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.bodyText());
    }

    private static synchronized HttpClient client() {
        if (sharedClient == null) {
            sharedClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return sharedClient;
    }
}
