package com.aibot.travelplanner.itinerary;

import com.aibot.travelplanner.ai.ItineraryGenerator;
import com.aibot.travelplanner.trip.TripService;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Profile("ai")
public class ItineraryService {
    private final TripService trips;
    private final ItineraryGenerator generator;
    private final Validator validator;
    private final ItineraryStore store;

    public ItineraryService(TripService trips, ItineraryGenerator generator, Validator validator, ItineraryStore store) {
        this.trips = trips;
        this.generator = generator;
        this.validator = validator;
        this.store = store;
    }

    // Intentionally no transaction here: TripService's DB transaction ends before inference.
    public ItineraryDraft generate(UUID tripId) {
        var trip = trips.get(tripId);
        long dayCount = ChronoUnit.DAYS.between(trip.startDate(), trip.endDate()) + 1;
        if (dayCount < 1 || dayCount > 7) {
            throw new TripTooLongException();
        }
        var generated = generator.generate(trip);
        if (generated == null || !validator.validate(generated).isEmpty()
                || generated.days().size() != dayCount) {
            throw new InvalidItineraryException();
        }

        var days = new ArrayList<ItineraryDraft.Day>();
        BigDecimal total = new BigDecimal("0.00");
        for (int i = 0; i < dayCount; i++) {
            var day = generated.days().get(i);
            if (day.dayNumber() != i + 1) {
                throw new InvalidItineraryException();
            }
            var activities = day.activities().stream().map(activity ->
                    new GeneratedItinerary.Activity(activity.title().strip(), activity.description().strip(),
                            activity.estimatedCost().setScale(2))).toList();
            BigDecimal dayTotal = activities.stream().map(GeneratedItinerary.Activity::estimatedCost)
                    .reduce(new BigDecimal("0.00"), BigDecimal::add);
            total = total.add(dayTotal);
            days.add(new ItineraryDraft.Day(i + 1, trip.startDate().plusDays(i), activities, dayTotal));
        }
        if (total.compareTo(trip.budget()) > 0) {
            throw new InvalidItineraryException();
        }
        return store.save(new ItineraryDraft(trip.id(), trip.destination(), trip.currency(), trip.budget(), total, days,
                List.of("AI-generated draft: verify places, opening hours and prices before travelling.",
                        "Costs are estimates for the entire travel party in " + trip.currency() + ".",
                        "Only listed activities are costed; accommodation, intercity travel and unlisted meals are excluded.",
                        "No live weather, availability or bookings were checked. This is a saved AI draft, not a booking.")));
    }

    public static class TripTooLongException extends RuntimeException {
        public TripTooLongException() {
            super("This first local itinerary version supports trips of 1 to 7 days inclusive.");
        }
    }
}
