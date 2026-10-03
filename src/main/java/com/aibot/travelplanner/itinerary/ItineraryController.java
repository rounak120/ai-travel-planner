package com.aibot.travelplanner.itinerary;

import com.aibot.travelplanner.trip.TripNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("ai")
@RequestMapping("/api/v1/trips/{tripId}/itinerary")
public class ItineraryController {
    private final ItineraryService service;

    public ItineraryController(ItineraryService service) {
        this.service = service;
    }

    @PostMapping
    public ItineraryDraft generate(@PathVariable UUID tripId) {
        return service.generate(tripId);
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ProblemDetail missingTrip(TripNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ItineraryService.TripTooLongException.class)
    public ProblemDetail unsupportedLength(ItineraryService.TripTooLongException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    @ExceptionHandler(InvalidItineraryException.class)
    public ProblemDetail invalidOutput(InvalidItineraryException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("Itinerary generation failed");
        return problem;
    }
}
