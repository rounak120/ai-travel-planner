package com.aibot.travelplanner.destination;

import com.aibot.travelplanner.trip.TripService;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class TripLocationService {
    private final TripService trips;
    private final DestinationService destinations;
    private final TripLocationStore store;

    public TripLocationService(TripService trips, DestinationService destinations, TripLocationStore store) {
        this.trips = trips;
        this.destinations = destinations;
        this.store = store;
    }

    // No transaction around the network request. Only the final store write is transactional.
    public SelectedDestination select(UUID tripId, SelectDestinationRequest request) {
        String name = request.searchName().strip();
        if (name.length() < 2 || name.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a valid destination search name.");
        }
        trips.get(tripId); // Reject missing trips before any external request.
        var matches = destinations.search(name);
        var place = matches.results().stream().filter(candidate -> candidate.id() == request.placeId())
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "The selected place is not in the current search results. Search again and choose a returned ID."));
        return store.save(new SelectedDestination(tripId, place, matches.attribution(), matches.sourceUrl(),
                Instant.now().truncatedTo(ChronoUnit.MICROS)));
    }

    public SelectedDestination get(UUID tripId) {
        trips.get(tripId);
        return store.find(tripId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "This trip has no selected location."));
    }
}
