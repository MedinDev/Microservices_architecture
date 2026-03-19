package com.microservices.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.event.DomainEvent;
import com.microservices.common.event.EventType;
import com.microservices.common.security.FieldEncryptionService;
import com.microservices.notification.domain.NotificationChannel;
import com.microservices.notification.domain.NotificationEntity;
import com.microservices.notification.domain.NotificationStatus;
import com.microservices.notification.dto.NotificationResponse;
import com.microservices.notification.repository.NotificationRepository;
import com.microservices.notification.repository.ProcessedNotificationEventRepository;
import com.microservices.notification.repository.UserPreferenceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private ProcessedNotificationEventRepository processedNotificationEventRepository;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository = Mockito.mock(NotificationRepository.class);
        UserPreferenceRepository preferenceRepository = Mockito.mock(UserPreferenceRepository.class);
        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        SimpMessagingTemplate messagingTemplate = new SimpMessagingTemplate(Mockito.mock(MessageChannel.class));
        ProducerFactory<String, DomainEvent> producerFactory = Mockito.mock(ProducerFactory.class);
        KafkaTemplate<String, DomainEvent> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        processedNotificationEventRepository = Mockito.mock(ProcessedNotificationEventRepository.class);

        notificationService = new NotificationService(
            notificationRepository,
            preferenceRepository,
            mailSender,
            messagingTemplate,
            kafkaTemplate,
            new ObjectMapper(),
            processedNotificationEventRepository,
            new SimpleMeterRegistry(),
            new FieldEncryptionService("0123456789abcdef0123456789abcdef"),
            30,
            30
        );
    }

    @Test
    void markAsReadUpdatesState() {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(88L);
        entity.setTitle("Payment");
        entity.setMessage("Payment completed");
        entity.setChannel(NotificationChannel.IN_APP);
        entity.setStatus(NotificationStatus.SENT);
        entity.setReadFlag(false);
        entity.setCreatedAt(Instant.now());

        when(notificationRepository.findById(5L)).thenReturn(Optional.of(entity));
        when(notificationRepository.save(entity)).thenReturn(entity);

        NotificationResponse response = notificationService.markAsRead(5L);

        assertEquals(NotificationStatus.READ, response.status());
        assertEquals(Boolean.TRUE, response.read());
    }

    @Test
    void onDomainEventSkipsDuplicateReplay() {
        DomainEvent event = new DomainEvent(
            UUID.randomUUID(),
            EventType.ORDER_CREATED,
            "ORDER",
            "10",
            "order-service",
            Instant.now(),
            "corr-10",
            Map.of("orderId", 10, "userId", 3, "status", "PAYMENT_PENDING", "totalAmount", 10)
        );
        when(processedNotificationEventRepository.existsByEventId(event.eventId())).thenReturn(true);

        notificationService.onDomainEvent(event);

        verify(notificationRepository, never()).save(any(NotificationEntity.class));
    }
}
