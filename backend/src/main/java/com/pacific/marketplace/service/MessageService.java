package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.Conversation;
import com.pacific.marketplace.domain.Message;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.notify.NotificationService;
import com.pacific.marketplace.repo.ConversationRepository;
import com.pacific.marketplace.repo.MessageRepository;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.SellerProfileRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.security.Throttles;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.MessageDtos.ConversationDto;
import com.pacific.marketplace.web.dto.MessageDtos.ConversationSummary;
import com.pacific.marketplace.web.dto.MessageDtos.MessageDto;
import com.pacific.marketplace.web.dto.MessageDtos.ProductRef;
import com.pacific.marketplace.web.dto.MessageDtos.Side;
import com.pacific.marketplace.web.dto.MessageDtos.UnreadDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Private messages between buyers and sellers' stores. A buyer can write to any approved store (about one of its
 * products or one of their orders from it); a seller can write to the buyer of one of their orders; either can reply.
 * Only the two sides can read a conversation. The other side is emailed when a conversation gets its first unread
 * message, not for every message, and the email doesn't quote the message.
 */
@Service
public class MessageService {

    private static final int THREAD_LIMIT = 100;
    private static final int INBOX_LIMIT = 50;
    private static final ApiException NOT_FOUND = ApiException.notFound("Conversation not found.");

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final SellerProfileRepository sellers;
    private final SellerService sellerService;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserRepository users;
    private final NotificationService notifications;
    private final Throttles throttles;

    public MessageService(ConversationRepository conversations, MessageRepository messages, SellerProfileRepository sellers,
                          SellerService sellerService, ProductRepository products, OrderRepository orders,
                          UserRepository users, NotificationService notifications, Throttles throttles) {
        this.conversations = conversations;
        this.messages = messages;
        this.sellers = sellers;
        this.sellerService = sellerService;
        this.products = products;
        this.orders = orders;
        this.users = users;
        this.notifications = notifications;
        this.throttles = throttles;
    }

    /** A buyer writes to a store; continues their existing conversation with it if there is one. */
    @Transactional
    public ConversationDto start(Long buyerId, String sellerSlug, Long productId, Long orderId, String body) {
        SellerProfile seller = sellers.findBySlug(sellerSlug.strip()).filter(SellerProfile::isApproved)
                .orElseThrow(() -> ApiException.notFound("Store not found."));
        if (seller.getUser().getId().equals(buyerId)) throw ApiException.badRequest("You can't message your own store.");
        Product product = null;
        if (productId != null) {
            product = products.findById(productId)
                    .filter(p -> p.isVisibleInStore() && p.getSeller() != null && p.getSeller().getId().equals(seller.getId()))
                    .orElseThrow(() -> ApiException.badRequest("That product isn't sold by this store."));
        }
        Order order = null;
        if (orderId != null) order = buyersOrderFrom(buyerId, seller, orderId);

        Conversation c = conversations.findByBuyerIdAndSellerId(buyerId, seller.getId())
                .orElseGet(() -> create(users.getReferenceById(buyerId), seller));
        send(c, Side.BUYER, buyerId, body, product, order);
        return view(c.getId(), buyerId);
    }

    /** A seller writes to the buyer of one of their orders. */
    @Transactional
    public ConversationDto sellerStart(Long sellerUserId, Long orderId, String body) {
        SellerProfile seller = sellerService.approved(sellerUserId);
        Order order = orders.findById(orderId).filter(o -> o.getSeller() != null && o.getSeller().getId().equals(seller.getId()))
                .orElseThrow(() -> ApiException.notFound("Order not found."));
        User buyer = order.getUser();
        if (buyer.getId().equals(sellerUserId)) throw ApiException.badRequest("You can't message yourself.");
        Conversation c = conversations.findByBuyerIdAndSellerId(buyer.getId(), seller.getId())
                .orElseGet(() -> create(buyer, seller));
        send(c, Side.SELLER, sellerUserId, body, null, order);
        return view(c.getId(), sellerUserId);
    }

    @Transactional
    public ConversationDto reply(Long userId, Long conversationId, String body) {
        Conversation c = conversations.findWithPeopleById(conversationId).orElseThrow(() -> NOT_FOUND);
        Side side = sideOf(c, userId);
        if (!c.getSeller().isApproved()) throw ApiException.forbidden("This store can't send or receive messages right now.");
        send(c, side, userId, body, null, null);
        return view(conversationId, userId);
    }

    /** Opens a conversation, marking it read for the viewer. */
    @Transactional
    public ConversationDto open(Long userId, Long conversationId) {
        Conversation c = conversations.findWithPeopleById(conversationId).orElseThrow(() -> NOT_FOUND);
        if (sideOf(c, userId) == Side.BUYER) conversations.buyerRead(c.getId());
        else conversations.sellerRead(c.getId());
        return view(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> buyerInbox(Long buyerId) {
        return conversations.findByBuyerIdOrderByLastMessageAtDescIdDesc(buyerId, PageRequest.of(0, INBOX_LIMIT)).stream()
                .map(c -> new ConversationSummary(c.getId(), Side.BUYER, c.getSeller().getStoreName(), c.getSeller().getSlug(),
                        c.getLastPreview(), c.getLastMessageAt(), c.getBuyerUnread()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> sellerInbox(Long sellerUserId) {
        SellerProfile seller = sellerService.approved(sellerUserId);
        return conversations.findBySellerIdOrderByLastMessageAtDescIdDesc(seller.getId(), PageRequest.of(0, INBOX_LIMIT)).stream()
                .map(c -> new ConversationSummary(c.getId(), Side.SELLER, c.getBuyer().getName(), seller.getSlug(),
                        c.getLastPreview(), c.getLastMessageAt(), c.getSellerUnread()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadDto unread(Long userId) {
        long asSeller = sellers.findByUserId(userId).filter(SellerProfile::isApproved)
                .map(s -> conversations.countBySellerIdAndSellerUnreadGreaterThan(s.getId(), 0)).orElse(0L);
        return new UnreadDto(conversations.countByBuyerIdAndBuyerUnreadGreaterThan(userId, 0), asSeller);
    }

    // ---------- helpers ----------

    private Conversation create(User buyer, SellerProfile seller) {
        try {
            return conversations.saveAndFlush(new Conversation(buyer, seller));
        } catch (DataIntegrityViolationException e) {
            // the same buyer started one with this store at the same moment (a double click): theirs will do
            throw ApiException.conflict("Your message couldn't be sent. Please try again.");
        }
    }

    private Order buyersOrderFrom(Long buyerId, SellerProfile seller, Long orderId) {
        return orders.findById(orderId)
                .filter(o -> o.getUser().getId().equals(buyerId) && o.getSeller() != null && o.getSeller().getId().equals(seller.getId()))
                .orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    private void send(Conversation c, Side from, Long senderId, String body, Product product, Order order) {
        String key = String.valueOf(senderId);
        throttles.messages.check(key);
        throttles.messages.hit(key);
        String text = body.strip();
        messages.save(new Message(c, users.getReferenceById(senderId), text, product, order));
        boolean firstUnread = from == Side.BUYER ? c.getSellerUnread() == 0 : c.getBuyerUnread() == 0;
        Instant now = Instant.now();
        if (from == Side.BUYER) conversations.buyerSent(c.getId(), now, Conversation.preview(text));
        else conversations.sellerSent(c.getId(), now, Conversation.preview(text));
        if (firstUnread) {
            Conversation fresh = conversations.findWithPeopleById(c.getId()).orElseThrow();
            if (from == Side.BUYER) {
                notifications.newMessage(fresh.getSeller().getUser(), fresh.getBuyer().getName(), "/seller/messages/" + fresh.getId());
            } else {
                notifications.newMessage(fresh.getBuyer(), fresh.getSeller().getStoreName(), "/messages/" + fresh.getId());
            }
        }
    }

    /** Which side the user is on; 404 (not 403) for anyone else, so conversation ids reveal nothing. */
    private static Side sideOf(Conversation c, Long userId) {
        if (c.isBuyer(userId)) return Side.BUYER;
        if (c.isSellerUser(userId)) return Side.SELLER;
        throw NOT_FOUND;
    }

    private ConversationDto view(Long conversationId, Long userId) {
        Conversation c = conversations.findWithPeopleById(conversationId).orElseThrow(() -> NOT_FOUND);
        Side side = sideOf(c, userId);
        List<Message> newest = messages.findByConversationIdOrderByIdDesc(c.getId(), PageRequest.of(0, THREAD_LIMIT + 1));
        boolean earlier = newest.size() > THREAD_LIMIT;
        List<Message> shown = new ArrayList<>(earlier ? newest.subList(0, THREAD_LIMIT) : newest);
        Collections.reverse(shown);
        String storeName = c.getSeller().getStoreName();
        List<MessageDto> dtos = shown.stream().map(m -> {
            boolean fromBuyer = m.getSender().getId().equals(c.getBuyer().getId());
            Product p = m.getProduct();
            return new MessageDto(m.getId(), m.getSender().getId().equals(userId), fromBuyer ? c.getBuyer().getName() : storeName,
                    m.getBody(), m.getCreatedAt(), p == null ? null : new ProductRef(p.getId(), p.getName()),
                    m.getOrder() == null ? null : m.getOrder().getId());
        }).toList();
        return new ConversationDto(c.getId(), side, storeName, c.getSeller().getSlug(), c.getBuyer().getName(),
                c.getSeller().isApproved(), earlier, dtos);
    }
}
