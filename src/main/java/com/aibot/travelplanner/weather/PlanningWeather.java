package com.aibot.travelplanner.weather;

/** Captures what weather context was available when this itinerary was generated. */
public record PlanningWeather(Status status, TripWeather forecast, String message) {
    public enum Status { AVAILABLE, NO_LOCATION, UNSUPPORTED, UNAVAILABLE }
}
