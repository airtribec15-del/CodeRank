package com.coderank.execution.config;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.common.event.CodeExecutionResultEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for coderank-execution.
 *
 * Consumer  ← code.execution.requests   (handled by ExecutionRequestConsumer)
 * Producer  → code.execution.results    (published by CodeExecutionService)
 *
 * Key decisions:
 *  - ErrorHandlingDeserializer wraps JsonDeserializer → bad JSON payload goes
 *    to DLT instead of crashing the consumer partition.
 *  - Offset mode MANUAL_IMMEDIATE on consumer factory — the consumer acks after
 *    dispatching to the executionTaskExecutor thread pool.
 *  - Idempotent producer with acks=all on result publisher.
 *  - concurrency(1): @RetryableTopic registers a second internal listener
 *    container on the main topic for retry-classification routing. With
 *    concurrency > 1 this creates multiple active containers that all receive
 *    the same message, causing every event to be dispatched N times.
 *    Horizontal throughput scaling belongs at the partition/replica level
 *    (more coderank-execution instances), not within a single JVM.
 *    The executionTaskExecutor thread pool handles intra-JVM parallelism.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ================================================================== //
    //  CONSUMER  (execution requests)                                    //
    // ================================================================== //

    @Bean
    public ConsumerFactory<String, CodeExecutionRequestEvent> executionRequestConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 5);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.coderank.common.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CodeExecutionRequestEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CodeExecutionRequestEvent>
    kafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, CodeExecutionRequestEvent>();
        factory.setConsumerFactory(executionRequestConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // FIX: concurrency MUST be 1.
        // @RetryableTopic registers a second internal listener container on the
        // main topic for retry classification. With concurrency > 1, multiple
        // container instances are created and all receive the same message,
        // causing every event to be dispatched (concurrency) times.
        // Scale throughput horizontally via additional coderank-execution
        // replicas — each replica gets its own partition assignment.
        factory.setConcurrency(1);
        return factory;
    }

    // ================================================================== //
    //  PRODUCER  (execution results)                                     //
    // ================================================================== //

    @Bean
    public ProducerFactory<String, CodeExecutionResultEvent> resultProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, CodeExecutionResultEvent> resultKafkaTemplate() {
        return new KafkaTemplate<>(resultProducerFactory());
    }

    // ================================================================== //
    //  TOPICS                                                             //
    // ================================================================== //

    @Bean public NewTopic executionRequestsTopic() {
        return TopicBuilder.name(KafkaTopics.EXECUTION_REQUESTS).partitions(3).replicas(1).build();
    }

    @Bean public NewTopic executionResultsTopic() {
        return TopicBuilder.name(KafkaTopics.EXECUTION_RESULTS).partitions(3).replicas(1).build();
    }

    @Bean public NewTopic executionRequestsDltTopic() {
        return TopicBuilder.name(KafkaTopics.EXECUTION_REQUESTS_DLT).partitions(1).replicas(1).build();
    }
}