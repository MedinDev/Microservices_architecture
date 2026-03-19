package com.microservices.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.event.DomainEvent;
import java.util.HashMap;
import java.util.Map;
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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class CommonKafkaConfiguration {

    @Value("${spring.kafka.bootstrap-servers:localhost:19092,localhost:19093}")
    private String bootstrapServers;

    @Value("${app.kafka.group-id:microservices-platform}")
    private String groupId;

    @Value("${spring.application.name:microservices-service}")
    private String applicationName;

    @Value("${app.kafka.listener.concurrency:3}")
    private Integer listenerConcurrency;

    @Value("${app.kafka.topic.partitions:3}")
    private Integer topicPartitions;

    @Value("${app.kafka.topic.replication-factor:2}")
    private Short topicReplicationFactor;

    @Value("${app.kafka.topic.retention-ms:604800000}")
    private Long topicRetentionMs;

    @Value("${app.kafka.topic.dlt-retention-ms:1209600000}")
    private Long dltRetentionMs;

    @Value("${spring.kafka.producer.batch-size:65536}")
    private Integer producerBatchSize;

    @Value("${spring.kafka.producer.properties.linger.ms:10}")
    private Integer producerLingerMs;

    @Value("${spring.kafka.producer.compression-type:lz4}")
    private String producerCompressionType;

    @Value("${spring.kafka.consumer.max-poll-records:200}")
    private Integer consumerMaxPollRecords;

    @Value("${spring.kafka.consumer.fetch-min-size:1024}")
    private Integer consumerFetchMinSize;

    @Bean
    public ProducerFactory<String, DomainEvent> producerFactory(ObjectMapper objectMapper) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        properties.put(ProducerConfig.BATCH_SIZE_CONFIG, producerBatchSize);
        properties.put(ProducerConfig.LINGER_MS_CONFIG, producerLingerMs);
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producerCompressionType);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        DefaultKafkaProducerFactory<String, DomainEvent> producerFactory =
            new DefaultKafkaProducerFactory<>(properties, new StringSerializer(), new JsonSerializer<>(objectMapper));
        producerFactory.setTransactionIdPrefix(applicationName + "-tx-");
        return producerFactory;
    }

    @Bean
    public KafkaTemplate<String, DomainEvent> kafkaTemplate(ProducerFactory<String, DomainEvent> producerFactory) {
        KafkaTemplate<String, DomainEvent> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        kafkaTemplate.setObservationEnabled(true);
        return kafkaTemplate;
    }

    @Bean
    public ConsumerFactory<String, DomainEvent> consumerFactory(ObjectMapper objectMapper) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerMaxPollRecords);
        properties.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, consumerFetchMinSize);
        properties.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        JsonDeserializer<DomainEvent> valueDeserializer = new JsonDeserializer<>(DomainEvent.class, objectMapper);
        valueDeserializer.addTrustedPackages("com.microservices.common.event", "java.util");
        return new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DomainEvent> kafkaListenerContainerFactory(
        ConsumerFactory<String, DomainEvent> consumerFactory,
        KafkaTemplate<String, DomainEvent> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, DomainEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(listenerConcurrency);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> switch (record.topic()) {
                case KafkaTopics.ORDER_EVENTS -> new org.apache.kafka.common.TopicPartition(KafkaTopics.ORDER_EVENTS_DLT, record.partition());
                case KafkaTopics.PAYMENT_EVENTS -> new org.apache.kafka.common.TopicPartition(KafkaTopics.PAYMENT_EVENTS_DLT, record.partition());
                default -> new org.apache.kafka.common.TopicPartition(KafkaTopics.NOTIFICATION_EVENTS_DLT, record.partition());
            }
        );
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L)));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.getContainerProperties().setPollTimeout(3000L);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS)
            .partitions(topicPartitions)
            .replicas(topicReplicationFactor)
            .config("retention.ms", String.valueOf(topicRetentionMs))
            .config("cleanup.policy", "delete")
            .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS)
            .partitions(topicPartitions)
            .replicas(topicReplicationFactor)
            .config("retention.ms", String.valueOf(topicRetentionMs))
            .config("cleanup.policy", "delete")
            .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATION_EVENTS)
            .partitions(topicPartitions)
            .replicas(topicReplicationFactor)
            .config("retention.ms", String.valueOf(topicRetentionMs))
            .config("cleanup.policy", "delete")
            .build();
    }

    @Bean
    public NewTopic orderEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS_DLT)
            .partitions(topicPartitions)
            .replicas(topicReplicationFactor)
            .config("retention.ms", String.valueOf(dltRetentionMs))
            .config("cleanup.policy", "delete")
            .build();
    }

    @Bean
    public NewTopic paymentEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS_DLT)
            .partitions(topicPartitions)
            .replicas(topicReplicationFactor)
            .config("retention.ms", String.valueOf(dltRetentionMs))
            .config("cleanup.policy", "delete")
            .build();
    }

    @Bean
    public NewTopic notificationEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATION_EVENTS_DLT)
            .partitions(topicPartitions)
            .replicas(topicReplicationFactor)
            .config("retention.ms", String.valueOf(dltRetentionMs))
            .config("cleanup.policy", "delete")
            .build();
    }
}
