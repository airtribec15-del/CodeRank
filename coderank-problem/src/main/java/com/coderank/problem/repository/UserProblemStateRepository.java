package com.coderank.problem.repository;

import com.coderank.problem.entity.UserProblemState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProblemStateRepository extends JpaRepository<UserProblemState, UUID> {

    Optional<UserProblemState> findByUserIdAndProblemId(UUID userId, UUID problemId);

    boolean existsByUserIdAndProblemIdAndIsSolvedTrue(UUID userId, UUID problemId);
}