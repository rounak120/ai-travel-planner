package com.aibot.travelplanner.ai;

public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException() {
        super("The local AI model could not return a response. Check that Ollama is running and the configured model is downloaded.");
    }
}
