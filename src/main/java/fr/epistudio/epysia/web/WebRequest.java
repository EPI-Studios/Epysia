package fr.epistudio.epysia.web;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WebRequest {

    private static final float DEFAULT_TIMEOUT_SECONDS = 15.0f;

    private final String method;
    private final String url;
    private final Map<String, String> headers = new LinkedHashMap<>();

    private String body = "";
    private float timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    private WebRequest(String method, String url) {
        this.method = method;
        this.url = url;
    }

    public static WebRequest get(String url) {
        return new WebRequest("GET", url);
    }

    public static WebRequest post(String url) {
        return new WebRequest("POST", url);
    }

    public static WebRequest put(String url) {
        return new WebRequest("PUT", url);
    }

    public static WebRequest delete(String url) {
        return new WebRequest("DELETE", url);
    }

    public WebRequest header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public WebRequest json(String payload) {
        body = payload;
        return header("Content-Type", "application/json");
    }

    public WebRequest body(String payload) {
        body = payload;
        return this;
    }

    public WebRequest timeoutSeconds(float seconds) {
        timeoutSeconds = Math.max(0.1f, seconds);
        return this;
    }

    public String method() {
        return method;
    }

    public String url() {
        return url;
    }

    public String bodyText() {
        return body;
    }

    public float timeout() {
        return timeoutSeconds;
    }

    public Map<String, String> headerMap() {
        return Map.copyOf(headers);
    }
}
