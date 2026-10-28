package de.codepath.backend.engine;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Spricht den {@code runner}-Dienst an (ADR-4, runner/runner.py). Ist er nicht erreichbar, liefert
 * jeder Lauf UNAVAILABLE – die Abgabe wird dann gespeichert, aber nicht bewertet.
 */
@Slf4j
public class HttpCodeRunner implements CodeRunner {

    private static final Duration HEALTH_TTL = Duration.ofSeconds(30);
    // Warteschlange im Runner (10 s) plus Laufzeit (5 s) plus Puffer
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(20);

    private final URI baseUri;
    private final String token;
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private volatile boolean healthy;
    private volatile long checkedAt;

    public HttpCodeRunner(String url, String token, ObjectMapper objectMapper) {
        this.baseUri = URI.create(url.endsWith("/") ? url : url + "/");
        this.token = token;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean available() {
        long now = System.nanoTime();
        if (checkedAt == 0 || now - checkedAt > HEALTH_TTL.toNanos()) {
            boolean ok;
            try {
                HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("health")).timeout(Duration.ofSeconds(10)).GET().build();
                ok = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                ok = false;
            }
            if (ok != healthy || checkedAt == 0) {
                log.info(ok ? "Code-Runner erreichbar: {}" : "Code-Runner nicht erreichbar: {}", baseUri);
            }
            healthy = ok;
            checkedAt = now;
        }
        return healthy;
    }

    @Override
    public RunResult run(String code, String stdin) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("code", code, "stdin", stdin == null ? "" : stdin));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("run"))
                    .timeout(RUN_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Code-Runner antwortet mit {}", response.statusCode());
                return unavailable();
            }
            Map<?, ?> result = objectMapper.readValue(response.body(), Map.class);
            RunResult.Status status = switch (String.valueOf(result.get("status"))) {
                case "OK" -> RunResult.Status.OK;
                case "TIMEOUT" -> RunResult.Status.TIMEOUT;
                default -> RunResult.Status.ERROR;
            };
            return new RunResult(status, text(result.get("stdout")), text(result.get("stderr")));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Code-Runner nicht erreichbar: {}", e.toString());
            checkedAt = 0;
            return unavailable();
        }
    }

    private static RunResult unavailable() {
        return new RunResult(RunResult.Status.UNAVAILABLE, "", "");
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
