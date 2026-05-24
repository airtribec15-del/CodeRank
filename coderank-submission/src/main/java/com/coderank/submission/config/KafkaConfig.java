package com.coderank.submission.config;

import com.coderank.common.constants.KafkaTopics;
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
 * Kafka configuration for coderank-submission.
 *
 * Producer  → code.execution.requests   (fired by SubmissionService)
 * Consumer  ← code.execution.results    (handled by ExecutionResultConsumer)
 *
 * Retry topics and DLT are created by @RetryableTopic — we only declare
 * the two primary topics and the DLT topic here via AdminClient beans
 * so they exist with the correct partition/replica settings.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ================================================================== //
    //  PRODUCER  (execution requests)                                    //
    // ================================================================== //

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        // Idempotent producer: exactly-once delivery guarantee
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ================================================================== //
    //  CONSUMER  (execution results)                                     //
    // ================================================================== //

    @Bean
    public ConsumerFactory<String, CodeExecutionResultEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Manual ack — offset committed only after successful DB write
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        // ErrorHandlingDeserializer wraps JsonDeserializer so a bad JSON payload
        // does NOT crash the consumer — it produces a DeserializationException
        // which @RetryableTopic routes straight to DLT (non-retryable).
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
        factory.setConsumerFactory(consumerFactory());
        // MANUAL_IMMEDIATE: ack offset right after acknowledgment.acknowledge()
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(2);
        return factory;
    }

    // ================================================================== //
    //  TOPICS  (primary + DLT — retry topics auto-created by @RetryableTopic)
    // ================================================================== //

    @Bean public NewTopic executionRequestsTopic() {
        return TopicBuilder.name(KafkaTopics.EXECUTION_REQUESTS).partitions(3).replicas(1).build();
    }

    @Bean public NewTopic executionResultsTopic() {
        return TopicBuilder.name(KafkaTopics.EXECUTION_RESULTS).partitions(3).replicas(1).build();
    }

    @Bean public NewTopic executionResultsDltTopic() {
        // DLT needs only 1 partition — messages arrive here only after exhausted retries
        return TopicBuilder.name(KafkaTopics.EXECUTION_REQUESTS_DLT).partitions(1).replicas(1).build();
    }
}
