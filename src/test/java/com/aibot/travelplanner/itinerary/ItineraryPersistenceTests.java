package com.aibot.travelplanner.itinerary;

import com.aibot.travelplanner.trip.CreateTripRequest;
import com.aibot.travelplanner.trip.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.net.URI;
import java.net.http.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ItineraryPersistenceTests {
    @Autowired ItineraryStore store;
    @Autowired TripService trips;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;

    @Test
    void committedSnapshotIsReadableOverHttpWithoutAiAndRollbackKeepsPrevious() throws Exception {
        var date = LocalDate.now().plusDays(30);
        var trip = trips.create(new CreateTripRequest("Jaipur", date, date, 2,
                new BigDecimal("1000.00"), "INR", List.of("history")));
        try {
            var cost = new BigDecimal("123.45");
            var draft = new ItineraryDraft(trip.id(), trip.destination(), "INR", trip.budget(), cost,
                    List.of(new ItineraryDraft.Day(1, date,
                            List.of(new GeneratedItinerary.Activity("Fort", "Explore history", cost)), cost)),
                    List.of("Unverified estimate"));
            store.save(draft);
            // Simulate JSON written before selectedLocation was added to the response record.
            jdbc.update("UPDATE itineraries SET document = document - 'selectedLocation' - 'weatherContext' WHERE trip_id = ?", trip.id());
            assertThat(store.get(trip.id()).selectedLocation()).isNull();
            assertThat(store.get(trip.id()).weatherContext()).isNull();
            assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                store.save(draft);
                throw new IllegalStateException("rollback test");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM itineraries WHERE trip_id = ?",
                    Integer.class, trip.id())).isEqualTo(1);
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(com.aibot.travelplanner.TestAuth.request(URI.create("http://127.0.0.1:" + port
                        + "/api/v1/trips/" + trip.id() + "/itinerary")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(json.readValue(response.body(), ItineraryDraft.class)).isEqualTo(draft);
            }
        } finally {
            jdbc.update("DELETE FROM trips WHERE id = ?", trip.id());
        }
    }
}
