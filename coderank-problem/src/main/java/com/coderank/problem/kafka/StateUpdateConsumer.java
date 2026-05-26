package com.coderank.problem.kafka;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.StateUpdateEvent;
import com.coderank.problem.entity.Problem;
import com.coderank.problem.entity.UserProblemState;
import com.coderank.problem.repository.ProblemRepository;
import com.coderank.problem.repository.UserProblemStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Consumes {@code state-update-events} published by Result Processor (Step 6).
 *
 * <p><strong>Locked Flow Step 7:</strong> When verdict is {@code ACCEPTED},
 * upserts a {@link UserProblemState} row marking the user as having solved
 * the problem. Non-ACCEPTED verdicts are acknowledged and discarded — no DB
 * write is needed since the problem remains unsolved.</p>
 *
 * <p>Uses {@link RetryableTopic} for transient failure handling with
 * exponential backoff. Exhausted retries are routed to the DLT.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StateUpdateConsumer {

    private final UserProblemStateRepository userProblemStateRepository;
    private final ProblemRepository problemRepository;

    /**
     * Retryable consumer: 3 attempts with exponential backoff (1s → 2s → 4s).
     * Failed exhausted messages route to {@code state-update-events-dlt}.
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "false",
            include = {Exception.class}
    )
    @org.springframework.kafka.annotation.KafkaListener(
            topics = KafkaTopics.STATE_UPDATE_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onStateUpdate(
            ConsumerRecord<String, StateUpdateEvent> record,
            Acknowledgment acknowledgment) {

        StateUpdateEvent event = record.value();
        log.info("StateUpdateConsumer received: jobId={} userId={} problemId={} verdict={}",
                event.getJobId(), event.getUserId(), event.getProblemId(), event.getVerdict());

        try {
            if (event.getUserId() == null || event.getProblemId() == null || event.getVerdict() == null) {
                log.warn("StateUpdateConsumer: incomplete event, skipping. jobId={}", event.getJobId());
                acknowledgment.acknowledge();
                return;
            }

            // Only ACCEPTED verdict creates/updates a solved record
            if (Verdict.ACCEPTED.equals(event.getVerdict())) {
                markUserProblemSolved(event);
            } else {
                log.debug("Verdict {} is not ACCEPTED — no state change for userId={} problemId={}",
                        event.getVerdict(), event.getUserId(), event.getProblemId());
            }

            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("StateUpdateConsumer: failed to process event jobId={} — will retry. Error: {}",
                    event.getJobId(), ex.getMessage(), ex);
            // Re-throw to trigger @RetryableTopic retry mechanism
            throw ex;
        }
    }

    /**
     * Upserts a {@link UserProblemState} row for the given user/problem pair.
     *
     * <p>If a row already exists (user solved the problem before), this is a no-op —
     * we do not overwrite the original {@code solvedAt} timestamp.</p>
     */
    private void markUserProblemSolved(StateUpdateEvent event) {
        Optional<UserProblemState> existing =
                userProblemStateRepository.findByUserIdAndProblemId(
                        event.getUserId(), event.getProblemId());

        if (existing.isPresent() && existing.get().isSolved()) {
            log.debug("userId={} already solved problemId={}, skipping upsert",
                    event.getUserId(), event.getProblemId());
            return;
        }

        Problem problem = problemRepository.findById(event.getProblemId())
                .orElseThrow(() -> new IllegalStateException(
                        "Problem not found for state update: " + event.getProblemId()));

        UserProblemState state = existing.map(s -> {
            s.setSolved(true);
            s.setSolvedAt(event.getCompletedAt() != null ? event.getCompletedAt() : Instant.now());
            return s;
        }).orElseGet(() -> UserProblemState.builder()
                .userId(event.getUserId())
                .problem(problem)
                .isSolved(true)
                .solvedAt(event.getCompletedAt() != null ? event.getCompletedAt() : Instant.now())
                .build());

        userProblemStateRepository.save(state);
        log.info("Marked userId={} as solved for problemId={}", event.getUserId(), event.getProblemId());
    }

    /**
     * Dead Letter Topic handler — called after all retry attempts are exhausted.
     * Logs the poisoned event. No further processing is attempted.
     */
    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, StateUpdateEvent> record,
            Acknowledgment acknowledgment) {

        StateUpdateEvent event = record.value();
        log.error("StateUpdateConsumer DLT: event permanently failed. " +
                        "jobId={} userId={} problemId={} verdict={} — manual intervention required.",
                event != null ? event.getJobId() : "null",
                event != null ? event.getUserId() : "null",
                event != null ? event.getProblemId() : "null",
                event != null ? event.getVerdict() : "null");
        acknowledgment.acknowledge();
    }
}