package com.aibot.travelplanner.trip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TripApiTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> createdTripIds = new ArrayList<>();

    @AfterEach
    void removeOnlyTripsCreatedByThisTest() {
        // HTTP requests commit on a server thread; a test-thread rollback cannot clean them.
        createdTripIds.forEach(id -> jdbc.update("DELETE FROM trips WHERE id = ?", id));
    }

    @Test
    void createsTripAndRetrievesItFromLocation() throws Exception {
        var request = validTrip();
        request.put("destination", "  Jaipur  ");
        request.put("interests", List.of(" food ", "history"));

        var created = post(JSON.writeValueAsString(request));
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("content-type").orElse("")).startsWith("application/json");
        JsonNode trip = JSON.readTree(created.body());
        UUID.fromString(trip.get("id").asText());
        Instant.parse(trip.get("createdAt").asText());
        assertThat(trip.get("destination").asText()).isEqualTo("Jaipur");
        assertThat(trip.get("startDate").asText()).isEqualTo(request.get("startDate"));
        assertThat(trip.get("endDate").asText()).isEqualTo(request.get("endDate"));
        assertThat(trip.get("travelers").asInt()).isEqualTo(2);
        assertThat(trip.get("budget").decimalValue()).isEqualByComparingTo("50000.25");
        assertThat(trip.get("currency").asText()).isEqualTo("INR");
        assertThat(trip.get("interests").get(0).asText()).isEqualTo("food");

        UUID id = UUID.fromString(trip.get("id").asText());
        assertThat(jdbc.queryForObject("SELECT destination FROM trips WHERE id = ?", String.class, id))
                .isEqualTo("Jaipur");
        assertThat(jdbc.queryForList("""
                SELECT interest FROM trip_interests WHERE trip_id = ? ORDER BY interest_order
                """, String.class, id)).containsExactly("food", "history");

        String location = created.headers().firstValue("location").orElseThrow();
        assertThat(location).isEqualTo("/api/v1/trips/" + trip.get("id").asText());
        var fetched = get(location);
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(fetched.body())).isEqualTo(trip);

        var second = post(JSON.writeValueAsString(request));
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(JSON.readTree(second.body()).get("id")).isNotEqualTo(trip.get("id"));
    }

    @Test
    void acceptsSameDayTripStartingTodayWithNoInterests() throws Exception {
        var request = validTrip();
        request.put("startDate", LocalDate.now().toString());
        request.put("endDate", LocalDate.now().toString());
        request.put("interests", List.of());
        var created = post(JSON.writeValueAsString(request));
        assertThat(created.statusCode()).isEqualTo(201);
        var fetched = get(created.headers().firstValue("location").orElseThrow());
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(fetched.body())).isEqualTo(JSON.readTree(created.body()));
    }

    @ParameterizedTest(name = "rejects invalid {0}: {1}")
    @MethodSource("invalidFields")
    void rejectsInvalidFields(String field, Object value, String errorField) throws Exception {
        var request = validTrip();
        request.put(field, value);
        JsonNode problem = assertProblem(post(JSON.writeValueAsString(request)), 400);
        assertThat(problem.get("errors").has(errorField)).isTrue();
        assertThat(problem.get("errors").get(errorField).asText()).isNotBlank();
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("destination", " ", "destination"),
                Arguments.of("destination", "x".repeat(201), "destination"),
                Arguments.of("startDate", LocalDate.now().minusDays(1).toString(), "startDate"),
                Arguments.of("endDate", LocalDate.now().plusDays(1).toString(), "endDate"),
                Arguments.of("travelers", 0, "travelers"),
                Arguments.of("travelers", 101, "travelers"),
                Arguments.of("budget", -1, "budget"),
                Arguments.of("budget", 0, "budget"),
                Arguments.of("budget", new BigDecimal("1.001"), "budget"),
                Arguments.of("budget", new BigDecimal("1000000000000"), "budget"),
                Arguments.of("currency", "inr", "currency"),
                Arguments.of("currency", "ZZZ", "currency"),
                Arguments.of("interests", List.of(" "), "interests[0]"),
                Arguments.of("interests", List.of("x".repeat(81)), "interests[0]"),
                Arguments.of("interests", java.util.Collections.nCopies(21, "food"), "interests")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"destination", "startDate", "endDate", "travelers", "budget", "currency", "interests"})
    void rejectsMissingRequiredFields(String field) throws Exception {
        var request = validTrip();
        request.remove(field);
        JsonNode problem = assertProblem(post(JSON.writeValueAsString(request)), 400);
        assertThat(problem.get("errors").has(field)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "null", "[]", "{\"startDate\":\"not-a-date\"}", "{\"travelers\":\"many\"}"})
    void rejectsMalformedBodies(String body) throws Exception {
        assertProblem(post(body), 400);
    }

    @Test
    void returns404ForUnknownTrip() throws Exception {
        assertProblem(get("/api/v1/trips/" + UUID.randomUUID()), 404);
    }

    @Test
    void returns400ForMalformedId() throws Exception {
        assertProblem(get("/api/v1/trips/not-a-uuid"), 400);
    }

    @Test
    void storedInterestsCannotBeMutated() {
        var interests = new ArrayList<>(List.of("food"));
        var trip = new Trip(UUID.randomUUID(), "Jaipur", LocalDate.now(), LocalDate.now(),
                2, new BigDecimal("50000"), "INR", interests, Instant.now());
        interests.add("history");
        assertThat(trip.interests()).containsExactly("food");
        assertThatThrownBy(() -> trip.interests().add("history"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Map<String, Object> validTrip() {
        var request = new LinkedHashMap<String, Object>();
        request.put("destination", "Jaipur");
        request.put("startDate", LocalDate.now().plusDays(30).toString());
        request.put("endDate", LocalDate.now().plusDays(34).toString());
        request.put("travelers", 2);
        request.put("budget", new BigDecimal("50000.25"));
        request.put("currency", "INR");
        request.put("interests", List.of("food", "history"));
        return request;
    }

    private JsonNode assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("application/problem+json");
        JsonNode problem = JSON.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("detail").asText()).isNotBlank();
        return problem;
    }

    private HttpResponse<String> post(String body) throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/v1/trips"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        if (response.statusCode() == 201) {
            createdTripIds.add(UUID.fromString(JSON.readTree(response.body()).get("id").asText()));
        }
        return response;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
