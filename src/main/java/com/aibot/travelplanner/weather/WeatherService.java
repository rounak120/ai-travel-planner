package com.aibot.travelplanner.weather;

import com.aibot.travelplanner.destination.TripLocationService;
import com.aibot.travelplanner.trip.TripService;
import com.aibot.travelplanner.trip.Trip;
import com.aibot.travelplanner.destination.SelectedDestination;
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
        return forecast(trip, locations.get(tripId));
    }

    /** Uses the captured location, never re-reads it during itinerary generation. */
    public PlanningWeather forPlanning(Trip trip, SelectedDestination selected) {
        if (selected == null) {
            return new PlanningWeather(PlanningWeather.Status.NO_LOCATION, null,
                    "No forecast used: select a location to enable weather-aware planning.");
        }
        try {
            return new PlanningWeather(PlanningWeather.Status.AVAILABLE, forecast(trip, selected),
                    "Forecast supplied to the AI for these trip dates. Predictions may change; null measurements are unavailable.");
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 422) throw exception;
            return new PlanningWeather(PlanningWeather.Status.UNSUPPORTED, null,
                    "No forecast used: " + exception.getReason());
        } catch (WeatherUnavailableException exception) {
            return new PlanningWeather(PlanningWeather.Status.UNAVAILABLE, null,
                    "No forecast used: the weather provider was unavailable or returned invalid data.");
        }
    }

    private TripWeather forecast(Trip trip, SelectedDestination selected) {
        var place = selected.place();
        ZoneId zone;
        try {
            zone = ZoneId.of(place.timezone());
        } catch (RuntimeException exception) {
            throw WeatherEligibilityException.invalidTimezone();
        }
        var today = LocalDate.now(clock.withZone(zone));
        var lastDay = today.plusDays(15);
        if (trip.startDate().isBefore(today) || trip.endDate().isAfter(lastDay)) {
            throw WeatherEligibilityException.outsideWindow(trip.startDate(), trip.endDate(), today, lastDay, zone);
        }
        var days = client.forecast(place, trip.startDate(), trip.endDate());
        return new TripWeather(trip.id(), place, zone.getId(), clock.instant(), days,
                "Weather data: Open-Meteo (CC BY 4.0). Location data: GeoNames via Open-Meteo.", "https://open-meteo.com/",
                List.of("Forecasts may change; recheck near departure. This is not a weather alert service.",
                        "Null values mean unavailable forecast data, not zero. Precipitation includes rain and snow water equivalent.",
                        "Forecast is for the selected location and exact trip dates; it does not verify activities."));
    }
}
