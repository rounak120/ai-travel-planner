package com.aibot.travelplanner.mcp;

import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@Profile("mcp-client")
@RequestMapping("/api/v1/mcp")
public class McpClientController {
    private final TravelMcpClient client;

    public McpClientController(TravelMcpClient client) { this.client = client; }

    @GetMapping("/tools")
    public List<Tool> tools() { return client.tools(); }

    @GetMapping("/destinations")
    public CallToolResult search(@RequestParam String name) { return client.search(name); }

    @GetMapping("/trips/{tripId}/weather")
    public CallToolResult weather(@PathVariable String tripId) { return client.weather(tripId); }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail failure(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }
}
