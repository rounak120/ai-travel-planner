package com.aibot.travelplanner.ai;

import com.aibot.travelplanner.mcp.TravelMcpClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TravelAssistantTests {
    final ChatModel model = mock(ChatModel.class);
    final TravelMcpClient mcp = mock(TravelMcpClient.class);
    final TravelAssistantService service = new TravelAssistantService(model, mcp, JsonMapper.builder().build());

    @org.junit.jupiter.api.BeforeEach void configuredModel() {
        when(model.getOptions()).thenReturn(org.springframework.ai.ollama.api.OllamaChatOptions.builder().model("qwen3:4b").build());
    }

    ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).build())));
    }
    ChatResponse call(String name, String args) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", name, args))).build())));
    }
    CallToolResult data(boolean error) {
        return CallToolResult.builder().isError(error).structuredContent(Map.of("message", error ? "UNSUPPORTED" : "Jaipur"))
                .addTextContent(error ? "UNSUPPORTED" : "Jaipur").build();
    }
    TravelAssistantService.Request request(String tripId) {
        return new TravelAssistantService.Request("Help with travel", tripId);
    }

    @Test void plainAnswerNeedsNoTools() {
        when(model.call(any(Prompt.class))).thenReturn(reply("Where would you like to go?"));
        assertThat(service.ask(request(null)).tools()).isEmpty();
        verifyNoInteractions(mcp);
    }

    @Test void searchResultFeedsNextModelRoundAndTrace() {
        when(mcp.search("Jaipur")).thenReturn(data(false));
        when(model.call(any(Prompt.class))).thenReturn(call("search_destinations", "{\"name\":\"Jaipur\"}"), reply("Found Jaipur."));
        var answer = service.ask(request(null));
        assertThat(answer.message()).isEqualTo("Found Jaipur.");
        assertThat(answer.tools()).hasSize(1);
        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(captor.capture());
        assertThat(captor.getAllValues().get(1).getInstructions()).anySatisfy(message -> {
            assertThat(message).isInstanceOf(ToolResponseMessage.class);
            assertThat(((ToolResponseMessage) message).getResponses().getFirst().responseData()).contains("Jaipur", "isError");
        });
        var options = (ToolCallingChatOptions) captor.getAllValues().getFirst().getOptions();
        assertThat(options.getModel()).isEqualTo("qwen3:4b");
        assertThat(options.getToolCallbacks()).extracting(t -> t.getToolDefinition().name()).containsExactly("search_destinations");
        verify(mcp).search("Jaipur");
    }

    @Test void weatherUsesOnlyRequestTripAndPreservesError() {
        String id = UUID.randomUUID().toString();
        when(mcp.weather(id)).thenReturn(data(true));
        when(model.call(any(Prompt.class))).thenReturn(call("get_trip_weather", "{}"), reply("Forecast unavailable."));
        assertThat(service.ask(request(id)).tools().getFirst().result().isError()).isTrue();
        verify(mcp).weather(id);
    }

    @Test void weatherCannotSupplyAnotherTrip() {
        when(model.call(any(Prompt.class))).thenReturn(call("get_trip_weather", "{\"tripId\":\"other\"}"));
        assertThatThrownBy(() -> service.ask(request(UUID.randomUUID().toString()))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mcp);
    }

    @Test void weatherWithoutRequestTripIsRejected() {
        when(model.call(any(Prompt.class))).thenReturn(call("get_trip_weather", "{}"));
        assertThatThrownBy(() -> service.ask(request(null))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mcp);
    }

    @Test void unknownAndMalformedToolsAreRejected() {
        for (var response : List.of(call("create_trip", "{}"), call("search_destinations", "not-json"),
                call("search_destinations", "{\"name\":42}"), call("search_destinations", "[]"))) {
            when(model.call(any(Prompt.class))).thenReturn(response);
            assertThatThrownBy(() -> service.ask(request(null))).isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(mcp);
    }

    @Test void repeatedRequestsStopAtTwoToolsAndThreeModelCalls() {
        when(mcp.search("Jaipur")).thenReturn(data(false));
        when(model.call(any(Prompt.class))).thenReturn(call("search_destinations", "{\"name\":\"Jaipur\"}"));
        assertThatThrownBy(() -> service.ask(request(null))).isInstanceOf(ResponseStatusException.class);
        verify(model, times(3)).call(any(Prompt.class));
        verify(mcp, times(2)).search("Jaipur");
    }

    @Test void oversizedBatchIsRejectedBeforeAnyToolExecutes() {
        var calls = java.util.stream.IntStream.range(0, 3).mapToObj(i ->
                new AssistantMessage.ToolCall("call-" + i, "function", "search_destinations", "{\"name\":\"Jaipur\"}")).toList();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content("").toolCalls(calls).build()))));
        assertThatThrownBy(() -> service.ask(request(null))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mcp);
    }

    @Test void weatherSuccessReturnsItsEvidence() {
        String id = UUID.randomUUID().toString();
        when(mcp.weather(id)).thenReturn(data(false));
        when(model.call(any(Prompt.class))).thenReturn(call("get_trip_weather", "{}"), reply("Here is the forecast."));
        var answer = service.ask(request(id));
        assertThat(answer.tools().getFirst().name()).isEqualTo("get_trip_weather");
        assertThat(answer.tools().getFirst().result().isError()).isFalse();
        verify(mcp).weather(id);
    }

    @Test void invalidRequestNeverCallsModelOrTools() {
        for (var request : List.of(new TravelAssistantService.Request(" ", null),
                new TravelAssistantService.Request("a".repeat(2001), null), request("1-1-1-1-1"))) {
            assertThatThrownBy(() -> service.ask(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        verifyNoInteractions(model, mcp);
    }

    @Test void emptyAndProviderFailureAreSafe() {
        when(model.call(any(Prompt.class))).thenReturn(reply(""));
        assertThatThrownBy(() -> service.ask(request(null))).isInstanceOf(ResponseStatusException.class);
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("secret-provider-detail"));
        assertThatThrownBy(() -> service.ask(request(null))).hasMessageNotContaining("secret-provider-detail");
        verifyNoInteractions(mcp);
    }
}
