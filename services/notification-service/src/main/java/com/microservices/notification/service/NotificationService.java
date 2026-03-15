package com.microservices.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.dto.NotificationEventPayload;
import com.microservices.common.dto.OrderEventPayload;
import com.microservices.common.dto.PaymentEventPayload;
import com.microservices.common.event.DomainEvent;
import com.microservices.common.event.EventType;
import com.microservices.common.exception.ResourceNotFoundException;
import com.microservices.common.kafka.KafkaTopics;
import com.microservices.common.tracing.CorrelationIdUtil;
import com.microservices.notification.domain.NotificationChannel;
import com.microservices.notification.domain.NotificationEntity;
import com.microservices.notification.domain.NotificationStatus;
import com.microservices.notification.domain.ProcessedNotificationEventEntity;
import com.microservices.notification.domain.UserPreferenceEntity;
import com.microservices.notification.dto.NotificationPreferenceRequest;
import com.microservices.notification.dto.NotificationPreferenceResponse;
import com.microservices.notification.dto.NotificationResponse;
import com.microservices.notification.repository.NotificationRepository;
import com.microservices.notification.repository.ProcessedNotificationEventRepository;
import com.microservices.notification.repository.UserPreferenceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final JavaMailSender mailSender;
    private final SimpMessagingTemplate messagingTemplate;
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ProcessedNotificationEventRepository processedNotificationEventRepository;
    private final MeterRegistry meterRegistry;

    public NotificationService(
        NotificationRepository notificationRepository,
        UserPreferenceRepository preferenceRepository,
        JavaMailSender mailSender,
        SimpMessagingTemplate messagingTemplate,
        KafkaTemplate<String, DomainEvent> kafkaTemplate,
        ObjectMapper objectMapper,
        ProcessedNotificationEventRepository processedNotificationEventRepository,
        MeterRegistry meterRegistry
    ) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.mailSender = mailSender;
        this.messagingTemplate = messagingTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.processedNotificationEventRepository = processedNotificationEventRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public NotificationResponse markAsRead(Long id) {
        NotificationEntity entity = findNotification(id);
        entity.setReadFlag(true);
        entity.setStatus(NotificationStatus.READ);
        return toResponse(notificationRepository.save(entity));
    }

    @Transactional
    public void deleteNotification(Long id) {
        NotificationEntity entity = findNotification(id);
        notificationRepository.delete(entity);
    }

    @Transactional
    public NotificationPreferenceResponse updatePreferences(NotificationPreferenceRequest request) {
        UserPreferenceEntity pref = preferenceRepository.findByUserId(request.userId()).orElseGet(UserPreferenceEntity::new);
        pref.setUserId(request.userId());
        pref.setEmailEnabled(request.emailEnabled());
        pref.setSmsEnabled(request.smsEnabled());
        pref.setPushEnabled(request.pushEnabled());
        pref.setInAppEnabled(request.inAppEnabled());
        pref = preferenceRepository.save(pref);
        return new NotificationPreferenceResponse(
            pref.getUserId(),
            pref.getEmailEnabled(),
            pref.getSmsEnabled(),
            pref.getPushEnabled(),
            pref.getInAppEnabled()
        );
    }

    @KafkaListener(topics = {KafkaTopics.ORDER_EVENTS, KafkaTopics.PAYMENT_EVENTS}, groupId = "notification-service-domain-events")
    @Transactional
    public void onDomainEvent(DomainEvent event) {
        if (processedNotificationEventRepository.existsByEventId(event.eventId())) {
            return;
        }
        Long userId = extractUserId(event);
        if (userId == null) {
            return;
        }
        UserPreferenceEntity pref = preferenceRepository.findByUserId(userId).orElseGet(() -> defaultPreferences(userId));
        String title = buildTitle(event);
        String message = buildMessage(event);
        List<NotificationChannel> channels = enabledChannels(pref);
        for (NotificationChannel channel : channels) {
            NotificationEntity notification = new NotificationEntity();
            notification.setUserId(userId);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setChannel(channel);
            notification.setStatus(NotificationStatus.PENDING);
            notification.setReadFlag(false);
            notification.setCreatedAt(Instant.now());
            notification = notificationRepository.save(notification);
            sendByChannel(notification);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);
            incrementNotificationSentCounter(channel);
            messagingTemplate.convertAndSend("/topic/notifications/" + userId, toResponse(notification));
            publishSentEvent(notification, event.correlationId());
        }
        markEventProcessed(event.eventId());
    }

    private void incrementNotificationSentCounter(NotificationChannel channel) {
        Counter.builder("business_notifications_sent_total")
            .tag("channel", channel.name())
            .register(meterRegistry)
            .increment();
    }

    private void sendByChannel(NotificationEntity notification) {
        if (notification.getChannel() == NotificationChannel.EMAIL) {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo("user" + notification.getUserId() + "@example.com");
            mail.setSubject(notification.getTitle());
            mail.setText(notification.getMessage());
            mailSender.send(mail);
        }
    }

    private void publishSentEvent(NotificationEntity notification) {
        publishSentEvent(notification, CorrelationIdUtil.currentOrNew());
    }

    private void publishSentEvent(NotificationEntity notification, String correlationId) {
        NotificationEventPayload payload = new NotificationEventPayload(
            notification.getId(),
            notification.getUserId(),
            notification.getChannel().name(),
            notification.getTitle(),
            notification.getStatus().name(),
            notification.getSentAt()
        );
        DomainEvent event = new DomainEvent(
            UUID.randomUUID(),
            EventType.NOTIFICATION_SENT,
            "NOTIFICATION",
            String.valueOf(notification.getId()),
            "notification-service",
            Instant.now(),
            correlationId,
            objectMapper.convertValue(payload, Map.class)
        );
        kafkaTemplate.executeInTransaction(
            operations -> operations.send(KafkaTopics.NOTIFICATION_EVENTS, String.valueOf(notification.getUserId()), event)
        );
    }

    private void markEventProcessed(UUID eventId) {
        ProcessedNotificationEventEntity processedEvent = new ProcessedNotificationEventEntity();
        processedEvent.setEventId(eventId);
        processedEvent.setConsumerGroup("notification-service-domain-events");
        processedEvent.setProcessedAt(Instant.now());
        processedNotificationEventRepository.save(processedEvent);
    }

    private NotificationEntity findNotification(Long id) {
        return notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
    }

    private UserPreferenceEntity defaultPreferences(Long userId) {
        UserPreferenceEntity pref = new UserPreferenceEntity();
        pref.setUserId(userId);
        pref.setEmailEnabled(true);
        pref.setSmsEnabled(false);
        pref.setPushEnabled(true);
        pref.setInAppEnabled(true);
        return preferenceRepository.save(pref);
    }

    private List<NotificationChannel> enabledChannels(UserPreferenceEntity pref) {
        List<NotificationChannel> channels = new ArrayList<>();
        if (pref.getEmailEnabled()) {
            channels.add(NotificationChannel.EMAIL);
        }
        if (pref.getSmsEnabled()) {
            channels.add(NotificationChannel.SMS);
        }
        if (pref.getPushEnabled()) {
            channels.add(NotificationChannel.PUSH);
        }
        if (pref.getInAppEnabled()) {
            channels.add(NotificationChannel.IN_APP);
        }
        return channels;
    }

    private Long extractUserId(DomainEvent event) {
        if (event.eventType() == EventType.ORDER_CREATED || event.eventType() == EventType.ORDER_CANCELLED) {
            return objectMapper.convertValue(event.payload(), OrderEventPayload.class).userId();
        }
        if (event.eventType() == EventType.PAYMENT_PROCESSED || event.eventType() == EventType.PAYMENT_FAILED || event.eventType() == EventType.REFUND_PROCESSED) {
            return objectMapper.convertValue(event.payload(), PaymentEventPayload.class).userId();
        }
        return null;
    }

    private String buildTitle(DomainEvent event) {
        return switch (event.eventType()) {
            case ORDER_CREATED -> "Order Created";
            case ORDER_CANCELLED -> "Order Cancelled";
            case PAYMENT_PROCESSED -> "Payment Processed";
            case PAYMENT_FAILED -> "Payment Failed";
            case REFUND_PROCESSED -> "Refund Processed";
            default -> "Notification";
        };
    }

    private String buildMessage(DomainEvent event) {
        return switch (event.eventType()) {
            case ORDER_CREATED -> "Your order has been created successfully.";
            case ORDER_CANCELLED -> "Your order was cancelled.";
            case PAYMENT_PROCESSED -> "Your payment has been processed.";
            case PAYMENT_FAILED -> "Your payment failed and requires attention.";
            case REFUND_PROCESSED -> "Your refund has been processed.";
            default -> "You have a new update.";
        };
    }

    private NotificationResponse toResponse(NotificationEntity entity) {
        return new NotificationResponse(
            entity.getId(),
            entity.getUserId(),
            entity.getTitle(),
            entity.getMessage(),
            entity.getChannel(),
            entity.getStatus(),
            entity.getReadFlag(),
            entity.getCreatedAt(),
            entity.getSentAt()
        );
    }
}
