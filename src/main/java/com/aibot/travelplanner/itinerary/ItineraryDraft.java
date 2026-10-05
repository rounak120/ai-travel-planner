package com.aibot.travelplanner.itinerary;

import com.aibot.travelplanner.destination.SelectedDestination;

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
        List<String> warnings,
        SelectedDestination selectedLocation
) {
    public ItineraryDraft {
        days = List.copyOf(days);
        warnings = List.copyOf(warnings);
    }

    // Existing callers and pre-location JSON snapshots remain supported.
    public ItineraryDraft(UUID tripId, String destination, String currency, BigDecimal tripBudget,
            BigDecimal estimatedActivityCostTotal, List<Day> days, List<String> warnings) {
        this(tripId, destination, currency, tripBudget, estimatedActivityCostTotal, days, warnings, null);
    }

    public record Day(int dayNumber, LocalDate date, List<GeneratedItinerary.Activity> activities,
                      BigDecimal estimatedActivityCost) {
        public Day {
            activities = List.copyOf(activities);
        }
    }
}
