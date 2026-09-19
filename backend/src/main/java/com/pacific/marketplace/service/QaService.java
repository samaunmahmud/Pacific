package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.Answer;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.Question;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.repo.AnswerRepository;
import com.pacific.marketplace.repo.OrderItemRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.QuestionRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.QaDtos.AnswerDto;
import com.pacific.marketplace.web.dto.QaDtos.QuestionDto;
import com.pacific.marketplace.web.dto.QaDtos.QuestionsResponse;
import com.pacific.marketplace.web.dto.QaDtos.SellerQuestionDto;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product questions and answers. Any signed-in customer can ask. Answers come from the product's seller, from Pacific
 * (admins), or from customers who bought the product; answers are labelled so readers know who is speaking.
 */
@Service
public class QaService {

    private static final int MAX_QUESTIONS = 50;

    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final ProductRepository products;
    private final OrderItemRepository orderItems;
    private final UserRepository users;

    public QaService(QuestionRepository questions, AnswerRepository answers, ProductRepository products,
                     OrderItemRepository orderItems, UserRepository users) {
        this.questions = questions;
        this.answers = answers;
        this.products = products;
        this.orderItems = orderItems;
        this.users = users;
    }

    /** {@code viewerId}/{@code role} are null for anonymous visitors. */
    @Transactional(readOnly = true)
    public QuestionsResponse list(Long productId, Long viewerId, Role role) {
        Product product = visibleProduct(productId);
        List<Question> list = questions.findByProductIdOrderByCreatedAtDescIdDesc(productId,
                PageRequest.of(0, MAX_QUESTIONS));

        Set<Long> authorIds = new HashSet<>();
        list.forEach(q -> q.getAnswers().forEach(a -> authorIds.add(a.getUser().getId())));
        Set<Long> buyers = authorIds.isEmpty() ? Set.of()
                : new HashSet<>(orderItems.findBuyerIds(productId, authorIds));

        boolean admin = role == Role.ADMIN;
        List<QuestionDto> dtos = list.stream().map(q -> new QuestionDto(q.getId(), q.getText(), q.getUser().getName(),
                q.getCreatedAt(), q.getUser().getId().equals(viewerId), admin || q.getUser().getId().equals(viewerId),
                q.getAnswers().stream().map(a -> new AnswerDto(a.getId(), a.getText(), a.getUser().getName(),
                        label(a.getUser(), product, buyers), a.getCreatedAt(), a.getUser().getId().equals(viewerId),
                        admin || a.getUser().getId().equals(viewerId))).toList())).toList();

        boolean customer = role == Role.CUSTOMER && viewerId != null;
        boolean ownProduct = customer && isSellerOf(product, viewerId);
        boolean canAnswer = admin || ownProduct
                || (customer && orderItems.countPurchases(viewerId, productId) > 0);
        return new QuestionsResponse(dtos, customer && !ownProduct, canAnswer);
    }

    @Transactional
    public QuestionDto ask(Long userId, Long productId, String text) {
        Product product = visibleProduct(productId);
        if (isSellerOf(product, userId)) {
            throw ApiException.forbidden("Sellers can't ask questions about their own products.");
        }
        Question q = questions.saveAndFlush(new Question(product, users.getReferenceById(userId), text.strip()));
        return new QuestionDto(q.getId(), q.getText(), q.getUser().getName(), q.getCreatedAt(), true, true, List.of());
    }

    @Transactional
    public AnswerDto answer(Long userId, Role role, Long questionId, String text) {
        Question question = questions.findWithUserAndProductById(questionId)
                .orElseThrow(() -> ApiException.notFound("Question not found."));
        Product product = question.getProduct();
        if (role != Role.ADMIN && !product.isVisibleInStore()) throw ApiException.notFound("Question not found.");
        boolean allowed = role == Role.ADMIN || isSellerOf(product, userId)
                || orderItems.countPurchases(userId, product.getId()) > 0;
        if (!allowed) {
            throw ApiException.forbidden("Only the seller or people who bought this product can answer.");
        }
        User author = users.getReferenceById(userId);
        Answer answer = question.addAnswer(author, text.strip());
        questions.saveAndFlush(question);
        return new AnswerDto(answer.getId(), answer.getText(), author.getName(),
                role == Role.ADMIN ? "PACIFIC" : isSellerOf(product, userId) ? "SELLER" : "BUYER",
                answer.getCreatedAt(), true, true);
    }

    @Transactional
    public void deleteQuestion(Long userId, Role role, Long questionId) {
        Question q = questions.findWithUserAndProductById(questionId)
                .orElseThrow(() -> ApiException.notFound("Question not found."));
        if (role != Role.ADMIN && !q.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("You can only delete your own questions.");
        }
        questions.delete(q);
    }

    @Transactional
    public void deleteAnswer(Long userId, Role role, Long answerId) {
        Answer a = answers.findWithUserAndQuestionById(answerId)
                .orElseThrow(() -> ApiException.notFound("Answer not found."));
        if (role != Role.ADMIN && !a.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("You can only delete your own answers.");
        }
        a.getQuestion().getAnswers().remove(a);
    }

    /** Unanswered questions on a seller's products, for Seller Central. */
    @Transactional(readOnly = true)
    public List<SellerQuestionDto> unansweredForSeller(Long sellerId) {
        return questions.findUnansweredForSeller(sellerId, PageRequest.of(0, MAX_QUESTIONS)).stream()
                .map(q -> new SellerQuestionDto(q.getId(), q.getText(), q.getUser().getName(), q.getCreatedAt(),
                        q.getProduct().getId(), q.getProduct().getName())).toList();
    }

    // ---------- helpers ----------

    private Product visibleProduct(Long productId) {
        return products.findById(productId).filter(Product::isVisibleInStore)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
    }

    private static boolean isSellerOf(Product product, Long userId) {
        SellerProfile seller = product.getSeller();
        return seller != null && seller.getUser().getId().equals(userId);
    }

    private static String label(User author, Product product, Set<Long> buyers) {
        if (author.getRole() == Role.ADMIN) return "PACIFIC";
        if (isSellerOf(product, author.getId())) return "SELLER";
        return buyers.contains(author.getId()) ? "BUYER" : "CUSTOMER";
    }
}
