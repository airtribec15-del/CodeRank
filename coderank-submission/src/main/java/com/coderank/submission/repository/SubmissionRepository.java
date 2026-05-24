package com.coderank.submission.repository;

import com.coderank.submission.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Optional<Submission> findByJobId(UUID jobId);

    Page<Submission> findAllByUserId(UUID userId, Pageable pageable);

    Page<Submission> findAllByUserIdAndProblemId(UUID userId, UUID problemId, Pageable pageable);
}
