package com.aibot.travelplanner.itinerary;

public class ItineraryNotFoundException extends RuntimeException {
    public ItineraryNotFoundException(String message) {
        super(message);
    }

    public ItineraryNotFoundException() {
        super("This trip has no saved itinerary. Generate one first using POST.");
    }
}
