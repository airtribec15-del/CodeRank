package com.coderank.problem.service;

import com.coderank.common.exception.InvalidRequestException;
import com.coderank.problem.dto.request.CreateTopicRequest;
import com.coderank.problem.dto.response.TopicResponse;
import com.coderank.problem.entity.Topic;
import com.coderank.problem.mapper.ProblemMapper;
import com.coderank.problem.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;
    private final ProblemMapper problemMapper;

    @Transactional(readOnly = true)
    public List<TopicResponse> getAllTopics() {
        return topicRepository.findAll().stream()
                .map(problemMapper::toTopicResponse)
                .toList();
    }

    @Transactional
    public TopicResponse createTopic(CreateTopicRequest request) {
        if (topicRepository.existsByNameIgnoreCase(request.getName())) {
            throw new InvalidRequestException("Topic already exists: " + request.getName());
        }
        Topic topic = Topic.builder()
                .name(request.getName().trim())
                .build();
        Topic saved = topicRepository.save(topic);
        log.info("Topic created: {}", saved.getName());
        return problemMapper.toTopicResponse(saved);
    }

    @Transactional
    public void deleteTopic(UUID id) {
        if (!topicRepository.existsById(id)) {
            throw new InvalidRequestException("Topic not found: " + id);
        }
        topicRepository.deleteById(id);
        log.info("Topic deleted: {}", id);
    }
}