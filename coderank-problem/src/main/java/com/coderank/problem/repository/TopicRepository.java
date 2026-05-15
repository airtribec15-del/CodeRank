package com.coderank.problem.repository;

import com.coderank.problem.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    Set<Topic> findAllByIdIn(Set<UUID> ids);
}