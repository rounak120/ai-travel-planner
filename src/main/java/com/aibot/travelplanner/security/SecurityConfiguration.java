package com.aibot.travelplanner.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(12); }

    @Bean UserDetailsService users(JdbcTemplate jdbc) {
        return name -> jdbc.query("SELECT username, password_hash FROM app_users WHERE username = ?",
                (row, index) -> User.withUsername(row.getString(1)).password(row.getString(2)).roles("USER").build(), name)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/status", "/actuator/health", "/error").permitAll()
                .requestMatchers("/api/**", "/mcp", "/mcp/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(form -> form.loginProcessingUrl("/api/v1/auth/login")
                .successHandler((request,response,authentication) -> {
                    request.getSession().removeAttribute("chat.messages");
                    request.getSession().removeAttribute("chat.scope");
                    request.getSession().removeAttribute("chat.owner");
                    response.setStatus(204);
                })
                .failureHandler((request,response,exception) -> problem(response,401,"Invalid username or password.")))
            .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler((request,response,authentication) -> response.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request,response,exception) -> problem(response,401,"Sign in to continue."))
                .accessDeniedHandler((request,response,exception) -> problem(response,403,"Access denied or session token expired. Reload and try again.")));
        // CSRF remains enabled, including login/registration/logout and MCP POSTs.
        return http.build();
    }

    static void problem(jakarta.servlet.http.HttpServletResponse response, int status, String detail) throws java.io.IOException {
        response.setStatus(status);response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":"+status+",\"detail\":\""+detail+"\"}");
    }
}
