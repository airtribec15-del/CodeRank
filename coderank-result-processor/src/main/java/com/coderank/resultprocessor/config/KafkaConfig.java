package com.coderank.resultprocessor.config;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.common.event.StateUpdateEvent;
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
 * Kafka configuration for coderank-result-processor.
 *
 * <h2>Consumer</h2>
 * Consumes {@link CodeExecutionResultEvent} from {@code code.execution.results}.
 * - ACK mode: MANUAL_IMMEDIATE — offset committed only after all side effects
 *   (Submission update + Redis cache + state-update-events publish) succeed.
 * - ErrorHandlingDeserializer wraps JsonDeserializer so poison messages route
 *   to DLT instead of crashing the consumer.
 *
 * <h2>Producer</h2>
 * Publishes {@link StateUpdateEvent} to {@code state-update-events}.
 * - Idempotent producer with acks=all.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ─── CONSUMER ────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, CodeExecutionResultEvent> resultConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.coderank.common.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CodeExecutionResultEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CodeExecutionResultEvent>
    kafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, CodeExecutionResultEvent>();
        factory.setConsumerFactory(resultConsumerFactory());
        // MANUAL_IMMEDIATE: ACK after all side effects complete in the consumer
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // 3 threads — matches partitions on code.execution.results
        factory.setConcurrency(3);
        return factory;
    }

    // ─── PRODUCER ────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, StateUpdateEvent> stateUpdateProducerFactory() {
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
    public KafkaTemplate<String, StateUpdateEvent> stateUpdateKafkaTemplate() {
        return new KafkaTemplate<>(stateUpdateProducerFactory());
    }

    // ─── TOPICS ──────────────────────────────────────────────────────────────

    /**
     * Result Processor declares the DLT it writes to when retries for
     * code.execution.results are exhausted. 1 partition, no replication in dev.
     */
    @Bean
    public NewTopic executionResultsDltTopic() {
        return TopicBuilder.name(KafkaTopics.EXECUTION_RESULTS_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * state-update-events — consumed by Problem Service (Step 7).
     * 3 partitions to allow parallel consumption per-problem partition.
     */
    @Bean
    public NewTopic stateUpdateEventsTopic() {
        return TopicBuilder.name(KafkaTopics.STATE_UPDATE_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * DLT for state-update-events — Problem Service consumer failures.
     */
    @Bean
    public NewTopic stateUpdateEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.STATE_UPDATE_EVENTS_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }
}