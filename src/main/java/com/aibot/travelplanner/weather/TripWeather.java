package com.aibot.travelplanner.weather;

import com.aibot.travelplanner.destination.DestinationSearch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripWeather(UUID tripId, DestinationSearch.Place location, String timezone,
        Instant fetchedAt, List<Day> days, String attribution, String sourceUrl, List<String> warnings) {
    public TripWeather {
        days = List.copyOf(days);
        warnings = List.copyOf(warnings);
    }
    public record Day(LocalDate date, Double minimumTemperatureCelsius,
                      Double maximumTemperatureCelsius, Double precipitationMm) {}
}
