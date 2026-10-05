package com.aibot.travelplanner.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Local development guard, NOT user authentication or trip ownership checks. */
@Component
@Profile("mcp")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalMcpFilter extends OncePerRequestFilter {
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/mcp") && !request.getRequestURI().startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!LOCAL_HOSTS.contains(request.getServerName()) || !validOrigin(request)) {
            response.sendError(403, "MCP is restricted to local clients and same-origin requests.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean validOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) return true; // Native MCP clients need not send Origin.
        try {
            var uri = URI.create(origin);
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            return "http".equals(uri.getScheme()) && request.getServerName().equalsIgnoreCase(uri.getHost())
                    && port == request.getServerPort() && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
