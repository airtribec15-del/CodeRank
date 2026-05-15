package com.coderank.problem.controller;

import com.coderank.problem.dto.request.CreateTopicRequest;
import com.coderank.problem.dto.response.TopicResponse;
import com.coderank.problem.service.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
@Tag(name = "Topics", description = "Topic management endpoints")
public class TopicController {

    private final TopicService topicService;

    @GetMapping
    @Operation(summary = "List all topics")
    public ResponseEntity<List<TopicResponse>> getAllTopics() {
        return ResponseEntity.ok(topicService.getAllTopics());
    }

    @PostMapping
    @Operation(summary = "Create a new topic (ADMIN only)")
    public ResponseEntity<TopicResponse> createTopic(
            @Valid @RequestBody CreateTopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(topicService.createTopic(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a topic by ID (ADMIN only)")
    public ResponseEntity<Void> deleteTopic(@PathVariable UUID id) {
        topicService.deleteTopic(id);
        return ResponseEntity.noContent().build();
    }
}