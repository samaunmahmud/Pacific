package com.pacific.marketplace.web;

import com.pacific.marketplace.service.EmailVerificationService;
import com.pacific.marketplace.service.MessageService;
import com.pacific.marketplace.web.dto.MessageDtos.ConversationDto;
import com.pacific.marketplace.web.dto.MessageDtos.ConversationSummary;
import com.pacific.marketplace.web.dto.MessageDtos.ReplyRequest;
import com.pacific.marketplace.web.dto.MessageDtos.SellerStartRequest;
import com.pacific.marketplace.web.dto.MessageDtos.StartRequest;
import com.pacific.marketplace.web.dto.MessageDtos.UnreadDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Buyer-seller messages. Customers only (admins have no store and buy nothing); sellers use their customer account. */
@RestController
@RequestMapping("/api")
public class MessageController {

    private final MessageService messages;
    private final EmailVerificationService verification;

    public MessageController(MessageService messages, EmailVerificationService verification) {
        this.messages = messages;
        this.verification = verification;
    }

    @GetMapping("/messages")
    public List<ConversationSummary> inbox(@AuthenticationPrincipal Jwt jwt) {
        return messages.buyerInbox(CurrentUser.id(jwt));
    }

    @GetMapping("/messages/unread")
    public UnreadDto unread(@AuthenticationPrincipal Jwt jwt) {
        return messages.unread(CurrentUser.id(jwt));
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto start(@Valid @RequestBody StartRequest req, @AuthenticationPrincipal Jwt jwt) {
        verification.requireConfirmed(CurrentUser.id(jwt), "message sellers");
        return messages.start(CurrentUser.id(jwt), req.sellerSlug(), req.productId(), req.orderId(), req.body());
    }

    @GetMapping("/messages/{id}")
    public ConversationDto open(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return messages.open(CurrentUser.id(jwt), id);
    }

    @PostMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest req, @AuthenticationPrincipal Jwt jwt) {
        verification.requireConfirmed(CurrentUser.id(jwt), "send messages");
        return messages.reply(CurrentUser.id(jwt), id, req.body());
    }

    @GetMapping("/seller/messages")
    public List<ConversationSummary> sellerInbox(@AuthenticationPrincipal Jwt jwt) {
        return messages.sellerInbox(CurrentUser.id(jwt));
    }

    @PostMapping("/seller/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto sellerStart(@Valid @RequestBody SellerStartRequest req, @AuthenticationPrincipal Jwt jwt) {
        return messages.sellerStart(CurrentUser.id(jwt), req.orderId(), req.body());
    }
}
