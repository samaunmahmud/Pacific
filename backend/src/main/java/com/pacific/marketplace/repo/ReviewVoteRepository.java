package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.ReviewVote;
import com.pacific.marketplace.domain.VoteType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewVoteRepository extends JpaRepository<ReviewVote, Long> {

    Optional<ReviewVote> findByReviewIdAndUserId(Long reviewId, Long userId);

    List<ReviewVote> findByUserIdAndReviewIdIn(Long userId, Collection<Long> reviewIds);

    long countByReviewIdAndVoteType(Long reviewId, VoteType voteType);
}
