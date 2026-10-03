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

/** Exercises the real Spring AI HTTP client against a local fake, never OpenAI. */
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
            server.createContext("/v1/chat/completions", exchange -> {
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
        String baseUrl = "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1";
        registry.add("spring.ai.openai.base-url", () -> baseUrl);
        registry.add("spring.ai.openai.chat.base-url", () -> baseUrl);
        registry.add("spring.ai.openai.api-key", () -> "test-key-not-real");
        registry.add("spring.ai.openai.chat.api-key", () -> "test-key-not-real");
        registry.add("spring.ai.openai.chat.model", () -> "gpt-5.4-mini");
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
        assertThat(sent.get("model").asText()).isEqualTo("gpt-5.4-mini");
        assertThat(sent.get("max_completion_tokens").asInt()).isEqualTo(128);
        assertThat(sent.get("reasoning_effort").asText()).isEqualTo("none");
        assertThat(sent.get("store").asBoolean()).isFalse();
        assertThat(sent.has("max_tokens")).isFalse();
        assertThat(sent.get("messages").toString()).contains("Travel planner AI connection is working.");
        assertThat(authorization.get()).isEqualTo("Bearer test-key-not-real");
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void sanitizesProviderFailuresWithoutRetries(int status) throws Exception {
        providerStatus.set(status);
        providerBody.set("""
                {"error":{"message":"sensitive-provider-detail","type":"api_error","code":"test_error"}}
                """);
        var response = request("/api/v1/ai/test", "POST");
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(response.body()).contains("AI provider unavailable")
                .doesNotContain("sensitive-provider-detail", "test-key-not-real");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsEmptyModelReply() throws Exception {
        providerBody.set(completion(""));
        assertThat(request("/api/v1/ai/test", "POST").statusCode()).isEqualTo(502);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void getAndHealthNeverCallTheProvider() throws Exception {
        assertThat(request("/api/v1/ai/test", "GET").statusCode()).isEqualTo(405);
        assertThat(request("/actuator/health", "GET").statusCode()).isEqualTo(200);
        assertThat(calls.get()).isZero();
    }

    private static String completion(String content) {
        return """
                {"id":"chatcmpl-local-test","object":"chat.completion","created":1,
                 "model":"gpt-5.4-mini",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":15,"completion_tokens":8,"total_tokens":23}}
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
