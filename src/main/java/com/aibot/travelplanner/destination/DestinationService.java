package com.aibot.travelplanner.destination;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;

/** A read-only external data client. Never receives a saved trip or calls AI. */
@Service
public class DestinationService {
    private final HttpClient http;
    private final JsonMapper json;
    private final String baseUrl;
    private final Duration timeout;

    public DestinationService(JsonMapper json,
            @Value("${travel.geocoding.base-url:https://geocoding-api.open-meteo.com}") String baseUrl,
            @Value("${travel.geocoding.timeout-ms:5000}") long timeoutMs) {
        this.json = json;
        this.baseUrl = baseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public DestinationSearch search(String name) {
        try {
            // Encode the user's input as one query value, never as a URL or extra parameters.
            var uri = URI.create(baseUrl + "/v1/search?name="
                    + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&count=5&language=en&format=json");
            var request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Accept", "application/json").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new DestinationUnavailableException();
            var root = json.readTree(response.body());
            if (root == null || !root.isObject() || root.path("error").asBoolean(false)) {
                throw new DestinationUnavailableException();
            }
            var results = root.get("results");
            var places = new ArrayList<DestinationSearch.Place>();
            // Open-Meteo omits results when there are no matches.
            if (results != null) {
                if (!results.isArray() || results.size() > 5) throw new DestinationUnavailableException();
                for (var place : results) {
                    var id = place.path("id");
                    var lat = place.path("latitude");
                    var lon = place.path("longitude");
                    String placeName = text(place, "name");
                    if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() < 1
                            || placeName == null || placeName.isBlank() || !lat.isNumber() || !lon.isNumber()
                            || !Double.isFinite(lat.asDouble()) || !Double.isFinite(lon.asDouble())
                            || Math.abs(lat.asDouble()) > 90 || Math.abs(lon.asDouble()) > 180) {
                        throw new DestinationUnavailableException();
                    }
                    places.add(new DestinationSearch.Place(id.asLong(), placeName, text(place, "country"),
                            text(place, "country_code"), text(place, "admin1"), lat.asDouble(),
                            lon.asDouble(), text(place, "timezone")));
                }
            }
            return new DestinationSearch(places, "Location data: GeoNames, provided by Open-Meteo (CC BY 4.0).",
                    "https://open-meteo.com/en/docs/geocoding-api");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DestinationUnavailableException();
        } catch (Exception exception) {
            // Do not expose provider bodies, stack traces, or user queries in the error.
            throw new DestinationUnavailableException();
        }
    }

    private String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw new DestinationUnavailableException();
        return value.asString();
    }

    @PreDestroy
    public void close() {
        http.close();
    }
}
