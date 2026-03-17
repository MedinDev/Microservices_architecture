package com.microservices.common.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.event.DomainEvent;
import com.microservices.common.event.EventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.ORDER_EVENTS})
class CommonKafkaIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, DomainEvent> consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void publishesAndConsumesDomainEvent() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        KafkaTemplate<String, DomainEvent> kafkaTemplate = new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(producerProps, new StringSerializer(), new JsonSerializer<>(objectMapper))
        );
        kafkaTemplate.setObservationEnabled(false);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("phase5-group", "false", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        JsonDeserializer<DomainEvent> valueDeserializer = new JsonDeserializer<>(DomainEvent.class, objectMapper);
        valueDeserializer.addTrustedPackages("com.microservices.common.event", "java.util");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), valueDeserializer).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.ORDER_EVENTS);

        DomainEvent event = new DomainEvent(
            UUID.randomUUID(),
            EventType.ORDER_CREATED,
            "ORDER",
            "901",
            "order-service",
            Instant.now(),
            "corr-901",
            Map.of("orderId", 901, "userId", 15)
        );
        kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, "901", event);

        ConsumerRecord<String, DomainEvent> record = KafkaTestUtils.getSingleRecord(consumer, KafkaTopics.ORDER_EVENTS);
        assertEquals(EventType.ORDER_CREATED, record.value().eventType());
        assertEquals("901", record.key());
    }
}
