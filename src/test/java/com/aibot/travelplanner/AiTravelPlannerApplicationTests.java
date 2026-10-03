package com.aibot.travelplanner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts the real server on a free port and checks our two public contracts. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AiTravelPlannerApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void servesApplicationStatusAsJson() throws Exception {
        var response = get("/api/v1/status");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("application/json");
        Map<?, ?> json = JsonMapper.builder().build().readValue(response.body(), Map.class);
        assertThat(json.get("application")).isEqualTo("ai-travel-planner");
        assertThat(json.get("status")).isEqualTo("UP");
        assertThat(json.get("milestone")).isEqualTo("trip-api");
    }

    @Test
    void exposesHealthWithoutInternalDetails() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        Map<?, ?> json = JsonMapper.builder().build().readValue(response.body(), Map.class);
        assertThat(json.get("status")).isEqualTo("UP");
        assertThat(json.containsKey("components")).isFalse();
    }

    @Test
    void aiEndpointIsAbsentWithoutAiProfile() throws Exception {
        assertThat(get("/api/v1/ai/test").statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

}
