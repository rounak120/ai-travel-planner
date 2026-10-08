package com.aibot.travelplanner.ai;

import com.aibot.travelplanner.destination.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Fake model and provider service, real REST endpoint and MCP HTTP exchange. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "ai", "mcp", "mcp-client"})
class TravelAssistantHttpTests {
    @LocalServerPort int port;
    @MockitoBean ChatModel model;
    @MockitoBean DestinationService destinations;

    @org.junit.jupiter.api.BeforeEach void configuredModel() {
        when(model.getOptions()).thenReturn(org.springframework.ai.ollama.api.OllamaChatOptions.builder().model("qwen3:4b").build());
    }

    HttpResponse<String> post(String body) throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/ai/chat"))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test void modelRequestedSearchTraversesMcpAndReturnsEvidence() throws Exception {
        when(destinations.search("Jaipur")).thenReturn(new DestinationSearch(List.of(), "GeoNames via Open-Meteo", "https://open-meteo.com/"));
        var call = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("search-1", "function", "search_destinations", "{\"name\":\"Jaipur\"}"))).build();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(call))),
                new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("No matches found.").build()))));
        var response = post("{\"message\":\"Find Jaipur\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("No matches found", "search_destinations", "GeoNames", "\"isError\":false");
        verify(destinations).search("Jaipur");
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test void invalidInputIs400WithoutModelCall() throws Exception {
        assertThat(post("{\"message\":\"\"}").statusCode()).isEqualTo(400);
        assertThat(post("invalid-json").statusCode()).isEqualTo(400);
        verifyNoInteractions(model, destinations);
    }

    @Test void providerFailureIsSanitized502() throws Exception {
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("private-provider-detail"));
        var response = post("{\"message\":\"Find Jaipur\"}");
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).doesNotContain("private-provider-detail");
        verifyNoInteractions(destinations);
    }
}
