package com.aibot.travelplanner.trip;

import com.aibot.travelplanner.destination.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Revalidate the provider selection before starting one atomic database write. */
@Service
public class LocatedTripCreation {
    private final DestinationService destinations;
    private final TripService trips;
    private final TripLocationStore locations;
    private final TransactionTemplate transaction;
    private final jakarta.persistence.EntityManager entities;
    public LocatedTripCreation(DestinationService destinations, TripService trips,
                               TripLocationStore locations, PlatformTransactionManager manager,
                               jakarta.persistence.EntityManager entities) {
        this.destinations=destinations;this.trips=trips;this.locations=locations;
        this.entities=entities;
        this.transaction=new TransactionTemplate(manager);
    }
    public Trip create(CreateLocatedTripRequest request) {
        String query=request.location().searchName().strip();
        if(query.length()<2 || query.chars().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid destination search.");
        var results=destinations.search(query);
        var place=results.results().stream().filter(candidate->candidate.id()==request.location().placeId())
                .findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "The selected destination is no longer in the search results. Search again and select a location."));
        var input=request.trip();
        if(place.name()==null || place.name().isBlank() || place.name().length()>200)
            throw new DestinationUnavailableException();
        var normalized=new CreateTripRequest(place.name(),input.startDate(),input.endDate(),input.travelers(),
                input.budget(),input.currency(),input.interests());
        return transaction.execute(status->{
            var trip=trips.create(normalized);
            // JDBC location storage needs the JPA parent insert flushed, not committed.
            entities.flush();
            locations.save(new SelectedDestination(trip.id(),place,results.attribution(),results.sourceUrl(),
                    Instant.now().truncatedTo(ChronoUnit.MICROS)));
            return trip;
        });
    }
}
