package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Answer;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    @EntityGraph(attributePaths = {"user", "question"})
    Optional<Answer> findWithUserAndQuestionById(Long id);
}
