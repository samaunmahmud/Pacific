package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Message;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** The newest messages first; the caller reverses them for display. */
    @EntityGraph(attributePaths = {"sender", "product"})
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);
}
