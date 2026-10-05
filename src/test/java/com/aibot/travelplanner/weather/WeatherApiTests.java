package com.aibot.travelplanner.weather;

import com.aibot.travelplanner.destination.*;
import com.aibot.travelplanner.trip.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WeatherApiTests {
    private static final UUID ID = UUID.randomUUID();
    // October 4 in UTC, already October 5 in Jaipur: catches server-timezone mistakes.
    private static final Instant NOW = Instant.parse("2026-10-04T23:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final AtomicInteger calls = new AtomicInteger();
    private static final AtomicInteger status = new AtomicInteger();
    private static final AtomicInteger delay = new AtomicInteger();
    private static final AtomicReference<String> body = new AtomicReference<>();
    private static final AtomicReference<String> query = new AtomicReference<>();
    private static final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
    private static final HttpServer provider = startProvider();
    @LocalServerPort int port;
    @MockitoBean TripService trips;
    @MockitoBean TripLocationService locations;
    @MockitoBean Clock clock;

    private static HttpServer startProvider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/v1/forecast", exchange -> {
                calls.incrementAndGet();
                query.set(exchange.getRequestURI().getRawQuery());
                byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                int code = status.get();
                try {
                    Thread.sleep(delay.get());
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(code, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally { exchange.close(); }
            });
            server.start();
            return server;
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("travel.weather.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
        registry.add("travel.weather.timeout-ms", () -> 500);
    }

    @BeforeEach
    void setup() {
        calls.set(0); status.set(200); delay.set(0); query.set(null);
        when(clock.instant()).thenReturn(NOW);
        when(clock.withZone(any(ZoneId.class))).thenAnswer(invocation -> Clock.fixed(NOW, invocation.getArgument(0)));
        trip(TODAY, TODAY);
        location("Asia/Kolkata");
        body.set(forecast(TODAY, TODAY));
    }

    private void trip(LocalDate start, LocalDate end) {
        when(trips.get(ID)).thenReturn(new Trip(ID, "Jaipur", start, end, 2,
                new BigDecimal("5000"), "INR", List.of("history"), NOW));
    }

    private void location(String timezone) {
        when(locations.get(ID)).thenReturn(new SelectedDestination(ID,
                new DestinationSearch.Place(1269515, "Jaipur", "India", "IN", "Rajasthan", 26.91962, 75.78781, timezone),
                "GeoNames", "https://open-meteo.com", NOW));
    }

    private static String forecast(LocalDate start, LocalDate end) {
        var dates = start.datesUntil(end.plusDays(1)).map(LocalDate::toString).toList();
        return JsonMapper.builder().build().writeValueAsString(Map.of(
                "timezone", "Asia/Kolkata", "daily_units", Map.of("time", "iso8601", "temperature_2m_min", "°C",
                        "temperature_2m_max", "°C", "precipitation_sum", "mm"),
                "daily", Map.of("time", dates, "temperature_2m_min", Collections.nCopies(dates.size(), 20.5),
                        "temperature_2m_max", Collections.nCopies(dates.size(), 30.5),
                        "precipitation_sum", Collections.nCopies(dates.size(), 1.2))));
    }

    @AfterAll
    static void closeProvider() { provider.stop(0); executor.shutdownNow(); }

    @Test
    void usesSavedCoordinatesAndDestinationDatesWithoutAi() throws Exception {
        var response = get(ID.toString());
        assertThat(response.statusCode()).isEqualTo(200);
        var result = JsonMapper.builder().build().readValue(response.body(), TripWeather.class);
        assertThat(result.days()).containsExactly(new TripWeather.Day(TODAY, 20.5, 30.5, 1.2));
        assertThat(result.fetchedAt()).isEqualTo(NOW);
        assertThat(result.attribution()).contains("Open-Meteo");
        assertThat(query.get()).contains("latitude=26.91962", "longitude=75.78781", "timezone=Asia%2FKolkata",
                "start_date=2026-10-05", "end_date=2026-10-05", "temperature_unit=celsius", "precipitation_unit=mm")
                .doesNotContain(ID.toString(), "5000", "history");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void acceptsWholeSixteenDayWindowAndLastDayAlone() throws Exception {
        trip(TODAY, TODAY.plusDays(15));
        body.set(forecast(TODAY, TODAY.plusDays(15)));
        var response = get(ID.toString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonMapper.builder().build().readValue(response.body(), TripWeather.class).days()).hasSize(16);
        trip(TODAY.plusDays(15), TODAY.plusDays(15));
        body.set(forecast(TODAY.plusDays(15), TODAY.plusDays(15)));
        assertThat(get(ID.toString()).statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsPastFutureAndPartiallyCoveredDatesWithoutCallingProvider() throws Exception {
        for (var range : List.of(new LocalDate[]{TODAY.minusDays(1), TODAY},
                new LocalDate[]{TODAY, TODAY.plusDays(16)}, new LocalDate[]{TODAY.plusDays(30), TODAY.plusDays(31)})) {
            trip(range[0], range[1]);
            var response = get(ID.toString());
            assertThat(response.statusCode()).isEqualTo(422);
            assertThat(response.body()).contains("2026-10-05", "2026-10-20", "Asia/Kolkata");
        }
        assertThat(calls.get()).isZero();
    }

    @Test
    void missingTripLocationAndTimezoneDoNotCallProvider() throws Exception {
        when(trips.get(ID)).thenThrow(new TripNotFoundException(ID));
        assertThat(get(ID.toString()).statusCode()).isEqualTo(404);
        reset(trips); trip(TODAY, TODAY);
        when(locations.get(ID)).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "This trip has no selected location."));
        assertThat(get(ID.toString()).statusCode()).isEqualTo(404);
        reset(locations); location(null);
        assertThat(get(ID.toString()).statusCode()).isEqualTo(422);
        location("invalid-zone");
        assertThat(get(ID.toString()).statusCode()).isEqualTo(422);
        assertThat(get("not-a-uuid").statusCode()).isEqualTo(400);
        assertThat(calls.get()).isZero();
    }

    @Test
    void missingMeasurementsRemainNullAndTimezoneAliasesWork() throws Exception {
        body.set(body.get().replace("20.5", "null").replace("1.2", "null").replace("Asia/Kolkata", "Asia/Calcutta"));
        var response = get(ID.toString());
        assertThat(response.statusCode()).isEqualTo(200);
        var result = JsonMapper.builder().build().readValue(response.body(), TripWeather.class);
        assertThat(result.days().getFirst().minimumTemperatureCelsius()).isNull();
        assertThat(result.days().getFirst().precipitationMm()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 429, 500})
    void sanitizesFailuresWithoutRetry(int code) throws Exception {
        status.set(code); body.set("sensitive-provider-detail");
        var response = get(ID.toString());
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).doesNotContain("sensitive-provider-detail");
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null", "{}", "{\"error\":true}"})
    void rejectsInvalidResponse(String content) throws Exception {
        body.set(content);
        assertThat(get(ID.toString()).statusCode()).isEqualTo(502);
    }

    @Test
    void rejectsWrongUnitsDatesArrayLengthsRangesAndTimezone() throws Exception {
        String valid = body.get();
        for (String invalid : List.of(valid.replace("°C", "°F"), valid.replace("2026-10-05", "2026-10-06"),
                valid.replace("[20.5]", "[]"), valid.replace("20.5", "40.0"),
                valid.replace("1.2", "-1.2"), valid.replace("Asia/Kolkata", "UTC"))) {
            body.set(invalid);
            assertThat(get(ID.toString()).statusCode()).isEqualTo(502);
        }
    }

    @Test
    void providerTimeoutIsSafe() throws Exception {
        delay.set(1500);
        assertThat(get(ID.toString()).statusCode()).isEqualTo(502);
        assertThat(calls.get()).isEqualTo(1);
    }

    private HttpResponse<String> get(String id) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/trips/" + id + "/weather"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
