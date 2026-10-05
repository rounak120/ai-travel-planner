package com.aibot.travelplanner.weather;

public class WeatherUnavailableException extends RuntimeException {
    public WeatherUnavailableException() {
        super("The weather provider could not return a valid forecast. Try again later.");
    }
}
