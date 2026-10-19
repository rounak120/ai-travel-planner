package com.aibot.travelplanner.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "mcp-client"})
class McpClientUnavailableTests {
    @LocalServerPort int port;

    @Test
    void startsWithoutMcpServerAndReturnsSafeGatewayErrorOnDemand() throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            var request = com.aibot.travelplanner.TestAuth.request(URI.create("http://127.0.0.1:" + port + "/api/v1/mcp/tools"))
                    .timeout(Duration.ofSeconds(20)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(502);
            assertThat(response.body()).contains("Local MCP server unavailable")
                    .doesNotContain("stackTrace", "java.lang", "password");
        }
    }
}
