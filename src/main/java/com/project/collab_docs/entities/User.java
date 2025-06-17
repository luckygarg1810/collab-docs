package com.project.collab_docs.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;


@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment integer
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt encoded

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // For future OAuth:
    @Column(name = "provider")
    private String provider; // e.g., "google", "github"

    @Column(name = "provider_id")
    private String providerId; // ID from the OAuth provider

    @Column(name = "account_non_locked", nullable = false)
    @Builder.Default
    private Boolean accountNonLocked = true;

    @PrePersist
    public void setTimestamp() {
        this.createdAt = LocalDateTime.now();
    }
}
