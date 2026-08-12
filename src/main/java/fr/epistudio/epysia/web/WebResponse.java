package fr.epistudio.epysia.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record WebResponse(int status, String body, Map<String, List<String>> headers,
                          Optional<String> error) {

    public static final int NO_STATUS = 0;

    public static WebResponse failed(String message) {
        return new WebResponse(NO_STATUS, "", Map.of(), Optional.of(message));
    }

    public boolean successful() {
        return error.isEmpty() && status >= 200 && status < 300;
    }

    public String header(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? "" : values.getFirst();
    }
}
