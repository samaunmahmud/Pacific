package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Conversation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByBuyerIdAndSellerId(Long buyerId, Long sellerId);

    @EntityGraph(attributePaths = {"buyer", "seller", "seller.user"})
    Optional<Conversation> findWithPeopleById(Long id);

    @EntityGraph(attributePaths = "seller")
    List<Conversation> findByBuyerIdOrderByLastMessageAtDescIdDesc(Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = "buyer")
    List<Conversation> findBySellerIdOrderByLastMessageAtDescIdDesc(Long sellerId, Pageable pageable);

    long countByBuyerIdAndBuyerUnreadGreaterThan(Long buyerId, int unread);

    long countBySellerIdAndSellerUnreadGreaterThan(Long sellerId, int unread);

    /** Records a message from the buyer: the seller has one more to read. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Conversation c set c.sellerUnread = c.sellerUnread + 1, c.lastMessageAt = :at, c.lastPreview = :preview "
            + "where c.id = :id")
    void buyerSent(@Param("id") Long id, @Param("at") Instant at, @Param("preview") String preview);

    /** Records a message from the seller: the buyer has one more to read. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Conversation c set c.buyerUnread = c.buyerUnread + 1, c.lastMessageAt = :at, c.lastPreview = :preview "
            + "where c.id = :id")
    void sellerSent(@Param("id") Long id, @Param("at") Instant at, @Param("preview") String preview);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Conversation c set c.buyerUnread = 0 where c.id = :id")
    void buyerRead(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Conversation c set c.sellerUnread = 0 where c.id = :id")
    void sellerRead(@Param("id") Long id);
}
