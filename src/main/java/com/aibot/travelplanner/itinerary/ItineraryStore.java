package com.aibot.travelplanner.itinerary;

import com.aibot.travelplanner.trip.TripService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/** Stores whole validated snapshots; no AI dependency or long-running transaction. */
@Repository
public class ItineraryStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final TripService trips;

    public ItineraryStore(JdbcTemplate jdbc, JsonMapper json, TripService trips) {
        this.jdbc = jdbc;
        this.json = json;
        this.trips = trips;
    }

    @Transactional
    public ItineraryDraft save(ItineraryDraft draft) {
        jdbc.update("INSERT INTO itineraries (trip_id, document) VALUES (?, CAST(? AS jsonb))",
                draft.tripId(), json.writeValueAsString(draft));
        return draft;
    }

    @Transactional(readOnly = true)
    public ItineraryDraft get(UUID tripId) {
        trips.get(tripId); // Distinguish a missing trip from a trip without a plan.
        var documents = jdbc.query("""
                SELECT document::text FROM itineraries WHERE trip_id = ? ORDER BY id DESC LIMIT 1
                """, (row, index) -> row.getString(1), tripId);
        if (documents.isEmpty()) {
            throw new ItineraryNotFoundException();
        }
        return json.readValue(documents.getFirst(), ItineraryDraft.class);
    }
}
