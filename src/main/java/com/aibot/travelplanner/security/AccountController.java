package com.aibot.travelplanner.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AccountController(JdbcTemplate jdbc, PasswordEncoder passwords) {this.jdbc=jdbc;this.passwords=passwords;}
    public record Registration(@NotBlank @Pattern(regexp="[a-z0-9_]{3,50}") String username,
                               @NotNull @Size(min=12,max=72) String password) {}

    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }
    @GetMapping("/me") public Map<String,String> me() {return Map.of("username",CurrentUser.name());}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> register(@Valid @RequestBody Registration input) {
        if(input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at most 72 UTF-8 bytes.");
        try {
            jdbc.update("INSERT INTO app_users(username,password_hash) VALUES (?,?)",input.username(),passwords.encode(input.password()));
        } catch(org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Username unavailable.");
        }
        return Map.of("username",input.username());
    }
}
