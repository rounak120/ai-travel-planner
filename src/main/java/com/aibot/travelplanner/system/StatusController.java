package com.aibot.travelplanner.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Our first HTTP entry point; future travel features get their own packages. */
@RestController
@RequestMapping("/api/v1")
public class StatusController {

    private final String applicationName;

    public StatusController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(applicationName, "UP", "trip-api");
    }
}
