package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "answers")
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Answer() {
    }

    Answer(Question question, User user, String text) {
        this.question = question;
        this.user = user;
        this.text = text;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Question getQuestion() { return question; }
    public User getUser() { return user; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
