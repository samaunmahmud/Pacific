package com.pacific.marketplace.notify;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.ReturnRequest;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.domain.SentEmail;
import com.pacific.marketplace.repo.SentEmailRepository;
import com.pacific.marketplace.web.dto.EmailDtos.SentEmailDto;
import com.pacific.marketplace.web.dto.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Decides what to tell customers and sellers, and when. Emails are written while the caller's transaction is still
 * open (so the order's items and address can be read), but only sent once that transaction has committed: nobody is
 * emailed about an order that then rolled back. A failure to send is recorded and logged, and never fails the order.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Mailer mailer;
    private final SentEmailRepository sent;
    private final TaskExecutor executor;
    private final EmailTemplates templates;
    private final TransactionTemplate recordTx;

    public NotificationService(Mailer mailer, SentEmailRepository sent,
                               @Qualifier("notificationExecutor") TaskExecutor executor, AppProperties props,
                               PlatformTransactionManager txManager) {
        this.mailer = mailer;
        this.sent = sent;
        this.executor = executor;
        this.templates = new EmailTemplates(props.publicUrl(), props.shop().currency());
        // afterCommit runs while the committed transaction is still bound to the thread, so anything saved there would
        // join it and never be committed. Recording the email always gets a transaction of its own.
        this.recordTx = new TransactionTemplate(txManager);
        this.recordTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** A checkout became real orders (pay on delivery, or the card payment arrived): tell the customer and each seller. */
    public void ordersPlaced(List<Order> orders) {
        if (orders.isEmpty()) return;
        List<Email> emails = new java.util.ArrayList<>();
        emails.add(templates.orderConfirmation(orders));
        for (Order o : orders) {
            if (o.getSeller() != null) emails.add(templates.newOrderForSeller(o));
        }
        emails.forEach(this::afterCommit);
    }

    public void orderShipped(Order order) {
        afterCommit(templates.orderShipped(order));
    }

    public void orderDelivered(Order order) {
        afterCommit(templates.orderDelivered(order));
    }

    /** refunded: what went back to the customer's card, or null/zero when nothing had been charged. */
    public void orderCancelled(Order order, String by, BigDecimal refunded) {
        afterCommit(templates.orderCancelled(order, by, refunded));
    }

    /** The shop's email log, newest first, for the admin area. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PageResponse<SentEmailDto> log(int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(sent.findAll(pageable), SentEmailDto::from);
    }

    public void passwordReset(User user, String rawToken, int minutes) {
        afterCommit(templates.passwordReset(user, rawToken, minutes));
    }

    public void verifyEmail(User user, String rawToken, int hours) {
        afterCommit(templates.verifyEmail(user, rawToken, hours));
    }

    public void passwordChanged(User user) {
        afterCommit(templates.passwordChanged(user));
    }

    public void returnRequested(ReturnRequest r) {
        afterCommit(templates.returnRequested(r));
        if (r.getOrder().getSeller() != null) afterCommit(templates.returnRequestedForSeller(r));
    }

    public void returnApproved(ReturnRequest r) {
        afterCommit(templates.returnApproved(r));
    }

    public void returnRejected(ReturnRequest r) {
        afterCommit(templates.returnRejected(r));
    }

    public void returnRefunded(ReturnRequest r, boolean toCard) {
        afterCommit(templates.returnRefunded(r, toCard));
    }

    private void afterCommit(Email email) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(email);
                }
            });
        } else {
            deliver(email);
        }
    }

    private void deliver(Email email) {
        executor.execute(() -> {
            SentEmail.Status status;
            String error = null;
            try {
                status = mailer.send(email);
            } catch (Exception e) {
                status = SentEmail.Status.FAILED;
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("Could not send {} email to {}: {}", email.kind(), email.to(), error);
            }
            try {
                SentEmail record = new SentEmail(email.to(), email.subject(), email.kind(), email.recordedText(), status, error);
                recordTx.executeWithoutResult(t -> sent.save(record));
            } catch (RuntimeException e) {
                log.warn("Could not record the {} email to {}: {}", email.kind(), email.to(), e.getMessage());
            }
        });
    }
}
