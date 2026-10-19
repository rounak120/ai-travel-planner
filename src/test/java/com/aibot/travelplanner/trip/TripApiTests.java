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

    @Test
    void acceptsExactDecimalBudgetFromBrowserForm() throws Exception {
        var request = validTrip();
        request.put("budget", "999999999999.99");
        var created = post(JSON.writeValueAsString(request));
        assertThat(created.statusCode()).isEqualTo(201);
        var fetched = get(created.headers().firstValue("location").orElseThrow());
        assertThat(JSON.readTree(fetched.body()).get("budget").decimalValue()).isEqualByComparingTo("999999999999.99");
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

    @Test
    void listsTripsNewestFirstWithDeterministicPagination() throws Exception {
        var ids = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            var created = post(JSON.writeValueAsString(validTrip()));
            assertThat(created.statusCode()).isEqualTo(201);
            String id = JSON.readTree(created.body()).get("id").asText();
            ids.add(id);
            jdbc.update("UPDATE trips SET created_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(Instant.parse(i == 0 ? "2200-01-01T00:00:00Z" : "2201-01-01T00:00:00Z")),
                    UUID.fromString(id));
        }
        var tied = new ArrayList<>(ids.subList(1, 3));
        tied.sort(java.util.Comparator.reverseOrder());
        var firstResponse = get("/api/v1/trips?page=0&size=2");
        assertThat(firstResponse.statusCode()).isEqualTo(200);
        var first = JSON.readTree(firstResponse.body());
        assertThat(first.get("trips").size()).isEqualTo(2);
        assertThat(first.get("trips").get(0).get("id").asText()).isEqualTo(tied.get(0));
        assertThat(first.get("trips").get(1).get("id").asText()).isEqualTo(tied.get(1));
        assertThat(first.get("trips").get(0).get("interests").size()).isEqualTo(2);
        assertThat(first.get("page").asInt()).isZero();
        assertThat(first.get("size").asInt()).isEqualTo(2);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(first.get("totalElements").asLong()).isGreaterThanOrEqualTo(3);
        var second = JSON.readTree(get("/api/v1/trips?page=1&size=2").body());
        assertThat(second.get("trips").get(0).get("id").asText()).isEqualTo(ids.get(0));
        var defaults = JSON.readTree(get("/api/v1/trips").body());
        assertThat(defaults.get("page").asInt()).isZero();
        assertThat(defaults.get("size").asInt()).isEqualTo(10);
        var empty = JSON.readTree(get("/api/v1/trips?page=100000&size=50").body());
        assertThat(empty.get("trips").isEmpty()).isTrue();
        assertThat(empty.get("hasNext").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=100001", "size=0", "size=51", "page=abc", "size=abc", "page=999999999999"})
    void rejectsInvalidPagination(String query) throws Exception {
        assertProblem(get("/api/v1/trips?" + query), 400);
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
        var response = send(com.aibot.travelplanner.TestAuth.request(uri("/api/v1/trips"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        if (response.statusCode() == 201) {
            createdTripIds.add(UUID.fromString(JSON.readTree(response.body()).get("id").asText()));
        }
        return response;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(com.aibot.travelplanner.TestAuth.request(uri(path)).GET());
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
