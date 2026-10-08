package com.aibot.travelplanner.weather;

import com.aibot.travelplanner.trip.TripNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/weather")
public class WeatherController {
    private final WeatherService service;
    public WeatherController(WeatherService service) { this.service = service; }

    @GetMapping
    public TripWeather get(@PathVariable UUID tripId) { return service.get(tripId); }

    @ExceptionHandler(TripNotFoundException.class)
    public ProblemDetail missing(TripNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail invalid(ResponseStatusException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        if (exception instanceof WeatherEligibilityException eligibility) {
            problem.setProperty("code", eligibility.code());
            problem.setProperty("context", eligibility.context());
        }
        return problem;
    }

    @ExceptionHandler(WeatherUnavailableException.class)
    public ProblemDetail unavailable(WeatherUnavailableException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("Weather provider unavailable");
        return problem;
    }
}
