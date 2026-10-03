package com.aibot.travelplanner.itinerary;

/** Never carries raw model output or provider error bodies. */
public class InvalidItineraryException extends RuntimeException {
    public InvalidItineraryException() {
        super("The model did not produce a complete, valid itinerary within the trip's activity budget. No draft was saved.");
    }
}
