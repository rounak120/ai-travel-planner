package com.aibot.travelplanner.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/** Explicit, read-only MCP calls to this application's loopback server. */
@Service
@Profile("mcp-client")
public class TravelMcpClient {
    private static final Set<String> ALLOWED = Set.of("search_destinations", "get_trip_weather");
    private final Environment environment;

    public TravelMcpClient(Environment environment) { this.environment = environment; }

    public List<Tool> tools() {
        return connected(client -> client.listTools().tools().stream()
                .filter(tool -> ALLOWED.contains(tool.name())).toList());
    }

    public CallToolResult search(String name) {
        String query = name == null ? "" : name.strip();
        if (query.length() < 2 || query.length() > 100 || query.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "name must contain 2–100 characters without control characters.");
        }
        return call("search_destinations", Map.of("name", query));
    }

    public CallToolResult weather(String tripId) {
        try {
            if (tripId == null || !UUID.fromString(tripId).toString().equalsIgnoreCase(tripId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tripId must be a full UUID.");
        }
        return call("get_trip_weather", Map.of("tripId", tripId));
    }

    private CallToolResult call(String name, Map<String, Object> arguments) {
        if (!ALLOWED.contains(name)) throw new IllegalArgumentException("Tool is not allowed.");
        return connected(client -> client.callTool(CallToolRequest.builder(name).arguments(arguments).build()));
    }

    private <T> T connected(Function<McpSyncClient, T> action) {
        // Resolve only when requested: the embedded HTTP server must already be running.
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        try {
            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                    .endpoint("/mcp").connectTimeout(Duration.ofSeconds(3))
                    .openConnectionOnStartup(false).build();
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10))
                    .initializationTimeout(Duration.ofSeconds(5)).build()) {
                var initialized = client.initialize();
                if (!"travel-planner-tools".equals(initialized.serverInfo().name())) {
                    throw new IllegalStateException("Unexpected MCP server.");
                }
                return action.apply(client);
            }
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Local MCP server unavailable. Enable both mcp and mcp-client profiles.");
        }
    }
}
