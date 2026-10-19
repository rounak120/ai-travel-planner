package com.aibot.travelplanner.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public final class CurrentUser {
    private CurrentUser() {}
    public static String name() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        return authentication.getName();
    }
}
