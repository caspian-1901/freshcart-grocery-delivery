package com.isha.grocery.web;

import com.isha.grocery.config.JwtService;
import com.isha.grocery.dto.Requests;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.service.AuthService;
import com.isha.grocery.service.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final CurrentUser currentUser;
    private final JwtService jwt;

    public AuthController(AuthService auth, CurrentUser currentUser, JwtService jwt) {
        this.auth = auth;
        this.currentUser = currentUser;
        this.jwt = jwt;
    }

    @PostMapping("/signup")
    public Responses.Auth signup(@Valid @RequestBody Requests.Signup request) {
        return auth.signup(request);
    }

    @PostMapping("/login")
    public Responses.Auth login(@Valid @RequestBody Requests.Login request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    public Responses.UserSummary me() {
        return auth.summary(currentUser.require());
    }

    /**
     * Week 4 fix: the checkout flow calls this before sending the user to
     * payment, so an expiring session prompts a re-login early instead of
     * failing confusingly at the payment step.
     */
    @GetMapping("/session")
    public Responses.Session session(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return new Responses.Session(false, null, 0);
        }
        String token = header.substring(7).trim();
        if (!jwt.isValid(token)) {
            return new Responses.Session(false, null, 0);
        }
        Instant expiresAt = jwt.expiresAt(token);
        long remaining = Math.max(expiresAt.getEpochSecond() - Instant.now().getEpochSecond(), 0);
        return new Responses.Session(true, expiresAt, remaining);
    }
}
