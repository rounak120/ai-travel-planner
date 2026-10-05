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

    @GetMapping("/versions")
    public ItineraryHistory history(@PathVariable UUID tripId,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) Long beforeId) {
        if (limit < 1 || limit > 50 || (beforeId != null && beforeId < 1)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit must be 1–50 and beforeId must be positive.");
        }
        return store.history(tripId, beforeId, limit);
    }

    @GetMapping("/versions/{versionId}")
    public ItineraryVersion version(@PathVariable UUID tripId, @PathVariable long versionId) {
        if (versionId < 1) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "versionId must be positive.");
        }
        return store.getVersion(tripId, versionId);
    }

    @ExceptionHandler({TripNotFoundException.class, ItineraryNotFoundException.class})
    public ProblemDetail missing(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
