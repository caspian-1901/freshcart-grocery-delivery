package com.isha.grocery.service;

import com.isha.grocery.config.JwtService;
import com.isha.grocery.domain.Cart;
import com.isha.grocery.domain.User;
import com.isha.grocery.dto.Requests;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.repo.CartRepository;
import com.isha.grocery.repo.UserRepository;
import com.isha.grocery.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Sign-up / login with JWT issuance and BCrypt password hashing (Week 1). */
@Service
public class AuthService {

    private final UserRepository users;
    private final CartRepository carts;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, CartRepository carts,
                       PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.carts = carts;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public Responses.Auth signup(Requests.Signup request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
        }

        User user = users.save(User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(encoder.encode(request.password()))
                .phone(request.phone())
                .createdAt(Instant.now())
                .build());

        // Every user gets exactly one cart, created up front.
        carts.save(Cart.builder().user(user).build());

        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public Responses.Auth login(Requests.Login request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                        "Incorrect email or password."));

        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                    "Incorrect email or password.");
        }
        return tokenFor(user);
    }

    private Responses.Auth tokenFor(User user) {
        String token = jwt.issue(user.getId(), user.getEmail());
        return new Responses.Auth(token, jwt.expiresAt(token), summary(user));
    }

    public Responses.UserSummary summary(User user) {
        return new Responses.UserSummary(user.getId(), user.getName(), user.getEmail(), user.getPhone());
    }
}
