package com.aibot.travelplanner.ai;

public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException() {
        super("The AI provider could not return a response. Check your API key, model access, and API quota.");
    }
}
