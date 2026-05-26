package com.coderank.problem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user solved state for a problem.
 * Written by {@link com.coderank.problem.kafka.StateUpdateConsumer}
 * when a SUBMIT verdict of ACCEPTED arrives on state-update-events.
 */
@Entity
@Table(
        name = "user_problem_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_problem_state",
                columnNames = {"user_id", "problem_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProblemState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "is_solved", nullable = false)
    @Builder.Default
    private boolean isSolved = false;

    @Column(name = "solved_at")
    private Instant solvedAt;
}