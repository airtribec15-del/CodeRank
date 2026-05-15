package com.coderank.problem.repository;

import com.coderank.problem.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

    List<TestCase> findAllByProblemIdOrderByOrderIndexAsc(UUID problemId);
}