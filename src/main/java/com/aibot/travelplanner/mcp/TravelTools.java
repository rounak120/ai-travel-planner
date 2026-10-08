package com.aibot.travelplanner.mcp;

import com.aibot.travelplanner.destination.DestinationService;
import com.aibot.travelplanner.destination.DestinationUnavailableException;
import com.aibot.travelplanner.trip.TripNotFoundException;
import com.aibot.travelplanner.weather.WeatherService;
import com.aibot.travelplanner.weather.WeatherUnavailableException;
import com.aibot.travelplanner.weather.WeatherEligibilityException;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/** Protocol adapter only: existing services own provider and trip rules. */
@Component
@Profile("mcp")
public class TravelTools {
    private final DestinationService destinations;
    private final WeatherService weather;
    private final JsonMapper json;

    public TravelTools(DestinationService destinations, WeatherService weather, JsonMapper json) {
        this.destinations = destinations;
        this.weather = weather;
        this.json = json;
    }

    @McpTool(name = "search_destinations", description = "Search up to five destination locations by name. Returns country, coordinates and attribution; does not select or save a place. Sends the search text to Open-Meteo.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = true))
    public CallToolResult searchDestinations(@McpToolParam(description = "Place name, 2–100 characters", required = true) String name) {
        if (name == null) return error("INVALID_ARGUMENT", "Provide a destination name.");
        String query = name.strip();
        if (query.length() < 2 || query.length() > 100 || query.chars().anyMatch(Character::isISOControl)) {
            return error("INVALID_ARGUMENT", "name must contain 2–100 characters without control characters.");
        }
        try {
            return success(destinations.search(query));
        } catch (DestinationUnavailableException exception) {
            return error("PROVIDER_UNAVAILABLE", "Destination search is temporarily unavailable.");
        } catch (RuntimeException exception) {
            return error("TOOL_UNAVAILABLE", "Destination search could not complete.");
        }
    }

    @McpTool(name = "get_trip_weather", description = "Fetch a fresh forecast for an existing trip UUID and its saved selected location. All dates must be within the local 16-day forecast window. No trip or itinerary changes; not a weather-alert service.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = true))
    public CallToolResult getTripWeather(@McpToolParam(description = "Existing trip UUID, not a destination name", required = true) String tripId) {
        UUID id;
        try {
            id = UUID.fromString(tripId);
            if (!id.toString().equalsIgnoreCase(tripId)) throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            return error("INVALID_ARGUMENT", "tripId must be a full UUID.");
        }
        try {
            return success(weather.get(id));
        } catch (TripNotFoundException exception) {
            return error("NOT_FOUND", "No trip exists with that ID.");
        } catch (WeatherEligibilityException exception) {
            return CallToolResult.builder().isError(true).addTextContent(exception.getReason())
                    .structuredContent(Map.of("code", exception.code(), "message", exception.getReason(),
                            "context", exception.context())).build();
        } catch (ResponseStatusException exception) {
            return switch (exception.getStatusCode().value()) {
                case 404 -> error("NOT_FOUND", "This trip has no selected location.");
                case 422 -> error("UNSUPPORTED", "Weather is not supported for this request. No specific cause was provided.");
                default -> error("TOOL_UNAVAILABLE", "Trip weather could not complete.");
            };
        } catch (WeatherUnavailableException exception) {
            return error("PROVIDER_UNAVAILABLE", "The weather provider could not return a valid forecast.");
        } catch (RuntimeException exception) {
            return error("TOOL_UNAVAILABLE", "Trip weather could not complete.");
        }
    }

    private CallToolResult success(Object data) {
        String content = json.writeValueAsString(data);
        return CallToolResult.builder().isError(false).structuredContent(json.readValue(content, Map.class))
                .addTextContent(content).build();
    }

    private CallToolResult error(String code, String message) {
        return CallToolResult.builder().isError(true).structuredContent(Map.of("code", code, "message", message))
                .addTextContent(message).build();
    }
}
