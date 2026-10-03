package com.aibot.travelplanner.trip;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Immutable API response, separate from the database entity and its lazy collection. */
public record Trip(
        UUID id,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        int travelers,
        BigDecimal budget,
        String currency,
        List<String> interests,
        Instant createdAt
) {
    public Trip {
        // A record alone does not stop callers from mutating a supplied list.
        interests = List.copyOf(interests);
    }
}
