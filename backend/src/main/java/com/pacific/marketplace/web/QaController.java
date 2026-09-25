package com.pacific.marketplace.web;

import com.pacific.marketplace.service.QaService;
import com.pacific.marketplace.service.EmailVerificationService;
import com.pacific.marketplace.web.dto.QaDtos.AnswerDto;
import com.pacific.marketplace.web.dto.QaDtos.AnswerRequest;
import com.pacific.marketplace.web.dto.QaDtos.QuestionDto;
import com.pacific.marketplace.web.dto.QaDtos.QuestionRequest;
import com.pacific.marketplace.web.dto.QaDtos.QuestionsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QaController {

    private final QaService qa;
    private final EmailVerificationService verification;

    public QaController(QaService qa, EmailVerificationService verification) {
        this.qa = qa;
        this.verification = verification;
    }

    @GetMapping("/products/{productId}/questions")
    public QuestionsResponse list(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt) {
        return qa.list(productId, jwt == null ? null : CurrentUser.id(jwt), CurrentUser.role(jwt));
    }

    @PostMapping("/products/{productId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDto ask(@PathVariable Long productId, @Valid @RequestBody QuestionRequest req,
                           @AuthenticationPrincipal Jwt jwt) {
        verification.requireConfirmed(CurrentUser.id(jwt), "ask questions");
        return qa.ask(CurrentUser.id(jwt), productId, req.text());
    }

    @PostMapping("/questions/{id}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public AnswerDto answer(@PathVariable Long id, @Valid @RequestBody AnswerRequest req,
                            @AuthenticationPrincipal Jwt jwt) {
        verification.requireConfirmed(CurrentUser.id(jwt), "answer questions");
        return qa.answer(CurrentUser.id(jwt), CurrentUser.role(jwt), id, req.text());
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        qa.deleteQuestion(CurrentUser.id(jwt), CurrentUser.role(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/answers/{id}")
    public ResponseEntity<Void> deleteAnswer(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        qa.deleteAnswer(CurrentUser.id(jwt), CurrentUser.role(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
