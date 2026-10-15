package com.aibot.travelplanner.trip;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Translates HTTP requests into service calls and selects the success status codes. */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {
    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<Trip> create(@Valid @RequestBody CreateTripRequest request) {
        Trip trip = tripService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + trip.id())).body(trip);
    }

    @GetMapping("/{id}")
    public Trip get(@PathVariable UUID id) {
        return tripService.get(id);
    }

    @GetMapping
    public TripPage list(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size) {
        return tripService.list(page, size);
    }
}
