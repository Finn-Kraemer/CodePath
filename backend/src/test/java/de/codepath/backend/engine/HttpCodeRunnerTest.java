package de.codepath.backend.engine;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpCodeRunnerTest {

    static final String TOKEN = "test-token-1234567890";

    HttpServer server;
    final AtomicReference<String> lastBody = new AtomicReference<>();
    final AtomicReference<String> lastAuth = new AtomicReference<>();
    volatile int runStatus = 200;
    volatile String runResponse = "{\"status\":\"OK\",\"stdout\":\"42\\n\",\"stderr\":\"\",\"durationMs\":3}";

    @BeforeEach
    void startFakeRunner() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/run", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = runResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(runStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    HttpCodeRunner runner() {
        return new HttpCodeRunner("http://127.0.0.1:" + server.getAddress().getPort(), TOKEN, JsonMapper.builder().build());
    }

    @Test
    void sendsCodeWithTokenAndMapsResult() {
        HttpCodeRunner runner = runner();
        assertTrue(runner.available());
        CodeRunner.RunResult result = runner.run("print(6*7)", "x");
        assertEquals(CodeRunner.RunResult.Status.OK, result.status());
        assertEquals("42\n", result.stdout());
        assertEquals("Bearer " + TOKEN, lastAuth.get());
        assertTrue(lastBody.get().contains("print(6*7)"));

        runResponse = "{\"status\":\"TIMEOUT\",\"stdout\":\"\",\"stderr\":\"\"}";
        assertEquals(CodeRunner.RunResult.Status.TIMEOUT, runner.run("while True: pass", "").status());
    }

    @Test
    void failuresMeanUnavailableNotWrong() {
        HttpCodeRunner runner = runner();
        runStatus = 503;
        assertEquals(CodeRunner.RunResult.Status.UNAVAILABLE, runner.run("print(1)", "").status());
        server.stop(0);
        assertEquals(CodeRunner.RunResult.Status.UNAVAILABLE, runner.run("print(1)", "").status());
        assertFalse(runner.available());
    }

    /** Gegen einen echten Runner (CI): CODEPATH_RUNNER_URL und CODEPATH_RUNNER_TOKEN setzen. */
    @Test
    @EnabledIfEnvironmentVariable(named = "CODEPATH_RUNNER_URL", matches = ".+")
    void realRunnerSandboxesCode() {
        HttpCodeRunner real = new HttpCodeRunner(System.getenv("CODEPATH_RUNNER_URL"), System.getenv("CODEPATH_RUNNER_TOKEN"), JsonMapper.builder().build());
        assertTrue(real.available());
        assertEquals("Hallo Welt\n", real.run("print('Hallo', input())", "Welt").stdout());
        assertEquals(CodeRunner.RunResult.Status.ERROR, real.run("open('/etc/passwd')", "").status());
        assertEquals(CodeRunner.RunResult.Status.TIMEOUT, real.run("while True: pass", "").status());
    }
}
