package com.coderank.problem.service;

import com.coderank.common.exception.InvalidRequestException;
import com.coderank.problem.dto.request.CreateTopicRequest;
import com.coderank.problem.dto.response.TopicResponse;
import com.coderank.problem.entity.Topic;
import com.coderank.problem.mapper.ProblemMapper;
import com.coderank.problem.repository.TopicRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TopicService")
class TopicServiceTest {

    @Mock private TopicRepository topicRepository;
    @Mock private ProblemMapper   problemMapper;

    @InjectMocks
    private TopicService topicService;

    private final UUID  topicId = UUID.randomUUID();
    private       Topic topic;
    private       TopicResponse topicResponse;

    @BeforeEach
    void setUp() {
        topic = Topic.builder().id(topicId).name("Dynamic Programming").build();
        topicResponse = TopicResponse.builder().id(topicId).name("Dynamic Programming").build();
    }

    // ------------------------------------------------------------------ //
    //  getAllTopics                                                        //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("getAllTopics")
    class GetAllTopics {

        @Test
        @DisplayName("returns mapped list of all topics")
        void shouldReturnAllTopics() {
            when(topicRepository.findAll()).thenReturn(List.of(topic));
            when(problemMapper.toTopicResponse(topic)).thenReturn(topicResponse);

            List<TopicResponse> result = topicService.getAllTopics();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Dynamic Programming");
        }

        @Test
        @DisplayName("returns empty list when no topics exist")
        void shouldReturnEmptyListWhenNone() {
            when(topicRepository.findAll()).thenReturn(List.of());

            List<TopicResponse> result = topicService.getAllTopics();

            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------ //
    //  createTopic                                                         //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("createTopic")
    class CreateTopic {

        @Test
        @DisplayName("creates and returns topic response when name is unique")
        void shouldCreateWhenNameIsUnique() {
            when(topicRepository.existsByNameIgnoreCase("Dynamic Programming")).thenReturn(false);
            when(topicRepository.save(any(Topic.class))).thenReturn(topic);
            when(problemMapper.toTopicResponse(topic)).thenReturn(topicResponse);

            TopicResponse result = topicService.createTopic(
                    CreateTopicRequest.builder().name("Dynamic Programming").build());

            assertThat(result.getName()).isEqualTo("Dynamic Programming");
            verify(topicRepository).save(any(Topic.class));
        }

        @Test
        @DisplayName("throws InvalidRequestException when topic name already exists")
        void shouldThrowWhenDuplicate() {
            when(topicRepository.existsByNameIgnoreCase("Dynamic Programming")).thenReturn(true);

            assertThatThrownBy(() ->
                    topicService.createTopic(
                            CreateTopicRequest.builder().name("Dynamic Programming").build()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("already exists");

            verify(topicRepository, never()).save(any());
        }

        @Test
        @DisplayName("name check is case-insensitive")
        void shouldCheckCaseInsensitive() {
            when(topicRepository.existsByNameIgnoreCase("arrays")).thenReturn(true);

            assertThatThrownBy(() ->
                    topicService.createTopic(
                            CreateTopicRequest.builder().name("arrays").build()))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    // ------------------------------------------------------------------ //
    //  deleteTopic                                                         //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("deleteTopic")
    class DeleteTopic {

        @Test
        @DisplayName("calls deleteById when topic exists")
        void shouldDeleteWhenExists() {
            when(topicRepository.existsById(topicId)).thenReturn(true);

            topicService.deleteTopic(topicId);

            verify(topicRepository).deleteById(topicId);
        }

        @Test
        @DisplayName("throws and never deletes when topic not found")
        void shouldThrowWhenNotFound() {
            when(topicRepository.existsById(topicId)).thenReturn(false);

            assertThatThrownBy(() -> topicService.deleteTopic(topicId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Topic not found");

            verify(topicRepository, never()).deleteById(any());
        }
    }
}