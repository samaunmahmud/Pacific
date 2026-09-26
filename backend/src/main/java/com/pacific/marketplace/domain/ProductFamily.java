package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Product pages that are variations of one item (colours, sizes...). Members point here with {@code family_id} and
 * say which option they are with {@code option1} (and {@code option2} when there's a second dimension).
 */
@Entity
@Table(name = "product_families")
public class ProductFamily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String dim1;

    @Column(length = 30)
    private String dim2;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductFamily() {
    }

    public ProductFamily(String dim1, String dim2) {
        this.dim1 = dim1;
        this.dim2 = dim2;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void rename(String dim1, String dim2) {
        this.dim1 = dim1;
        this.dim2 = dim2;
    }

    /** "Colour: Red, Size: M" for these options. */
    public String label(String option1, String option2) {
        return dim2 == null || option2 == null ? dim1 + ": " + option1 : dim1 + ": " + option1 + ", " + dim2 + ": " + option2;
    }

    public Long getId() { return id; }
    public String getDim1() { return dim1; }
    public String getDim2() { return dim2; }
}
