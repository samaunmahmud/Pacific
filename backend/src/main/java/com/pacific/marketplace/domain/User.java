package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "users")
@BatchSize(size = 50)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Customers sign in with their email. Null for admins. */
    @Column(length = 190)
    private String email;

    /** Admins sign in with a username ("Admin ID"). Null for customers. */
    @Column(length = 60)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Bumped on every password change; sign-in tokens carry it, so a change signs every other session out. */
    @Column(name = "password_version", nullable = false)
    private int passwordVersion;

    /** When the customer confirmed they own their email address; null until then. */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected User() {
    }

    public User(String name, String email, String username, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordVersion++;
    }

    public void markEmailVerified() {
        if (emailVerifiedAt == null) emailVerifiedAt = Instant.now();
    }

    public boolean isEmailVerified() { return emailVerifiedAt != null; }

    public void rename(String name) {
        this.name = name;
    }

    public int getPasswordVersion() { return passwordVersion; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
