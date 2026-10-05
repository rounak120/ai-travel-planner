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
    @org.springframework.beans.factory.annotation.Autowired
    com.aibot.travelplanner.trip.TripService trips;
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    TripLocationStore locationStore;
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;
    private final java.util.List<java.util.UUID> ownTripIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanOwnTrips() {
        ownTripIds.forEach(id -> jdbc.update("DELETE FROM trips WHERE id = ?", id));
    }

    private java.util.UUID createTrip() {
        var date = java.time.LocalDate.now().plusDays(30);
        var trip = trips.create(new com.aibot.travelplanner.trip.CreateTripRequest("Jaipur", date, date, 2,
                new java.math.BigDecimal("5000.00"), "INR", java.util.List.of("history")));
        ownTripIds.add(trip.id());
        return trip.id();
    }

    @Test
    void savesProviderLocationReadsOfflineAndAtomicallyReplaces() throws Exception {
        var id = createTrip();
        assertThat(location(id, "GET", null).statusCode()).isEqualTo(404);
        var saved = location(id, "PUT", "{\"searchName\":\"Jaipur\",\"placeId\":1269515}");
        assertThat(saved.statusCode()).isEqualTo(200);
        var mapper = JsonMapper.builder().build();
        var selected = mapper.readValue(saved.body(), SelectedDestination.class);
        assertThat(selected.tripId()).isEqualTo(id);
        assertThat(selected.place().latitude()).isEqualTo(26.91962);
        assertThat(selected.selectedAt()).isNotNull();
        assertThat(selected.attribution()).contains("GeoNames");
        status.set(500); // Reading a saved location must work even if the provider is down.
        var loaded = location(id, "GET", null);
        assertThat(loaded.statusCode()).isEqualTo(200);
        assertThat(mapper.readValue(loaded.body(), SelectedDestination.class)).isEqualTo(selected);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(location(id, "PUT", "{\"searchName\":\"Jaipur\",\"placeId\":1269515}").statusCode()).isEqualTo(502);
        assertThat(locationStore.find(id).orElseThrow()).isEqualTo(selected);
        status.set(200);
        assertThat(location(id, "PUT", "{\"searchName\":\"Jaipur\",\"placeId\":999999}").statusCode()).isEqualTo(422);
        assertThat(locationStore.find(id).orElseThrow()).isEqualTo(selected);
        body.set(body.get().replace("1269515", "123456").replace("26.91962", "26.95"));
        assertThat(location(id, "PUT", "{\"searchName\":\"Jaipur\",\"placeId\":123456}").statusCode()).isEqualTo(200);
        assertThat(locationStore.find(id).orElseThrow().place().id()).isEqualTo(123456);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_locations WHERE trip_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(trips.get(id).destination()).isEqualTo("Jaipur");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM itineraries WHERE trip_id = ?", Integer.class, id)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "not-json", "{\"searchName\":\"Jaipur\"}",
            "{\"searchName\":\"Jaipur\",\"placeId\":0}", "{\"searchName\":\"  \",\"placeId\":1}",
            "{\"searchName\":\"Ab\\ncd\",\"placeId\":1}"})
    void invalidSelectionNeverCallsProvider(String payload) throws Exception {
        assertThat(location(createTrip(), "PUT", payload).statusCode()).isEqualTo(400);
        assertThat(calls.get()).isZero();
    }

    @Test
    void missingTripNeverCallsProviderAndTripsKeepSeparateSelections() throws Exception {
        var missing = java.util.UUID.randomUUID();
        assertThat(location(missing, "PUT", "{\"searchName\":\"Jaipur\",\"placeId\":1269515}").statusCode()).isEqualTo(404);
        assertThat(location(missing, "GET", null).statusCode()).isEqualTo(404);
        assertThat(calls.get()).isZero();
        var first = createTrip();
        var second = createTrip();
        assertThat(location(first, "PUT", "{\"searchName\":\"Jaipur\",\"placeId\":1269515}").statusCode()).isEqualTo(200);
        assertThat(location(second, "GET", null).statusCode()).isEqualTo(404);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rolledBackReplacementPreservesSavedLocation() throws Exception {
        var id = createTrip();
        assertThat(location(id, "PUT", "{\"searchName\":\"Jaipur\",\"placeId\":1269515}").statusCode()).isEqualTo(200);
        var saved = locationStore.find(id).orElseThrow();
        var changed = new SelectedDestination(id, saved.place(), "different attribution", saved.sourceUrl(), saved.selectedAt());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    locationStore.save(changed);
                    throw new IllegalStateException("rollback test");
                })).isInstanceOf(IllegalStateException.class);
        assertThat(locationStore.find(id).orElseThrow()).isEqualTo(saved);
    }

    private HttpResponse<String> location(java.util.UUID id, String method, String payload) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                    + "/api/v1/trips/" + id + "/location")).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }
    }

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
