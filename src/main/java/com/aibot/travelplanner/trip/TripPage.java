package com.aibot.travelplanner.trip;

import java.util.List;

/** Stable API contract rather than exposing Spring's internal Page serialization. */
public record TripPage(List<Trip> trips, int page, int size, long totalElements,
                       int totalPages, boolean hasNext) {
    public TripPage {
        trips = List.copyOf(trips);
    }
}
