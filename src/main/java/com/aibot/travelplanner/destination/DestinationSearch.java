package com.aibot.travelplanner.destination;

import java.util.List;

public record DestinationSearch(List<Place> results, String attribution, String sourceUrl) {
    public DestinationSearch {
        results = List.copyOf(results);
    }

    public record Place(long id, String name, String country, String countryCode,
                        String region, double latitude, double longitude, String timezone) {}
}
