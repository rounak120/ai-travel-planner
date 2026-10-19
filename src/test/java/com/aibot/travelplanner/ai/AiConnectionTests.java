package com.aibot.travelplanner.ai;

import com.sun.net.httpserver.HttpServer;
import com.aibot.travelplanner.trip.Trip;
import com.aibot.travelplanner.trip.TripService;
import com.aibot.travelplanner.trip.TripNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Exercises the real Spring AI HTTP client against a fake, never the real model. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"ai", "test"})
class AiConnectionTests {

    private static final AtomicInteger providerStatus = new AtomicInteger(200);
    private static final AtomicInteger calls = new AtomicInteger();
    private static final AtomicReference<String> providerBody = new AtomicReference<>();
    private static final AtomicReference<String> requestBody = new AtomicReference<>();
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final AtomicReference<Runnable> duringModelCall = new AtomicReference<>();
    private static final HttpServer provider = startProvider();

    @LocalServerPort
    private int port;

    @MockitoBean
    private TripService trips;

    @MockitoBean
    private com.aibot.travelplanner.weather.WeatherClient weatherClient;

    @MockitoBean
    private java.time.Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    private com.aibot.travelplanner.destination.TripLocationStore locations;

    @org.springframework.beans.factory.annotation.Autowired
    private com.aibot.travelplanner.itinerary.ItineraryStore itineraries;

    @org.junit.jupiter.api.AfterEach
    void removeOwnTrip() {
        jdbc.update("DELETE FROM trips WHERE id = ?", TRIP_ID);
    }

    private static final UUID TRIP_ID = UUID.randomUUID();

    private void stubTrip(int days) {
        var start = LocalDate.of(2027, 1, 10);
        when(trips.get(TRIP_ID)).thenReturn(new Trip(TRIP_ID, "Jaipur", start,
                start.plusDays(days - 1), 2, new BigDecimal("1000.00"), "INR",
                List.of("history"), Instant.now()));
    }

    private static String itinerary(int day, String cost) {
        return "{\"days\":[{\"dayNumber\":" + day
                + ",\"activities\":[{\"title\":\" Fort visit \",\"description\":\"Explore local history.\",\"estimatedCost\":"
                + cost + "}]}]}";
    }

    @Test
    void revisionPromptIncludesTheSelectedDraftAndRequestedChange() throws Exception {
        stubTrip(1);providerBody.set(completion(itinerary(1,"250.25")));
        assertThat(request("/api/v1/trips/"+TRIP_ID+"/itinerary","POST").statusCode()).isEqualTo(200);
        long version=jdbc.queryForObject("SELECT id FROM itineraries WHERE trip_id=?",Long.class,TRIP_ID);
        providerBody.set(completion(itinerary(1,"100.00")));
        try(var client=java.net.http.HttpClient.newHttpClient()) {
            var response=client.send(com.aibot.travelplanner.TestAuth.request(java.net.URI.create(
                    "http://127.0.0.1:"+port+"/api/v1/trips/"+TRIP_ID+"/itinerary/revisions"))
                    .header("Content-Type","application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"versionId\":"+version+",\"instruction\":\"Make day 1 cheaper\"}"))
                    .build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
        }
        var sent=JsonMapper.builder().build().readTree(requestBody.get());
        assertThat(sent.get("model").asText()).isEqualTo("qwen3:4b");
        assertThat(sent.get("messages").toString()).contains("previousDraft","Fort visit","revisionRequest","Make day 1 cheaper");
        assertThat(itineraries.getVersion(TRIP_ID,version).itinerary().estimatedActivityCostTotal()).isEqualByComparingTo("250.25");
    }

    @Test
    void returnsValidatedDraftWithServerDatesAndTotals() throws Exception {
        stubTrip(1);
        providerBody.set(completion(itinerary(1, "250.25")));
        var response = request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST");
        assertThat(response.statusCode()).isEqualTo(200);
        var mapper = JsonMapper.builder().build();
        var draft = mapper.readTree(response.body());
        assertThat(draft.get("tripId").asText()).isEqualTo(TRIP_ID.toString());
        assertThat(draft.get("days").get(0).get("date").asText()).isEqualTo("2027-01-10");
        assertThat(draft.get("estimatedActivityCostTotal").asText()).isEqualTo("250.25");
        assertThat(response.body()).contains("Fort visit", "saved AI draft", "INR");
        assertThat(draft.get("selectedLocation").isNull()).isTrue();
        assertThat(draft.get("weatherContext").get("status").asText()).isEqualTo("NO_LOCATION");
        org.mockito.Mockito.verifyNoInteractions(weatherClient);
        assertThat(response.body()).contains("unverified destination text");
        var loaded = request("/api/v1/trips/" + TRIP_ID + "/itinerary", "GET");
        assertThat(loaded.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(loaded.body())).isEqualTo(draft);
        var sent = mapper.readTree(requestBody.get());
        assertThat(sent.get("format").isObject()).isTrue();
        assertThat(sent.get("think").asBoolean()).isFalse();
        assertThat(sent.get("options").get("num_predict").asInt()).isEqualTo(4096);
        assertThat(sent.get("messages").toString()).contains("Jaipur", "history");
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1000.01", "1.001", "null"})
    void rejectsInvalidActivityCosts(String cost) throws Exception {
        stubTrip(1);
        providerBody.set(completion(itinerary(1, cost)));
        assertThat(request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST").statusCode()).isEqualTo(502);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void selectedLocationGuidesPromptAndSnapshotSurvivesConcurrentSelectionChange() throws Exception {
        stubTrip(1);
        var first = selectedLocation(100, "Paris", "France", "FR", "Europe/Paris");
        var second = selectedLocation(200, "Paris", "United States", "US", "America/Chicago");
        locations.save(first);
        duringModelCall.set(() -> locations.save(second));
        providerBody.set(completion(itinerary(1, "20.25")));
        var response = request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST");
        assertThat(response.statusCode()).isEqualTo(200);
        var mapper = JsonMapper.builder().build();
        var result = mapper.readValue(response.body(), com.aibot.travelplanner.itinerary.ItineraryDraft.class);
        assertThat(result.selectedLocation()).isEqualTo(first);
        assertThat(result.weatherContext().status()).isEqualTo(com.aibot.travelplanner.weather.PlanningWeather.Status.UNSUPPORTED);
        org.mockito.Mockito.verifyNoInteractions(weatherClient);
        assertThat(result.destination()).isEqualTo("Paris");
        assertThat(locations.find(TRIP_ID).orElseThrow()).isEqualTo(second);
        var messages = mapper.readTree(requestBody.get()).get("messages");
        var input = mapper.readTree(messages.get(messages.size() - 1).get("content").asText());
        assertThat(input.get("destination").asText()).isEqualTo("Paris");
        assertThat(input.get("selectedLocation").get("country").asText()).isEqualTo("France");
        assertThat(input.toString()).doesNotContain("Jaipur", "United States", TRIP_ID.toString(), "selectedAt");
        assertThat(input.get("currency").asText()).isEqualTo("INR");
        long oldId = itineraries.history(TRIP_ID, null, 1).versions().getFirst().id();
        assertThat(itineraries.get(TRIP_ID).selectedLocation()).isEqualTo(first);

        assertThat(request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST").statusCode()).isEqualTo(200);
        assertThat(itineraries.get(TRIP_ID).selectedLocation()).isEqualTo(second);
        assertThat(itineraries.getVersion(TRIP_ID, oldId).itinerary().selectedLocation()).isEqualTo(first);
        jdbc.update("UPDATE itineraries SET document = document - 'weatherContext' WHERE id = ? AND trip_id = ?", oldId, TRIP_ID);
        var legacy = itineraries.getVersion(TRIP_ID, oldId).itinerary();
        assertThat(legacy.selectedLocation()).isEqualTo(first);
        assertThat(legacy.weatherContext()).isNull();
    }

    private com.aibot.travelplanner.destination.SelectedDestination selectedLocation(
            long id, String name, String country, String code, String zone) {
        return new com.aibot.travelplanner.destination.SelectedDestination(TRIP_ID,
                new com.aibot.travelplanner.destination.DestinationSearch.Place(id, name, country, code,
                        null, 48.85, 2.35, zone), "Test provider attribution", "https://example.invalid/", Instant.parse("2026-10-05T10:00:00Z"));
    }

    @Test
    void suppliesForecastAndPersistsOriginalContextAcrossLocationChangesAndLaterFailures() throws Exception {
        var date = LocalDate.of(2026, 10, 6);
        when(trips.get(TRIP_ID)).thenReturn(new Trip(TRIP_ID, "Paris", date, date, 2,
                new BigDecimal("1000.00"), "INR", List.of("history"), Instant.now()));
        var first = selectedLocation(100, "Paris", "France", "FR", "Europe/Paris");
        var second = selectedLocation(200, "Paris", "United States", "US", "America/Chicago");
        locations.save(first);
        var measurements = List.of(new com.aibot.travelplanner.weather.TripWeather.Day(date, null, 30.0, 12.5));
        when(weatherClient.forecast(first.place(), date, date)).thenAnswer(invocation -> {
            locations.save(second); // Simulate a change while the weather request runs.
            return measurements;
        });
        providerBody.set(completion(itinerary(1, "20.25")));
        String path = "/api/v1/trips/" + TRIP_ID + "/itinerary";
        var response = request(path, "POST");
        assertThat(response.statusCode()).isEqualTo(200);
        var mapper = JsonMapper.builder().build();
        var draft = mapper.readValue(response.body(), com.aibot.travelplanner.itinerary.ItineraryDraft.class);
        assertThat(draft.weatherContext().status()).isEqualTo(com.aibot.travelplanner.weather.PlanningWeather.Status.AVAILABLE);
        assertThat(draft.weatherContext().forecast().location()).isEqualTo(first.place());
        assertThat(draft.weatherContext().forecast().days()).isEqualTo(measurements);
        assertThat(draft.selectedLocation()).isEqualTo(first);
        var messages = mapper.readTree(requestBody.get()).get("messages");
        var input = mapper.readTree(messages.get(messages.size() - 1).get("content").asText());
        assertThat(input.get("weather").get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(input.get("weather").get("days").get(0).get("minimumTemperatureCelsius").isNull()).isTrue();
        assertThat(input.get("weather").toString()).contains("12.5", "2026-10-06", "fetchedAt").doesNotContain(TRIP_ID.toString());
        assertThat(draft.warnings().toString()).contains("Forecast supplied").doesNotContain("No live weather");
        long version = itineraries.history(TRIP_ID, null, 1).versions().getFirst().id();
        assertThat(mapper.readTree(request(path, "GET").body())).isEqualTo(mapper.readTree(response.body()));
        org.mockito.Mockito.verify(weatherClient, org.mockito.Mockito.times(1)).forecast(first.place(), date, date);

        when(weatherClient.forecast(second.place(), date, date)).thenThrow(new com.aibot.travelplanner.weather.WeatherUnavailableException());
        assertThat(request(path, "POST").statusCode()).isEqualTo(200);
        var fallback = itineraries.get(TRIP_ID);
        assertThat(fallback.weatherContext().status()).isEqualTo(com.aibot.travelplanner.weather.PlanningWeather.Status.UNAVAILABLE);
        assertThat(fallback.weatherContext().forecast()).isNull();
        assertThat(fallback.warnings().toString()).contains("No forecast used");
        assertThat(itineraries.getVersion(TRIP_ID, version).itinerary().weatherContext()).isEqualTo(draft.weatherContext());
        assertThat(requestBody.get()).doesNotContain("12.5");
    }

    @Test
    void forecastSuccessCannotMakeInvalidAiOutputPersist() throws Exception {
        var date = LocalDate.of(2026, 10, 6);
        when(trips.get(TRIP_ID)).thenReturn(new Trip(TRIP_ID, "Paris", date, date, 2,
                new BigDecimal("1000.00"), "INR", List.of("history"), Instant.now()));
        var selected = selectedLocation(100, "Paris", "France", "FR", "Europe/Paris");
        locations.save(selected);
        when(weatherClient.forecast(selected.place(), date, date)).thenReturn(
                List.of(new com.aibot.travelplanner.weather.TripWeather.Day(date, 20.0, 30.0, 0.0)));
        providerBody.set(completion("invalid"));
        assertThat(request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST").statusCode()).isEqualTo(502);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM itineraries WHERE trip_id = ?", Integer.class, TRIP_ID)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sensitive-invalid-content", "null", "{}", "{\"days\":[null]}", "{\"days\":[]}"})
    void rejectsInvalidDraftShape(String content) throws Exception {
        stubTrip(1);
        providerBody.set(completion(content));
        var response = request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST");
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).doesNotContain("sensitive-invalid-content");
    }

    @Test
    void acceptsSevenInclusiveDaysAndCalculatesTotal() throws Exception {
        stubTrip(7);
        var days = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> itinerary(day, "10.25").substring(9))
                .map(day -> day.substring(0, day.length() - 2))
                .collect(java.util.stream.Collectors.joining(","));
        providerBody.set(completion("{\"days\":[" + days + "]}"));
        var response = request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST");
        assertThat(response.statusCode()).isEqualTo(200);
        var draft = JsonMapper.builder().build().readTree(response.body());
        assertThat(draft.get("days").size()).isEqualTo(7);
        assertThat(draft.get("days").get(6).get("date").asText()).isEqualTo("2027-01-16");
        assertThat(draft.get("estimatedActivityCostTotal").asText()).isEqualTo("71.75");
    }

    @Test
    void itineraryProviderFailureIsSanitizedWithoutRetry() throws Exception {
        stubTrip(1);
        providerStatus.set(500);
        providerBody.set("{\"error\":\"sensitive-provider-detail\"}");
        var response = request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST");
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).doesNotContain("sensitive-provider-detail");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsMissingDaysWrongNumberAndTruncation() throws Exception {
        stubTrip(2);
        providerBody.set(completion(itinerary(1, "10")));
        assertThat(request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST").statusCode()).isEqualTo(502);
        stubTrip(1);
        providerBody.set(completion(itinerary(2, "10")));
        assertThat(request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST").statusCode()).isEqualTo(502);
        providerBody.set(completion(itinerary(1, "10")).replace("\"stop\"", "\"length\""));
        assertThat(request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST").statusCode()).isEqualTo(502);
    }

    @Test
    void rejectsUnknownTripAndLongTripsBeforeCallingModel() throws Exception {
        when(trips.get(TRIP_ID)).thenThrow(new TripNotFoundException(TRIP_ID));
        assertThat(request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST").statusCode()).isEqualTo(404);
        org.mockito.Mockito.reset(trips);
        stubTrip(8);
        assertThat(request("/api/v1/trips/" + TRIP_ID + "/itinerary", "POST").statusCode()).isEqualTo(422);
        assertThat(request("/api/v1/trips/not-a-uuid/itinerary", "POST").statusCode()).isEqualTo(400);
        assertThat(calls.get()).isZero();
    }

    private static HttpServer startProvider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                calls.incrementAndGet();
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var action = duringModelCall.getAndSet(null);
                if (action != null) action.run();
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
        var now = Instant.parse("2026-10-05T10:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(clock.withZone(org.mockito.ArgumentMatchers.any(java.time.ZoneId.class)))
                .thenAnswer(invocation -> java.time.Clock.fixed(now, invocation.getArgument(0)));
        duringModelCall.set(null);
        jdbc.update("""
                INSERT INTO trips (id, destination, start_date, end_date, travelers, budget, currency, created_at)
                VALUES (?, 'Jaipur', '2027-01-10', '2027-01-10', 2, 1000, 'INR', CURRENT_TIMESTAMP)
                """, TRIP_ID);
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
    void regenerationKeepsVersionsAndFailuresPreserveLastGoodDraft() throws Exception {
        stubTrip(1);
        String path = "/api/v1/trips/" + TRIP_ID + "/itinerary";
        assertThat(request(path, "GET").statusCode()).isEqualTo(404);
        assertThat(calls.get()).isZero();
        providerBody.set(completion(itinerary(1, "20.25")));
        assertThat(request(path, "POST").statusCode()).isEqualTo(200);
        providerBody.set(completion(itinerary(1, "30.50")));
        var latest = request(path, "POST");
        assertThat(latest.statusCode()).isEqualTo(200);
        providerBody.set(completion("invalid"));
        assertThat(request(path, "POST").statusCode()).isEqualTo(502);
        var loaded = request(path, "GET");
        var mapper = JsonMapper.builder().build();
        assertThat(mapper.readTree(loaded.body())).isEqualTo(mapper.readTree(latest.body()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM itineraries WHERE trip_id = ?", Integer.class, TRIP_ID))
                .isEqualTo(2);
        assertThat(calls.get()).isEqualTo(3);
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
                 "message":{"role":"assistant","content":%s},
                 "done":true,"done_reason":"stop","total_duration":1000000,
                 "prompt_eval_count":15,"eval_count":8}
                """.formatted(JsonMapper.builder().build().writeValueAsString(content));
    }

    private HttpResponse<String> request(String path, String method) throws Exception {
        var request = com.aibot.travelplanner.TestAuth.request(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
