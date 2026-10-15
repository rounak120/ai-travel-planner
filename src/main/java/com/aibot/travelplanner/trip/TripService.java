package com.aibot.travelplanner.trip;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Runs trip use cases and defines the database transaction boundaries. */
@Service
public class TripService {
    private final TripRepository tripRepository;

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    // Field-level constraints have already been checked by @Valid at the HTTP boundary.
    @Transactional
    public Trip create(CreateTripRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.endDate().isBefore(request.startDate())) {
            errors.put("endDate", "must be on or after startDate");
        }
        try {
            Currency.getInstance(request.currency());
        } catch (IllegalArgumentException exception) {
            errors.put("currency", "must be a recognized ISO 4217 currency code, such as INR or USD");
        }
        if (!errors.isEmpty()) {
            throw new InvalidTripException(errors);
        }

        // Match PostgreSQL's money scale and timestamp precision in POST and GET responses.
        var entity = new TripEntity(request.destination().strip(), request.startDate(),
                request.endDate(), request.travelers(), request.budget().setScale(2), request.currency(),
                request.interests().stream().map(String::strip).toList(),
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        return tripRepository.save(entity).toTrip();
    }

    @Transactional(readOnly = true)
    public Trip get(UUID id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new TripNotFoundException(id))
                .toTrip();
    }

    @Transactional(readOnly = true)
    public TripPage list(int page, int size) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (page < 0 || page > 100000) errors.put("page", "must be between 0 and 100000");
        if (size < 1 || size > 50) errors.put("size", "must be between 1 and 50");
        if (!errors.isEmpty()) throw new InvalidTripException(errors);
        // UUID breaks timestamp ties so unchanged data has deterministic page boundaries.
        var result = tripRepository.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new TripPage(result.getContent().stream().map(TripEntity::toTrip).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }
}
