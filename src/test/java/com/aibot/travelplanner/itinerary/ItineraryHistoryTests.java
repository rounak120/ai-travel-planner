package com.aibot.travelplanner.itinerary;

import com.aibot.travelplanner.trip.CreateTripRequest;
import com.aibot.travelplanner.trip.Trip;
import com.aibot.travelplanner.trip.TripService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") // The entire history feature works without AI.
class ItineraryHistoryTests {
    @Autowired ItineraryStore store;
    @Autowired TripService trips;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @LocalServerPort int port;
    Trip trip;
    Trip other;

    @BeforeEach
    void createOwnTrips() {
        var date = LocalDate.now().plusDays(30);
        var request = new CreateTripRequest("Jaipur", date, date, 2,
                new BigDecimal("1000.00"), "INR", List.of("history"));
        trip = trips.create(request);
        other = trips.create(request);
    }

    @AfterEach
    void cleanOwnTrips() {
        if (trip != null) jdbc.update("DELETE FROM trips WHERE id = ?", trip.id());
        if (other != null) jdbc.update("DELETE FROM trips WHERE id = ?", other.id());
    }

    @Test
    void pagesAreStableAcrossNewInsertAndSelectedVersionDoesNotChangeLatest() throws Exception {
        var oldest = save(trip, "10.25");
        save(trip, "20.50");
        var latest = save(trip, "30.75");
        var response = get(path(trip) + "/versions?limit=2");
        assertThat(response.statusCode()).isEqualTo(200);
        var first = json.readValue(response.body(), ItineraryHistory.class);
        assertThat(first.versions()).hasSize(2);
        assertThat(first.versions().getFirst().estimatedActivityCostTotal()).isEqualByComparingTo("30.75");
        assertThat(first.versions().getFirst().createdAt()).isNotNull();
        assertThat(first.nextBeforeId()).isEqualTo(first.versions().getLast().id());

        save(trip, "40.00"); // Does not shift the already-issued exclusive cursor.
        var second = json.readValue(get(path(trip) + "/versions?limit=2&beforeId="
                + first.nextBeforeId()).body(), ItineraryHistory.class);
        assertThat(second.versions()).hasSize(1);
        assertThat(second.nextBeforeId()).isNull();
        long oldId = second.versions().getFirst().id();
        var selectedResponse = get(path(trip) + "/versions/" + oldId);
        assertThat(selectedResponse.statusCode()).isEqualTo(200);
        var selected = json.readValue(selectedResponse.body(), ItineraryVersion.class);
        assertThat(selected.id()).isEqualTo(oldId);
        assertThat(selected.itinerary()).isEqualTo(oldest);
        assertThat(json.readValue(get(path(trip)).body(), ItineraryDraft.class)
                .estimatedActivityCostTotal()).isEqualByComparingTo("40.00");
        assertThat(get(path(other) + "/versions/" + oldId).statusCode()).isEqualTo(404);
        assertThat(latest.estimatedActivityCostTotal()).isEqualByComparingTo("30.75");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM itineraries WHERE trip_id = ?",
                Integer.class, trip.id())).isEqualTo(4);
    }

    @Test
    void emptyHistoryIsDifferentFromUnknownTripAndUnknownVersion() throws Exception {
        var response = get(path(trip) + "/versions");
        assertThat(response.statusCode()).isEqualTo(200);
        var history = json.readValue(response.body(), ItineraryHistory.class);
        assertThat(history.versions()).isEmpty();
        assertThat(history.nextBeforeId()).isNull();
        assertThat(get(path(trip) + "/versions/9223372036854775807").statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/trips/" + UUID.randomUUID() + "/itinerary/versions").statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/trips/not-a-uuid/itinerary/versions").statusCode()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?limit=0", "?limit=-1", "?limit=51", "?limit=bad", "?beforeId=0",
            "?beforeId=-1", "?beforeId=bad", "/0", "/-1", "/bad", "/9223372036854775808"})
    void rejectsInvalidPagingAndVersionInputs(String suffix) throws Exception {
        assertThat(get(path(trip) + "/versions" + suffix).statusCode()).isEqualTo(400);
    }

    @Test
    void defaultPageIsBoundedAndExactLastPageHasNoCursor() throws Exception {
        for (int i = 0; i < 21; i++) save(trip, "10.00");
        var page = json.readValue(get(path(trip) + "/versions").body(), ItineraryHistory.class);
        assertThat(page.versions()).hasSize(20);
        assertThat(page.nextBeforeId()).isNotNull();
        var all = json.readValue(get(path(trip) + "/versions?limit=21").body(), ItineraryHistory.class);
        assertThat(all.versions()).hasSize(21);
        assertThat(all.nextBeforeId()).isNull();
    }

    private ItineraryDraft save(Trip owner, String amount) {
        var cost = new BigDecimal(amount);
        return store.save(new ItineraryDraft(owner.id(), owner.destination(), "INR", owner.budget(), cost,
                List.of(new ItineraryDraft.Day(1, owner.startDate(),
                        List.of(new GeneratedItinerary.Activity("Fort", "Explore history", cost)), cost)),
                List.of("Unverified estimate")));
    }

    private String path(Trip owner) {
        return "/api/v1/trips/" + owner.id() + "/itinerary";
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(com.aibot.travelplanner.TestAuth.request(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
