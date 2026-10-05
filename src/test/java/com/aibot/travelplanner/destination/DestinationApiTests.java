package com.aibot.travelplanner.destination;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DestinationApiTests {
    private static final AtomicInteger calls = new AtomicInteger();
    private static final AtomicInteger status = new AtomicInteger(200);
    private static final AtomicInteger delay = new AtomicInteger();
    private static final AtomicReference<String> body = new AtomicReference<>();
    private static final AtomicReference<String> query = new AtomicReference<>();
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
    private static final HttpServer provider = startProvider();
    @LocalServerPort int port;

    private static HttpServer startProvider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/v1/search", exchange -> {
                calls.incrementAndGet();
                query.set(exchange.getRequestURI().getRawQuery());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var bytes = body.get().getBytes(StandardCharsets.UTF_8);
                int responseStatus = status.get();
                try {
                    Thread.sleep(delay.get());
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(responseStatus, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void providerSettings(DynamicPropertyRegistry registry) {
        registry.add("travel.geocoding.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
        registry.add("travel.geocoding.timeout-ms", () -> 500);
    }

    @BeforeEach
    void reset() {
        calls.set(0);
        status.set(200);
        delay.set(0);
        query.set(null);
        authorization.set(null);
        body.set("""
                {"results":[{"id":1269515,"name":"Jaipur","latitude":26.91962,
                "longitude":75.78781,"country":"India","country_code":"IN",
                "admin1":"Rajasthan","timezone":"Asia/Kolkata","population":2711758}]}
                """);
    }

    @AfterAll
    static void stop() {
        provider.stop(0);
        executor.shutdownNow();
    }

    @Test
    void mapsResultsWithAttributionWithoutAiOrCredentials() throws Exception {
        var response = get("?name=%20Jaipur%20");
        assertThat(response.statusCode()).isEqualTo(200);
        var result = JsonMapper.builder().build().readValue(response.body(), DestinationSearch.class);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().getFirst().name()).isEqualTo("Jaipur");
        assertThat(result.results().getFirst().region()).isEqualTo("Rajasthan");
        assertThat(result.results().getFirst().latitude()).isEqualTo(26.91962);
        assertThat(result.attribution()).contains("GeoNames", "Open-Meteo");
        assertThat(query.get()).isEqualTo("name=Jaipur&count=5&language=en&format=json");
        assertThat(authorization.get()).isNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void encodesSpecialCharactersAsOneSearchValue() throws Exception {
        String name = "São Paulo &count=99?x=#";
        assertThat(get("?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)).statusCode()).isEqualTo(200);
        assertThat(query.get().split("&")).hasSize(4);
        assertThat(URLDecoder.decode(query.get().split("&")[0].substring(5), StandardCharsets.UTF_8)).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"results\":[]}"})
    void noMatchesIsAnEmptySuccess(String content) throws Exception {
        body.set(content);
        var response = get("?name=Nothing");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonMapper.builder().build().readValue(response.body(), DestinationSearch.class).results()).isEmpty();
    }

    @Test
    void optionalLocationFieldsCanBeAbsent() throws Exception {
        body.set("{\"results\":[{\"id\":1,\"name\":\"Place\",\"latitude\":0,\"longitude\":0}]}");
        assertThat(get("?name=Place").statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?name=", "?name=A", "?name=%20%20", "?name=Ab%0Acd"})
    void invalidQueryNeverCallsProvider(String suffix) throws Exception {
        assertThat(get(suffix).statusCode()).isEqualTo(400);
        assertThat(calls.get()).isZero();
    }

    @Test
    void tooLongQueryNeverCallsProvider() throws Exception {
        assertThat(get("?name=" + "a".repeat(101)).statusCode()).isEqualTo(400);
        assertThat(calls.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 429, 500})
    void providerFailuresAreSanitizedWithoutRetries(int code) throws Exception {
        status.set(code);
        body.set("sensitive-provider-detail");
        var response = get("?name=Jaipur");
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).contains("Destination provider unavailable").doesNotContain("sensitive-provider-detail");
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null", "[]", "{\"error\":true}", "{\"results\":null}",
            "{\"results\":{}}", "{\"results\":[null]}",
            "{\"results\":[{\"id\":1,\"name\":\"Bad\",\"latitude\":91,\"longitude\":0}]}",
            "{\"results\":[{\"id\":1,\"name\":\"Bad\",\"latitude\":0,\"longitude\":181}]}"})
    void malformedResponsesFailSafely(String content) throws Exception {
        body.set(content);
        assertThat(get("?name=Jaipur").statusCode()).isEqualTo(502);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void slowProviderTimesOut() throws Exception {
        delay.set(1500);
        assertThat(get("?name=Jaipur").statusCode()).isEqualTo(502);
        assertThat(calls.get()).isEqualTo(1);
    }

    private HttpResponse<String> get(String suffix) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                    + "/api/v1/destinations" + suffix)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
