package com.aibot.travelplanner.destination;

public class DestinationUnavailableException extends RuntimeException {
    public DestinationUnavailableException() {
        super("Destination search is temporarily unavailable. Try again later.");
    }
}
