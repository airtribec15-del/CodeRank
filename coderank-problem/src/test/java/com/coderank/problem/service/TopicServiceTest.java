package com.coderank.problem.service;

import com.coderank.common.exception.InvalidRequestException;
import com.coderank.problem.dto.request.CreateTopicRequest;
import com.coderank.problem.dto.response.TopicResponse;
import com.coderank.problem.entity.Topic;
import com.coderank.problem.mapper.ProblemMapper;
import com.coderank.problem.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TopicService")
class TopicServiceTest {

    @Mock private TopicRepository topicRepository;
    @Mock private ProblemMapper problemMapper;

    @InjectMocks private TopicService topicService;

    private Topic topic;
    private TopicResponse topicResponse;

    @BeforeEach
    void setUp() {
        topic = Topic.builder()
                .id(UUID.randomUUID())
                .name("Array")
                .build();

        topicResponse = TopicResponse.builder()
                .id(topic.getId())
                .name("Array")
                .build();
    }

    // ------------------------------------------------------------------ //
    //  getAllTopics                                                         //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("getAllTopics")
    class GetAllTopics {

        @Test
        @DisplayName("returns list mapped from all repository topics")
        void shouldReturnMappedTopics() {
            when(topicRepository.findAll()).thenReturn(List.of(topic));
            when(problemMapper.toTopicResponse(topic)).thenReturn(topicResponse);

            List<TopicResponse> result = topicService.getAllTopics();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Array");
        }

        @Test
        @DisplayName("returns empty list when repository returns nothing")
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
        @DisplayName("saves topic and returns mapped response")
        void shouldSaveAndReturnResponse() {
            CreateTopicRequest request = new CreateTopicRequest("Array");

            when(topicRepository.existsByNameIgnoreCase("Array")).thenReturn(false);
            when(topicRepository.save(any(Topic.class))).thenReturn(topic);
            when(problemMapper.toTopicResponse(topic)).thenReturn(topicResponse);

            TopicResponse result = topicService.createTopic(request);

            assertThat(result.getName()).isEqualTo("Array");
            verify(topicRepository).save(any(Topic.class));
        }

        @Test
        @DisplayName("trims whitespace from name before saving")
        void shouldTrimNameBeforeSaving() {
            CreateTopicRequest request = new CreateTopicRequest("  Array  ");

            when(topicRepository.existsByNameIgnoreCase("  Array  ")).thenReturn(false);
            when(topicRepository.save(any(Topic.class))).thenReturn(topic);
            when(problemMapper.toTopicResponse(topic)).thenReturn(topicResponse);

            ArgumentCaptor<Topic> captor = ArgumentCaptor.forClass(Topic.class);
            topicService.createTopic(request);

            verify(topicRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Array");
        }

        @Test
        @DisplayName("throws InvalidRequestException containing name when topic already exists")
        void shouldThrowWhenTopicAlreadyExists() {
            CreateTopicRequest request = new CreateTopicRequest("Array");

            when(topicRepository.existsByNameIgnoreCase("Array")).thenReturn(true);

            assertThatThrownBy(() -> topicService.createTopic(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Array");

            verify(topicRepository, never()).save(any());
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
        void shouldCallDeleteByIdWhenExists() {
            UUID id = topic.getId();
            when(topicRepository.existsById(id)).thenReturn(true);

            topicService.deleteTopic(id);

            verify(topicRepository).deleteById(id);
        }

        @Test
        @DisplayName("throws InvalidRequestException and never deletes when not found")
        void shouldThrowAndNeverDeleteWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(topicRepository.existsById(id)).thenReturn(false);

            assertThatThrownBy(() -> topicService.deleteTopic(id))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Topic not found");

            verify(topicRepository, never()).deleteById(any());
        }
    }
}