package com.pacific.marketplace.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class QaDtos {

    private QaDtos() {
    }

    public record QuestionRequest(
            @NotBlank(message = "Please type your question.")
            @Size(max = 300, message = "Questions can be at most 300 characters.") String text) {
    }

    public record AnswerRequest(
            @NotBlank(message = "Please type your answer.")
            @Size(max = 500, message = "Answers can be at most 500 characters.") String text) {
    }

    /** label: PACIFIC (admin), SELLER (the product's seller) or BUYER (someone who bought it). */
    public record AnswerDto(Long id, String text, String authorName, String label, Instant createdAt, boolean mine,
                            boolean canDelete) {
    }

    public record QuestionDto(Long id, String text, String askerName, Instant createdAt, boolean mine,
                              boolean canDelete, List<AnswerDto> answers) {
    }

    /** A question as shown in Seller Central, with the product it's about. */
    public record SellerQuestionDto(Long id, String text, String askerName, Instant createdAt, Long productId,
                                    String productName) {
    }

    public record QuestionsResponse(List<QuestionDto> questions, boolean canAsk, boolean canAnswer) {
    }
}
