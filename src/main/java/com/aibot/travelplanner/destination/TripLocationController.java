package com.aibot.travelplanner.destination;

import com.aibot.travelplanner.trip.TripNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/location")
public class TripLocationController {
    private final TripLocationService service;

    public TripLocationController(TripLocationService service) {
        this.service = service;
    }

    @PutMapping
    public SelectedDestination select(@PathVariable UUID tripId, @Valid @RequestBody SelectDestinationRequest request) {
        return service.select(tripId, request);
    }

    @GetMapping
    public SelectedDestination get(@PathVariable UUID tripId) {
        return service.get(tripId);
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ProblemDetail missingTrip(TripNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DestinationUnavailableException.class)
    public ProblemDetail unavailable(DestinationUnavailableException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("Destination provider unavailable");
        return problem;
    }
}
