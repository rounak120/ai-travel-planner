package com.aibot.travelplanner.weather;

import com.aibot.travelplanner.destination.TripLocationService;
import com.aibot.travelplanner.trip.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
public class WeatherService {
    private final TripService trips;
    private final TripLocationService locations;
    private final WeatherClient client;
    private final Clock clock;

    public WeatherService(TripService trips, TripLocationService locations, WeatherClient client, Clock clock) {
        this.trips = trips;
        this.locations = locations;
        this.client = client;
        this.clock = clock;
    }

    public TripWeather get(UUID tripId) {
        var trip = trips.get(tripId);
        var place = locations.get(tripId).place();
        ZoneId zone;
        try {
            zone = ZoneId.of(place.timezone());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Select a location with a valid timezone before requesting weather.");
        }
        var today = LocalDate.now(clock.withZone(zone));
        var lastDay = today.plusDays(15);
        if (trip.startDate().isBefore(today) || trip.endDate().isAfter(lastDay)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "This endpoint requires all trip dates within " + today + " through " + lastDay
                            + " in " + zone + ". No forecast is available here for the full trip yet or its dates are past.");
        }
        var days = client.forecast(place, trip.startDate(), trip.endDate());
        return new TripWeather(tripId, place, zone.getId(), clock.instant(), days,
                "Weather data: Open-Meteo (CC BY 4.0). Location data: GeoNames via Open-Meteo.", "https://open-meteo.com/",
                List.of("Forecasts may change; recheck near departure. This is not a weather alert service.",
                        "Null values mean unavailable forecast data, not zero. Precipitation includes rain and snow water equivalent.",
                        "Forecast is for the selected location and exact trip dates; no itinerary was changed."));
    }
}
