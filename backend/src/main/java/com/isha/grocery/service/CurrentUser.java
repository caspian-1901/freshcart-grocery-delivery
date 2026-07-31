package com.isha.grocery.service;

import com.isha.grocery.config.AuthUser;
import com.isha.grocery.domain.User;
import com.isha.grocery.repo.UserRepository;
import com.isha.grocery.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the signed-in user from the security context. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in to continue.");
        }
        return principal.id();
    }

    public User require() {
        return users.findById(id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Your account could not be found. Please sign in again."));
    }
}
