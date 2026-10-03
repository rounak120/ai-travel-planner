package com.aibot.travelplanner.trip;

import java.util.UUID;

public class TripNotFoundException extends RuntimeException {
    public TripNotFoundException(UUID id) {
        super("No trip exists with ID " + id + ".");
    }
}
