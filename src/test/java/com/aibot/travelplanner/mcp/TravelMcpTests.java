package com.aibot.travelplanner.mcp;

import com.aibot.travelplanner.destination.*;
import com.aibot.travelplanner.trip.TripNotFoundException;
import com.aibot.travelplanner.weather.*;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MCP handshake, discovery and calls over HTTP; service results are deterministic fakes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"mcp", "mcp-client", "test"})
class TravelMcpTests {
    @LocalServerPort int port;
    @MockitoBean DestinationService destinations;
    @MockitoBean WeatherService weather;
    McpSyncClient client;

    @BeforeEach
    void connect() {
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp").openConnectionOnStartup(false).build();
        client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10)).build();
        var initialized = client.initialize();
        assertThat(initialized.serverInfo().name()).isEqualTo("travel-planner-tools");
        verifyNoInteractions(destinations, weather);
    }

    @AfterEach
    void close() { if (client != null) client.close(); }

    private CallToolResult call(String name, Map<String, Object> arguments) {
        return client.callTool(CallToolRequest.builder(name).arguments(arguments).build());
    }

    @Test
    void discoversExactlyTwoReadOnlyToolsWithRequiredInputSchemas() {
        var tools = client.listTools().tools();
        assertThat(tools).extracting(Tool::name).containsExactlyInAnyOrder("search_destinations", "get_trip_weather");
        for (var tool : tools) {
            assertThat(tool.annotations().readOnlyHint()).isTrue();
            assertThat(tool.annotations().destructiveHint()).isFalse();
            assertThat(tool.annotations().openWorldHint()).isTrue();
            assertThat(tool.inputSchema().get("required").toString())
                    .contains(tool.name().equals("search_destinations") ? "name" : "tripId");
        }
        assertThat(client.getServerCapabilities().resources()).isNull();
        assertThat(client.getServerCapabilities().prompts()).isNull();
        verifyNoInteractions(destinations, weather);
    }

    @Test
    void searchReturnsStructuredDataAndAttribution() {
        when(destinations.search("Jaipur")).thenReturn(new DestinationSearch(List.of(
                new DestinationSearch.Place(1269515, "Jaipur", "India", "IN", "Rajasthan", 26.91, 75.78, "Asia/Kolkata")),
                "GeoNames via Open-Meteo", "https://open-meteo.com/"));
        var result = call("search_destinations", Map.of("name", " Jaipur "));
        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent().toString()).contains("Jaipur", "GeoNames", "Rajasthan");
        assertThat(result.content()).hasSize(1);
        verify(destinations).search("Jaipur");
        verifyNoInteractions(weather);
    }

    @Test
    void weatherReturnsExistingServiceForecastWithoutPlanning() {
        UUID id = UUID.randomUUID();
        var place = new DestinationSearch.Place(1, "Jaipur", "India", "IN", "Rajasthan", 26.91, 75.78, "Asia/Kolkata");
        when(weather.get(id)).thenReturn(new TripWeather(id, place, "Asia/Kolkata", Instant.parse("2026-10-05T10:00:00Z"),
                List.of(new TripWeather.Day(LocalDate.of(2026, 10, 6), 20.0, 30.0, 0.0)),
                "Open-Meteo", "https://open-meteo.com/", List.of("Forecasts may change")));
        var result = call("get_trip_weather", Map.of("tripId", id.toString()));
        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent().toString()).contains("2026-10-06", "Open-Meteo", "Forecasts may change");
        verify(weather).get(id);
        verifyNoInteractions(destinations);
    }

    @Test
    void invalidInputsDoNotReachServices() {
        for (String name : List.of("", "a", "ab\ncd", "a".repeat(101))) {
            var result = call("search_destinations", Map.of("name", name));
            assertThat(result.isError()).isTrue();
            assertThat(result.structuredContent().toString()).contains("INVALID_ARGUMENT");
        }
        for (String id : List.of("invalid", "1-1-1-1-1")) {
            assertThat(call("get_trip_weather", Map.of("tripId", id)).isError()).isTrue();
        }
        verifyNoInteractions(destinations, weather);
    }

    @Test
    void safeErrorsPreserveMcpToolErrorSemantics() {
        UUID id = UUID.randomUUID();
        when(weather.get(id)).thenThrow(new TripNotFoundException(id));
        assertThat(call("get_trip_weather", Map.of("tripId", id.toString())).structuredContent().toString()).contains("NOT_FOUND");
        reset(weather);
        when(weather.get(id)).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "private detail"));
        var unsupported = call("get_trip_weather", Map.of("tripId", id.toString()));
        assertThat(unsupported.isError()).isTrue();
        assertThat(unsupported.structuredContent().toString()).contains("UNSUPPORTED").doesNotContain("private detail");
        reset(weather);
        when(weather.get(id)).thenThrow(new WeatherUnavailableException());
        assertThat(call("get_trip_weather", Map.of("tripId", id.toString())).structuredContent().toString()).contains("PROVIDER_UNAVAILABLE");
        when(destinations.search("Jaipur")).thenThrow(new RuntimeException("database-password-sensitive-detail"));
        var failed = call("search_destinations", Map.of("name", "Jaipur"));
        assertThat(failed.isError()).isTrue();
        assertThat(failed.toString()).contains("TOOL_UNAVAILABLE").doesNotContain("database-password-sensitive-detail");
    }

    @Test
    void unknownWriteToolCannotBeCalled() {
        assertThatThrownBy(() -> call("create_trip", Map.of())).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(destinations, weather);
    }

    private java.net.http.HttpResponse<String> get(String path) throws Exception {
        try (var http = java.net.http.HttpClient.newHttpClient()) {
            return http.send(java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(20)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void restBridgeDiscoversToolsWithoutCallingProviders() throws Exception {
        var response = get("/api/v1/mcp/tools");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("search_destinations", "get_trip_weather");
        verifyNoInteractions(destinations, weather);
    }

    @Test
    void restBridgeSearchUsesMcpAndPreservesStructuredResult() throws Exception {
        when(destinations.search("Jaipur")).thenReturn(new DestinationSearch(List.of(), "GeoNames", "https://open-meteo.com/"));
        var response = get("/api/v1/mcp/destinations?name=%20Jaipur%20");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"isError\":false", "structuredContent", "GeoNames");
        verify(destinations).search("Jaipur");
        verifyNoInteractions(weather);
    }

    @Test
    void restBridgePreservesToolErrorsWithoutLeakingInternals() throws Exception {
        UUID id = UUID.randomUUID();
        when(weather.get(id)).thenThrow(new TripNotFoundException(id));
        var response = get("/api/v1/mcp/trips/" + id + "/weather");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"isError\":true", "NOT_FOUND");
        verify(weather).get(id);
    }

    @Test
    void restBridgeRejectsInvalidArgumentsAndHasNoGenericToolRoute() throws Exception {
        assertThat(get("/api/v1/mcp/destinations?name=a").statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/mcp/trips/1-1-1-1-1/weather").statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/mcp/tools/create_trip").statusCode()).isEqualTo(404);
        verifyNoInteractions(destinations, weather);
    }

    @Test
    void rejectsForeignOriginOverHttp() throws Exception {
        try (var http = java.net.http.HttpClient.newHttpClient()) {
            var response = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/mcp"))
                    .header("Origin", "https://untrusted.example").header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                    .timeout(Duration.ofSeconds(5)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(403);
        }
        verifyNoInteractions(destinations, weather);
    }
}
