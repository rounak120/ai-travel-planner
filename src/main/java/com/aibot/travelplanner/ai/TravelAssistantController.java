package com.aibot.travelplanner.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("ai & mcp & mcp-client")
@RequestMapping("/api/v1/ai")
public class TravelAssistantController {
    private final TravelAssistantService assistant;
    public TravelAssistantController(TravelAssistantService assistant) { this.assistant = assistant; }

    @PostMapping("/chat")
    public TravelAssistantService.Answer chat(@RequestBody TravelAssistantService.Request request) {
        return assistant.ask(request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail failure(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }
}
