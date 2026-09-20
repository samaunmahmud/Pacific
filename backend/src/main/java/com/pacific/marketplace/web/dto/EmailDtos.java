package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.SentEmail;
import java.time.Instant;

public final class EmailDtos {

    private EmailDtos() {
    }

    public record SentEmailDto(Long id, String to, String subject, String kind, SentEmail.Status status, String error,
                               String body, Instant createdAt) {
        public static SentEmailDto from(SentEmail e) {
            return new SentEmailDto(e.getId(), e.getToAddress(), e.getSubject(), e.getKind(), e.getStatus(),
                    e.getError(), e.getBody(), e.getCreatedAt());
        }
    }
}
