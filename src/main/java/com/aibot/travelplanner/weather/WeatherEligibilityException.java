package com.aibot.travelplanner.weather;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/** Public, application-owned validation details, safe to expose through REST and MCP. */
public final class WeatherEligibilityException extends ResponseStatusException {
    private final String code;
    private final Map<String, String> context;

    private WeatherEligibilityException(String code, String message, Map<String, String> context) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, message);
        this.code = code;
        this.context = Map.copyOf(context);
    }

    public static WeatherEligibilityException invalidTimezone() {
        return new WeatherEligibilityException("INVALID_TIMEZONE",
                "Select a location with a valid timezone before requesting weather.", Map.of());
    }

    public static WeatherEligibilityException outsideWindow(LocalDate start, LocalDate end,
                                                            LocalDate today, LocalDate lastDay, ZoneId zone) {
        return new WeatherEligibilityException("DATES_OUTSIDE_FORECAST_WINDOW",
                "Trip dates " + start + " through " + end + " are outside the supported forecast window of "
                        + today + " through " + lastDay + " in " + zone.getId()
                        + ". All trip dates must be inside that window; past dates and dates beyond it are not supported by this endpoint.",
                Map.of("tripStartDate", start.toString(), "tripEndDate", end.toString(),
                        "forecastStartDate", today.toString(), "forecastEndDate", lastDay.toString(), "timezone", zone.getId()));
    }

    public String code() { return code; }
    public Map<String, String> context() { return context; }
}
