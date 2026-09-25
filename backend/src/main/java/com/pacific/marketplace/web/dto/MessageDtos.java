package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class MessageDtos {

    private MessageDtos() {
    }

    private static final String TOO_LONG = "Messages can be up to " + Message.MAX_LENGTH + " characters.";

    /** Which side of the conversation the viewer is on. */
    public enum Side { BUYER, SELLER }

    /** A buyer writes to a store, optionally about one of its products or one of their orders from it. */
    public record StartRequest(
            @NotBlank(message = "Which store is this for?") @Size(max = 120) String sellerSlug,
            Long productId,
            Long orderId,
            @NotBlank(message = "Please write a message.") @Size(max = Message.MAX_LENGTH, message = TOO_LONG) String body) {
    }

    /** A seller writes to the buyer of one of their orders. */
    public record SellerStartRequest(
            @NotNull(message = "Which order is this about?") Long orderId,
            @NotBlank(message = "Please write a message.") @Size(max = Message.MAX_LENGTH, message = TOO_LONG) String body) {
    }

    public record ReplyRequest(
            @NotBlank(message = "Please write a message.") @Size(max = Message.MAX_LENGTH, message = TOO_LONG) String body) {
    }

    /** A line in an inbox. {@code with} is the store (for the buyer) or the buyer's name (for the seller). */
    public record ConversationSummary(Long id, Side side, String with, String sellerSlug, String preview,
                                      Instant lastMessageAt, int unread) {
    }

    public record ProductRef(Long id, String name) {
    }

    public record MessageDto(Long id, boolean mine, String senderName, String body, Instant createdAt,
                             ProductRef product, Long orderId) {
    }

    /**
     * A whole conversation as one side sees it. {@code canReply} is false when the store can't currently be messaged
     * (suspended, say); {@code earlier} is true when older messages were left out.
     */
    public record ConversationDto(Long id, Side side, String storeName, String sellerSlug, String buyerName,
                                  boolean canReply, boolean earlier, List<MessageDto> messages) {
    }

    /** Conversations with something unread, as a buyer and (for sellers) as a store. */
    public record UnreadDto(long asBuyer, long asSeller) {
    }
}
