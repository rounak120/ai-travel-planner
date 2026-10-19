package com.aibot.travelplanner.security;

import org.springframework.context.annotation.*;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@Profile("mcp")
public class McpIdentityConfiguration {
    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(JsonMapper json) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(json)).mcpEndpoint("/mcp")
                .contextExtractor(request -> McpTransportContext.create(java.util.Map.of(
                        "authentication", request.principal().orElseThrow())))
                .build();
    }
}
