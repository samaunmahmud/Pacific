package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** One email the shop sent, or would have sent when no mail server is configured. */
@Entity
@Table(name = "sent_emails")
public class SentEmail {

    public enum Status { SENT, LOGGED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_address", nullable = false, length = 255)
    private String toAddress;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 30)
    private String kind;

    /** The plain-text version, cut to fit. */
    @Column(nullable = false, length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(length = 300)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SentEmail() {
    }

    public SentEmail(String toAddress, String subject, String kind, String body, Status status, String error) {
        this.toAddress = cut(toAddress, 255);
        this.subject = cut(subject, 200);
        this.kind = cut(kind, 30);
        this.body = cut(body, 4000);
        this.status = status;
        this.error = error == null ? null : cut(error, 300);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getToAddress() { return toAddress; }
    public String getSubject() { return subject; }
    public String getKind() { return kind; }
    public String getBody() { return body; }
    public Status getStatus() { return status; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
