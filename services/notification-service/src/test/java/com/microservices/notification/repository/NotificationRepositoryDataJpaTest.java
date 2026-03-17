package com.microservices.notification.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microservices.notification.domain.NotificationChannel;
import com.microservices.notification.domain.NotificationEntity;
import com.microservices.notification.domain.NotificationStatus;
import com.microservices.notification.domain.UserPreferenceEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {"spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class NotificationRepositoryDataJpaTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Test
    void findByUserIdOrderByCreatedAtDescReturnsNewestFirst() {
        notificationRepository.save(notification(10L, "first", Instant.parse("2026-03-10T14:00:00Z")));
        notificationRepository.save(notification(10L, "second", Instant.parse("2026-03-10T14:02:00Z")));
        notificationRepository.save(notification(11L, "third", Instant.parse("2026-03-10T14:03:00Z")));

        List<NotificationEntity> results = notificationRepository.findByUserIdOrderByCreatedAtDesc(10L);

        assertEquals(2, results.size());
        assertEquals("second", results.get(0).getTitle());
        assertEquals("first", results.get(1).getTitle());
    }

    @Test
    void findByUserIdReturnsPreference() {
        UserPreferenceEntity pref = new UserPreferenceEntity();
        pref.setUserId(22L);
        pref.setEmailEnabled(true);
        pref.setSmsEnabled(false);
        pref.setPushEnabled(true);
        pref.setInAppEnabled(true);
        userPreferenceRepository.save(pref);

        assertTrue(userPreferenceRepository.findByUserId(22L).isPresent());
    }

    private NotificationEntity notification(Long userId, String title, Instant createdAt) {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setMessage("message-" + title);
        entity.setChannel(NotificationChannel.IN_APP);
        entity.setStatus(NotificationStatus.SENT);
        entity.setReadFlag(false);
        entity.setCreatedAt(createdAt);
        entity.setSentAt(createdAt);
        return entity;
    }
}
