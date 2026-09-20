package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** An address a customer saved to reuse at checkout. */
@Entity
@Table(name = "user_addresses")
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160)
    private String line1;

    @Column(length = 160)
    private String line2;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 20)
    private String postcode;

    @Column(nullable = false, length = 80)
    private String country;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected UserAddress() {
    }

    public UserAddress(User user, String name, String line1, String line2, String city, String postcode, String country) {
        this.user = user;
        set(name, line1, line2, city, postcode, country);
    }

    public void set(String name, String line1, String line2, String city, String postcode, String country) {
        this.name = name;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.postcode = postcode;
        this.country = country;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void setDefault(boolean value) { this.isDefault = value; }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getPostcode() { return postcode; }
    public String getCountry() { return country; }
    public boolean isDefault() { return isDefault; }
    public Instant getCreatedAt() { return createdAt; }
}
