package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 300)
    private String text;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    @BatchSize(size = 50)
    private List<Answer> answers = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Question() {
    }

    public Question(Product product, User user, String text) {
        this.product = product;
        this.user = user;
        this.text = text;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Answer addAnswer(User author, String text) {
        Answer a = new Answer(this, author, text);
        answers.add(a);
        return a;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public User getUser() { return user; }
    public String getText() { return text; }
    public List<Answer> getAnswers() { return answers; }
    public Instant getCreatedAt() { return createdAt; }
}
