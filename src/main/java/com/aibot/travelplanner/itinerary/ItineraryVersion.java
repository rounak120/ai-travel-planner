package com.aibot.travelplanner.itinerary;

import java.time.Instant;

/** The database ID identifies a snapshot; it is not a per-trip version number. */
public record ItineraryVersion(long id, Instant createdAt, ItineraryDraft itinerary) {}
