package com.aibot.travelplanner.destination;

import java.time.Instant;
import java.util.UUID;

public record SelectedDestination(UUID tripId, DestinationSearch.Place place,
        String attribution, String sourceUrl, Instant selectedAt) {}
