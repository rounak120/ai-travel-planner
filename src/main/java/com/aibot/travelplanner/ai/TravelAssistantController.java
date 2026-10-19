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
    private final com.aibot.travelplanner.trip.TripService trips;
    public TravelAssistantController(TravelAssistantService assistant, com.aibot.travelplanner.trip.TripService trips) {
        this.assistant = assistant; this.trips = trips;
    }

    @PostMapping("/chat")
    public TravelAssistantService.Answer chat(@RequestBody TravelAssistantService.Request request,
            jakarta.servlet.http.HttpSession session) {
        if (request == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Provide a chat request.");
        if (request.tripId() != null) {
            try { trips.get(java.util.UUID.fromString(request.tripId())); }
            catch (IllegalArgumentException exception) {throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid trip ID.");}
        }
        synchronized (session) {
            String scope = request.tripId() == null ? "" : request.tripId();
            String owner = com.aibot.travelplanner.security.CurrentUser.name();
            if (!scope.equals(session.getAttribute("chat.scope")) || !owner.equals(session.getAttribute("chat.owner"))) {
                session.removeAttribute("chat.messages");
                session.setAttribute("chat.scope", scope);
                session.setAttribute("chat.owner", owner);
            }
            @SuppressWarnings("unchecked")
            var previous = (java.util.List<org.springframework.ai.chat.messages.Message>) session.getAttribute("chat.messages");
            var history = new java.util.ArrayList<org.springframework.ai.chat.messages.Message>(
                    previous == null ? java.util.List.of() : previous);
            var answer = assistant.ask(request, java.util.List.copyOf(history));
            history.add(new org.springframework.ai.chat.messages.UserMessage(request.message()));
            // Bound both turn count and characters; tool JSON is never retained as instruction history.
            history.add(new org.springframework.ai.chat.messages.AssistantMessage(
                    answer.message().substring(0, Math.min(2000, answer.message().length()))));
            while (history.size() > 6) history.subList(0, 2).clear();
            session.setAttribute("chat.messages", java.util.List.copyOf(history));
            return answer;
        }
    }

    @DeleteMapping("/chat/memory")
    public void clear(jakarta.servlet.http.HttpSession session) {
        synchronized (session) {
            session.removeAttribute("chat.messages");
            session.removeAttribute("chat.scope");
            session.removeAttribute("chat.owner");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail failure(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(com.aibot.travelplanner.trip.TripNotFoundException.class)
    public ProblemDetail missing(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.NOT_FOUND,"Trip not found.");
    }
}
