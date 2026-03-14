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
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> switch (record.topic()) {
                case KafkaTopics.ORDER_EVENTS -> new org.apache.kafka.common.TopicPartition(KafkaTopics.ORDER_EVENTS_DLT, record.partition());
                case KafkaTopics.PAYMENT_EVENTS -> new org.apache.kafka.common.TopicPartition(KafkaTopics.PAYMENT_EVENTS_DLT, record.partition());
                default -> new org.apache.kafka.common.TopicPartition(KafkaTopics.NOTIFICATION_EVENTS_DLT, record.partition());
            }
        );
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L)));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return new NewTopic(KafkaTopics.ORDER_EVENTS, 3, (short) 2);
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return new NewTopic(KafkaTopics.PAYMENT_EVENTS, 3, (short) 2);
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return new NewTopic(KafkaTopics.NOTIFICATION_EVENTS, 3, (short) 2);
    }

    @Bean
    public NewTopic orderEventsDltTopic() {
        return new NewTopic(KafkaTopics.ORDER_EVENTS_DLT, 3, (short) 2);
    }

    @Bean
    public NewTopic paymentEventsDltTopic() {
        return new NewTopic(KafkaTopics.PAYMENT_EVENTS_DLT, 3, (short) 2);
    }

    @Bean
    public NewTopic notificationEventsDltTopic() {
        return new NewTopic(KafkaTopics.NOTIFICATION_EVENTS_DLT, 3, (short) 2);
    }
}
