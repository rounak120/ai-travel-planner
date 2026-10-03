package com.aibot.travelplanner.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real Spring AI HTTP client against a fake, never the real model. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"ai", "test"})
class AiConnectionTests {

    private static final AtomicInteger providerStatus = new AtomicInteger(200);
    private static final AtomicInteger calls = new AtomicInteger();
    private static final AtomicReference<String> providerBody = new AtomicReference<>();
    private static final AtomicReference<String> requestBody = new AtomicReference<>();
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final HttpServer provider = startProvider();

    @LocalServerPort
    private int port;

    private static HttpServer startProvider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                calls.incrementAndGet();
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = providerBody.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(providerStatus.get(), body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start local AI test server", exception);
        }
    }

    @DynamicPropertySource
    static void useLocalProvider(DynamicPropertyRegistry registry) {
        String baseUrl = "http://127.0.0.1:" + provider.getAddress().getPort();
        registry.add("spring.ai.ollama.base-url", () -> baseUrl);
        registry.add("spring.ai.ollama.chat.model", () -> "qwen3:4b");
    }

    @BeforeEach
    void resetProvider() {
        calls.set(0);
        requestBody.set(null);
        authorization.set(null);
        providerStatus.set(200);
        providerBody.set(completion("Travel planner AI connection is working."));
    }

    @AfterAll
    static void stopProvider() {
        provider.stop(0);
    }

    @Test
    void sendsBoundedRequestAndReturnsModelReply() throws Exception {
        var response = request("/api/v1/ai/test", "POST");
        assertThat(response.statusCode()).isEqualTo(200);
        var mapper = JsonMapper.builder().build();
        assertThat(mapper.readTree(response.body()).get("message").asText())
                .isEqualTo("Travel planner AI connection is working.");
        var sent = mapper.readTree(requestBody.get());
        assertThat(sent.get("model").asText()).isEqualTo("qwen3:4b");
        assertThat(sent.get("options").get("num_predict").asInt()).isEqualTo(512);
        assertThat(sent.get("options").get("num_ctx").asInt()).isEqualTo(4096);
        assertThat(sent.get("think").asBoolean()).isTrue();
        assertThat(sent.get("stream").asBoolean()).isFalse();
        assertThat(sent.get("messages").toString()).contains("Travel planner AI connection is working.");
        assertThat(authorization.get()).isNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 429, 500})
    void sanitizesProviderFailuresWithoutRetries(int status) throws Exception {
        providerStatus.set(status);
        providerBody.set("""
                {"error":"sensitive-provider-detail"}
                """);
        var response = request("/api/v1/ai/test", "POST");
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(response.body()).contains("AI provider unavailable")
                .doesNotContain("sensitive-provider-detail", "API key", "API quota");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsEmptyModelReply() throws Exception {
        providerBody.set(completion(""));
        assertThat(request("/api/v1/ai/test", "POST").statusCode()).isEqualTo(502);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void returnsOnlyAnswerNotSeparateThinking() throws Exception {
        providerBody.set(completion("Travel planner AI connection is working.")
                .replace("\"role\":\"assistant\"", "\"role\":\"assistant\",\"thinking\":\"internal-test-reasoning\""));
        var response = request("/api/v1/ai/test", "POST");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Travel planner AI connection is working.")
                .doesNotContain("internal-test-reasoning", "thinking");
    }

    @Test
    void getAndHealthNeverCallTheProvider() throws Exception {
        assertThat(request("/api/v1/ai/test", "GET").statusCode()).isEqualTo(405);
        assertThat(request("/actuator/health", "GET").statusCode()).isEqualTo(200);
        assertThat(calls.get()).isZero();
    }

    private static String completion(String content) {
        return """
                {"model":"qwen3:4b","created_at":"2026-10-03T07:00:00Z",
                 "message":{"role":"assistant","content":"%s"},
                 "done":true,"done_reason":"stop","total_duration":1000000,
                 "prompt_eval_count":15,"eval_count":8}
                """.formatted(content);
    }

    private HttpResponse<String> request(String path, String method) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
