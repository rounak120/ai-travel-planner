package com.aibot.travelplanner.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class LocalMcpFilterTests {
    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example", "http://127.0.0.1:9999", "null", "invalid origin", "http://user@127.0.0.1:8080", "http://127.0.0.1:8080/extra"})
    void rejectsUnsafeOrigins(String origin) throws Exception {
        var request = request();
        request.addHeader("Origin", origin);
        var response = new MockHttpServletResponse();
        new LocalMcpFilter().doFilter(request, response, (req, res) -> { throw new AssertionError("must reject"); });
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void rejectsReboundHostAndAllowsSameOrigin() throws Exception {
        var request = request();
        request.setServerName("rebound.example");
        var response = new MockHttpServletResponse();
        new LocalMcpFilter().doFilter(request, response, (req, res) -> { throw new AssertionError("must reject"); });
        assertThat(response.getStatus()).isEqualTo(403);
        request = request();
        request.addHeader("Origin", "http://127.0.0.1:8080");
        var called = new AtomicBoolean();
        new LocalMcpFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertThat(called.get()).isTrue();
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.setServerName("127.0.0.1");
        request.setServerPort(8080);
        return request;
    }
}
