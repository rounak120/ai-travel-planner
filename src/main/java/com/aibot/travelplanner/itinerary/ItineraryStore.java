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
        trips.get(draft.tripId());
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

    @Transactional(readOnly = true)
    public ItineraryHistory history(UUID tripId, Long beforeId, int limit) {
        if (limit < 1 || limit > 50 || (beforeId != null && beforeId < 1)) {
            throw new IllegalArgumentException("limit must be 1–50 and beforeId must be positive.");
        }
        trips.get(tripId);
        // An exclusive ID cursor stays stable when newer generations are inserted.
        String sql = """
                SELECT id, created_at, document->>'currency' AS currency,
                       (document->>'estimatedActivityCostTotal')::numeric AS cost
                FROM itineraries WHERE trip_id = ?
                """ + (beforeId == null ? "" : " AND id < ?") + " ORDER BY id DESC LIMIT ?";
        Object[] arguments = beforeId == null
                ? new Object[]{tripId, limit + 1} : new Object[]{tripId, beforeId, limit + 1};
        var rows = jdbc.query(sql, (row, index) -> new ItineraryHistory.Entry(row.getLong("id"),
                row.getTimestamp("created_at").toInstant(), row.getString("currency"),
                row.getBigDecimal("cost")), arguments);
        boolean hasMore = rows.size() > limit;
        var page = hasMore ? rows.subList(0, limit) : rows;
        return new ItineraryHistory(page, hasMore ? page.getLast().id() : null);
    }

    @Transactional(readOnly = true)
    public ItineraryVersion getVersion(UUID tripId, long versionId) {
        trips.get(tripId);
        // Scope by BOTH keys: an ID from another trip must never select its document.
        var rows = jdbc.query("""
                SELECT id, created_at, document::text FROM itineraries WHERE trip_id = ? AND id = ?
                """, (row, index) -> new ItineraryVersion(row.getLong("id"),
                row.getTimestamp("created_at").toInstant(),
                json.readValue(row.getString("document"), ItineraryDraft.class)), tripId, versionId);
        if (rows.isEmpty()) {
            throw new ItineraryNotFoundException("No saved itinerary version exists with that ID for this trip.");
        }
        return rows.getFirst();
    }
}
