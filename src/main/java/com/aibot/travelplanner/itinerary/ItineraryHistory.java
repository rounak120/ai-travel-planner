package com.aibot.travelplanner.itinerary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ItineraryHistory(List<Entry> versions, Long nextBeforeId) {
    public ItineraryHistory {
        versions = List.copyOf(versions);
    }

    public record Entry(long id, Instant createdAt, String currency, BigDecimal estimatedActivityCostTotal) {}
}
