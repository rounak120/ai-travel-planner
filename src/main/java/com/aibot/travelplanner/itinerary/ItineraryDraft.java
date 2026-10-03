package com.aibot.travelplanner.itinerary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A validated AI draft, not a booking. Costs cover the whole party. */
public record ItineraryDraft(
        UUID tripId,
        String destination,
        String currency,
        BigDecimal tripBudget,
        BigDecimal estimatedActivityCostTotal,
        List<Day> days,
        List<String> warnings
) {
    public ItineraryDraft {
        days = List.copyOf(days);
        warnings = List.copyOf(warnings);
    }

    public record Day(int dayNumber, LocalDate date, List<GeneratedItinerary.Activity> activities,
                      BigDecimal estimatedActivityCost) {
        public Day {
            activities = List.copyOf(activities);
        }
    }
}
