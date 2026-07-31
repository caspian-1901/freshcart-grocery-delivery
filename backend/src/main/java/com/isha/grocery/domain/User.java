package com.isha.grocery.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash — the raw password is never stored (Week 1). */
    @Column(nullable = false)
    private String passwordHash;

    private String phone;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
