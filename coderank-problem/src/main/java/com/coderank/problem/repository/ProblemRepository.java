package com.coderank.problem.repository;

import com.coderank.problem.entity.Problem;
import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    Optional<Problem> findBySlug(String slug);

    boolean existsBySlug(String slug);

    // Paginated listing — only PUBLISHED problems visible to regular users
    Page<Problem> findAllByState(ProblemState state, Pageable pageable);

    // Admin can filter by difficulty + state
    @Query("""
            SELECT p FROM Problem p
            WHERE (:difficulty IS NULL OR p.difficulty = :difficulty)
            AND   (:state      IS NULL OR p.state      = :state)
            """)
    Page<Problem> findAllByFilters(
            @Param("difficulty") Difficulty difficulty,
            @Param("state") ProblemState state,
            Pageable pageable
    );
}