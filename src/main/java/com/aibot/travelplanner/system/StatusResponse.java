package com.aibot.travelplanner.system;

/** An immutable response object. Spring converts its fields into JSON. */
public record StatusResponse(String application, String status, String milestone) {
}
