package com.coderank.problem.kafka;

import com.coderank.common.enums.Verdict;
import com.coderank.common.event.StateUpdateEvent;
import com.coderank.problem.entity.Problem;
import com.coderank.problem.entity.UserProblemState;
import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import com.coderank.problem.repository.ProblemRepository;
import com.coderank.problem.repository.UserProblemStateRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StateUpdateConsumer")
class StateUpdateConsumerTest {

    @Mock private UserProblemStateRepository userProblemStateRepository;
    @Mock private ProblemRepository          problemRepository;
    @Mock private Acknowledgment             acknowledgment;

    @InjectMocks
    private StateUpdateConsumer stateUpdateConsumer;

    private final UUID jobId        = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();
    private final UUID userId       = UUID.randomUUID();
    private final UUID problemId    = UUID.randomUUID();

    private Problem problem;

    @BeforeEach
    void setUp() {
        problem = Problem.builder()
                .id(problemId).title("Two Sum").slug("two-sum")
                .description("desc").difficulty(Difficulty.EASY)
                .state(ProblemState.PUBLISHED).createdBy(UUID.randomUUID())
                .topics(new HashSet<>()).companies(new HashSet<>())
                .examples(new ArrayList<>()).testCases(new ArrayList<>())
                .build();
    }

    private ConsumerRecord<String, StateUpdateEvent> record(StateUpdateEvent event) {
        return new ConsumerRecord<>("state-update-events", 0, 0L, jobId.toString(), event);
    }

    @Nested
    @DisplayName("onStateUpdate — ACCEPTED")
    class OnStateUpdateAccepted {

        @Test
        @DisplayName("creates new UserProblemState when no existing record")
        void shouldCreateNewStateWhenNotExists() {
            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(userId).problemId(problemId)
                    .verdict(Verdict.ACCEPTED).completedAt(Instant.now())
                    .build();

            when(userProblemStateRepository.findByUserIdAndProblemId(userId, problemId))
                    .thenReturn(Optional.empty());
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(userProblemStateRepository.save(any(UserProblemState.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            stateUpdateConsumer.onStateUpdate(record(event), acknowledgment);

            ArgumentCaptor<UserProblemState> captor =
                    ArgumentCaptor.forClass(UserProblemState.class);
            verify(userProblemStateRepository).save(captor.capture());
            assertThat(captor.getValue().isSolved()).isTrue();
            assertThat(captor.getValue().getUserId()).isEqualTo(userId);
            assertThat(captor.getValue().getProblem()).isEqualTo(problem);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("updates existing unsolved record to solved")
        void shouldUpdateExistingUnsolvedToSolved() {
            UserProblemState existing = UserProblemState.builder()
                    .id(UUID.randomUUID()).userId(userId).problem(problem)
                    .isSolved(false).solvedAt(null).build();

            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(userId).problemId(problemId)
                    .verdict(Verdict.ACCEPTED).completedAt(Instant.now())
                    .build();

            when(userProblemStateRepository.findByUserIdAndProblemId(userId, problemId))
                    .thenReturn(Optional.of(existing));
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(userProblemStateRepository.save(any(UserProblemState.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            stateUpdateConsumer.onStateUpdate(record(event), acknowledgment);

            assertThat(existing.isSolved()).isTrue();
            assertThat(existing.getSolvedAt()).isNotNull();
            verify(userProblemStateRepository).save(existing);
        }

        @Test
        @DisplayName("no-op when user already solved the problem")
        void shouldSkipWhenAlreadySolved() {
            UserProblemState alreadySolved = UserProblemState.builder()
                    .id(UUID.randomUUID()).userId(userId).problem(problem)
                    .isSolved(true).solvedAt(Instant.now().minusSeconds(3600)).build();

            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(userId).problemId(problemId)
                    .verdict(Verdict.ACCEPTED).completedAt(Instant.now())
                    .build();

            when(userProblemStateRepository.findByUserIdAndProblemId(userId, problemId))
                    .thenReturn(Optional.of(alreadySolved));

            stateUpdateConsumer.onStateUpdate(record(event), acknowledgment);

            verify(userProblemStateRepository, never()).save(any());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("sets solvedAt from event completedAt")
        void shouldSetSolvedAtFromEvent() {
            Instant completedAt = Instant.parse("2025-01-01T10:00:00Z");
            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(userId).problemId(problemId)
                    .verdict(Verdict.ACCEPTED).completedAt(completedAt)
                    .build();

            when(userProblemStateRepository.findByUserIdAndProblemId(userId, problemId))
                    .thenReturn(Optional.empty());
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(userProblemStateRepository.save(any(UserProblemState.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            stateUpdateConsumer.onStateUpdate(record(event), acknowledgment);

            ArgumentCaptor<UserProblemState> captor =
                    ArgumentCaptor.forClass(UserProblemState.class);
            verify(userProblemStateRepository).save(captor.capture());
            assertThat(captor.getValue().getSolvedAt()).isEqualTo(completedAt);
        }

        @Test
        @DisplayName("throws IllegalStateException when problem entity not found during save")
        void shouldThrowWhenProblemEntityNotFound() {
            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(userId).problemId(problemId)
                    .verdict(Verdict.ACCEPTED).completedAt(Instant.now())
                    .build();

            when(userProblemStateRepository.findByUserIdAndProblemId(userId, problemId))
                    .thenReturn(Optional.empty());
            when(problemRepository.findById(problemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    stateUpdateConsumer.onStateUpdate(record(event), acknowledgment))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Problem not found");

            verify(userProblemStateRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("onStateUpdate — non-ACCEPTED verdicts")
    class OnStateUpdateNonAccepted {

        @ParameterizedTest
        @EnumSource(value = Verdict.class, names = {"ACCEPTED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("acknowledges without DB write for non-ACCEPTED verdict")
        void shouldAcknowledgeWithoutDbWrite(Verdict verdict) {
            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(userId).problemId(problemId)
                    .verdict(verdict).completedAt(Instant.now())
                    .build();

            stateUpdateConsumer.onStateUpdate(record(event), acknowledgment);

            verify(userProblemStateRepository, never()).findByUserIdAndProblemId(any(), any());
            verify(userProblemStateRepository, never()).save(any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("onStateUpdate — incomplete events")
    class OnStateUpdateIncomplete {

        @Test
        @DisplayName("acknowledges and skips when userId is null")
        void shouldSkipWhenUserIdNull() {
            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(null).problemId(problemId)
                    .verdict(Verdict.ACCEPTED).completedAt(Instant.now())
                    .build();

            stateUpdateConsumer.onStateUpdate(record(event), acknowledgment);

            verify(userProblemStateRepository, never()).save(any());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("acknowledges and skips when problemId is null")
        void shouldSkipWhenProblemIdNull() {
            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(userId).problemId(null)
                    .verdict(Verdict.ACCEPTED).completedAt(Instant.now())
                    .build();

            stateUpdateConsumer.onStateUpdate(record(event), acknowledgment);

            verify(userProblemStateRepository, never()).save(any());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("acknowledges and skips when verdict is null")
        void shouldSkipWhenVerdictNull() {
            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(userId).problemId(problemId)
                    .verdict(null).completedAt(Instant.now())
                    .build();

            stateUpdateConsumer.onStateUpdate(record(event), acknowledgment);

            verify(userProblemStateRepository, never()).save(any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleDlt")
    class HandleDlt {

        @Test
        @DisplayName("acknowledges DLT record without any DB interaction")
        void shouldAcknowledgeWithoutDbInteraction() {
            StateUpdateEvent event = StateUpdateEvent.builder()
                    .jobId(jobId).submissionId(submissionId)
                    .userId(userId).problemId(problemId)
                    .verdict(Verdict.ACCEPTED).completedAt(Instant.now())
                    .build();

            stateUpdateConsumer.handleDlt(record(event), acknowledgment);

            verify(userProblemStateRepository, never()).save(any());
            verify(problemRepository, never()).findById(any());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("handles null event value in DLT gracefully")
        void shouldHandleNullEventInDlt() {
            ConsumerRecord<String, StateUpdateEvent> nullRecord =
                    new ConsumerRecord<>("state-update-events-dlt", 0, 0L, "key", null);

            assertThatCode(() ->
                    stateUpdateConsumer.handleDlt(nullRecord, acknowledgment))
                    .doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }
    }
}