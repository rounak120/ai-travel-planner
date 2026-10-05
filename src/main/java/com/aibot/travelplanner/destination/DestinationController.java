package com.aibot.travelplanner.destination;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/destinations")
public class DestinationController {
    private final DestinationService service;

    public DestinationController(DestinationService service) {
        this.service = service;
    }

    @GetMapping
    public DestinationSearch search(@RequestParam String name) {
        String query = name.strip();
        if (query.length() < 2 || query.length() > 100 || query.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "name must contain 2–100 characters without control characters.");
        }
        return service.search(query);
    }

    @ExceptionHandler(DestinationUnavailableException.class)
    public ProblemDetail unavailable(DestinationUnavailableException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("Destination provider unavailable");
        return problem;
    }
}
