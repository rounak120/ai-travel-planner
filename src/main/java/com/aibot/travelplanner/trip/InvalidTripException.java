package com.aibot.travelplanner.trip;

import java.util.Map;

/** A travel rule failed; the HTTP layer decides how to present it to the caller. */
public class InvalidTripException extends RuntimeException {
    private final Map<String, String> errors;

    public InvalidTripException(Map<String, String> errors) {
        super("Check the highlighted trip fields.");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
