package com.aibot.travelplanner.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("ai")
@RequestMapping("/api/v1/ai")
public class AiConnectionController {

    private final AiConnectionService service;

    public AiConnectionController(AiConnectionService service) {
        this.service = service;
    }

    // POST makes model computation explicit; health checks never call it.
    @PostMapping("/test")
    public AiConnectionResponse testConnection() {
        return new AiConnectionResponse(service.testConnection());
    }

    @ExceptionHandler(AiUnavailableException.class)
    public ProblemDetail handleProviderFailure(AiUnavailableException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("AI provider unavailable");
        return problem;
    }

    public record AiConnectionResponse(String message) {
    }
}
