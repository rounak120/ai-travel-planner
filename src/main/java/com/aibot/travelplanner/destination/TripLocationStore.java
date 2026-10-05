package com.aibot.travelplanner.destination;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TripLocationStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public TripLocationStore(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public SelectedDestination save(SelectedDestination selected) {
        // Atomic replacement; no delete/insert gap and no duplicates for a trip.
        jdbc.update("""
                INSERT INTO trip_locations (trip_id, document) VALUES (?, CAST(? AS jsonb))
                ON CONFLICT (trip_id) DO UPDATE SET document = EXCLUDED.document
                """, selected.tripId(), json.writeValueAsString(selected));
        return selected;
    }

    @Transactional(readOnly = true)
    public Optional<SelectedDestination> find(UUID tripId) {
        return jdbc.query("SELECT document::text FROM trip_locations WHERE trip_id = ?",
                (row, index) -> json.readValue(row.getString(1), SelectedDestination.class), tripId)
                .stream().findFirst();
    }
}
