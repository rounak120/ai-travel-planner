package com.aibot.travelplanner.itinerary;

import com.aibot.travelplanner.trip.TripNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Reading saved data must not require a running model or the AI profile. */
@RestController
@RequestMapping("/api/v1/trips/{tripId}/itinerary")
public class SavedItineraryController {
    private final ItineraryStore store;

    public SavedItineraryController(ItineraryStore store) {
        this.store = store;
    }

    @GetMapping
    public ItineraryDraft get(@PathVariable UUID tripId) {
        return store.get(tripId);
    }

    @ExceptionHandler({TripNotFoundException.class, ItineraryNotFoundException.class})
    public ProblemDetail missing(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
