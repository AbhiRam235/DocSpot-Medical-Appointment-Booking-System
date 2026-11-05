package com.docspot.service;

import com.docspot.dto.response.NotificationResponse;
import com.docspot.dto.response.UnreadCountResponse;
import com.docspot.entity.Notification;
import com.docspot.entity.User;
import com.docspot.event.*;
import com.docspot.exception.ResourceNotFoundException;
import com.docspot.exception.UnauthorizedException;
import com.docspot.repository.NotificationRepository;
import com.docspot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;           // Spring Boot 3. Use javax.annotation.PostConstruct on Boot 2
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationPersistenceService notificationPersistenceService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager txManager;      // NEW (final, so Lombok injects it)

    @Value("${app.admin.email}")
    private String adminEmail;

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    // NEW: not final, so it is NOT part of the Lombok constructor. Built in @PostConstruct.
    private TransactionTemplate readOnlyTx;

    @PostConstruct
    void initReadOnlyTx() {
        readOnlyTx = new TransactionTemplate(txManager);
        readOnlyTx.setReadOnly(true);
    }

    @Async
    @EventListener
    public void handlePasswordReset(PasswordResetRequestedEvent event) {
        emailService.sendOtpEmail(event.email(), event.name(), event.otp());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TRIGGERS (Doctor & Appointment Events)
    //  NEW: after a notification row is created, evict the recipients' cached
    //  inbox pages and unread count, otherwise they stay stale for up to 60 s.
    //  (Assumes NotificationPersistenceService commits before it returns,
    //  for example @Transactional(REQUIRES_NEW).)
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorRegistered(DoctorRegisteredEvent event) {
        log.info("[Event] DoctorRegistered → doctor: {}", event.getDoctorEmail());
        notificationPersistenceService.persistDoctorRegisteredNotification(event);
        evictUsers(adminEmail, event.getDoctorEmail());
        sendEmailSafely("admin", () -> emailService.sendAdminNewDoctorAlert(
                adminEmail, event.getDoctorName(), event.getDoctorEmail(), event.getSpeciality()), event.getDoctorUserId());
        sendEmailSafely("doctor", () -> emailService.sendDoctorApplicationReceived(
                event.getDoctorEmail(), event.getDoctorName()), event.getDoctorUserId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorApproved(DoctorApprovedEvent event) {
        log.info("[Event] DoctorApproved → doctor: {}", event.getDoctorEmail());
        notificationPersistenceService.persistDoctorApprovedNotification(event);
        evictUsers(event.getDoctorEmail());
        sendEmailSafely("doctor", () -> emailService.sendDoctorApproved(
                event.getDoctorEmail(), event.getDoctorName(), event.getAdminNote()), event.getDoctorUserId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorRejected(DoctorRejectedEvent event) {
        log.info("[Event] DoctorRejected → doctor: {}", event.getDoctorEmail());
        notificationPersistenceService.persistDoctorRejectedNotification(event);
        evictUsers(event.getDoctorEmail());
        sendEmailSafely("doctor", () -> emailService.sendDoctorRejected(
                event.getDoctorEmail(), event.getDoctorName(), event.getReason()), event.getDoctorUserId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentBooked(AppointmentBookedEvent event) {
        log.info("[Event] AppointmentBooked → id: {}", event.getAppointmentId());
        notificationPersistenceService.persistBookedNotifications(event);
        evictUsers(event.getPatientEmail(), event.getDoctorEmail());
        String date = event.getAppointmentDate().toString();
        sendEmailSafely("patient", () -> emailService.sendAppointmentConfirmedPatient(
                event.getPatientEmail(), event.getPatientName(), event.getDoctorName(), date, event.getTimeRange()), event.getAppointmentId());
        sendEmailSafely("doctor", () -> emailService.sendAppointmentConfirmedDoctor(
                event.getDoctorEmail(), event.getDoctorName(), event.getPatientName(), date, event.getTimeRange()), event.getAppointmentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        log.info("[Event] AppointmentCancelled → id: {}", event.getAppointmentId());
        notificationPersistenceService.persistCancelledNotifications(event);
        evictUsers(event.getPatientEmail(), event.getDoctorEmail());
        String date = event.getAppointmentDate().toString();
        sendEmailSafely("patient", () -> emailService.sendAppointmentCancelledPatient(
                event.getPatientEmail(), event.getPatientName(), event.getDoctorName(), date, event.getTimeRange()), event.getAppointmentId());
        sendEmailSafely("doctor", () -> emailService.sendAppointmentCancelledDoctor(
                event.getDoctorEmail(), event.getDoctorName(), event.getPatientName(), date, event.getTimeRange()), event.getAppointmentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCompleted(AppointmentCompletedEvent event) {
        log.info("[Event] AppointmentCompleted → id: {}", event.getAppointmentId());
        notificationPersistenceService.persistCompletedNotifications(event);
        evictUsers(event.getPatientEmail(), event.getDoctorEmail());
        String date = event.getAppointmentDate().toString();
        sendEmailSafely("patient", () -> emailService.sendAppointmentCompletedPatient(
                event.getPatientEmail(), event.getPatientName(), event.getDoctorName(), date, event.getTimeRange()), event.getAppointmentId());
        sendEmailSafely("doctor", () -> emailService.sendAppointmentCompletedDoctor(
                event.getDoctorEmail(), event.getDoctorName(), event.getPatientName(), date, event.getTimeRange()), event.getAppointmentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentReminder(AppointmentReminderEvent event) {
        log.info("[Event] AppointmentReminder → id: {}", event.getAppointmentId());
        notificationPersistenceService.persistReminderNotifications(event);
        evictUsers(event.getPatientEmail(), event.getDoctorEmail());
        String date = event.getAppointmentDate().toString();
        sendEmailSafely("patient", () -> emailService.sendAppointmentReminderPatient(
                event.getPatientEmail(), event.getPatientName(), event.getDoctorName(), date, event.getTimeRange()), event.getAppointmentId());
        sendEmailSafely("doctor", () -> emailService.sendAppointmentReminderDoctor(
                event.getDoctorEmail(), event.getDoctorName(), event.getPatientName(), date, event.getTimeRange()), event.getAppointmentId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Inbox APIs with Redis Caching
    // ═══════════════════════════════════════════════════════════════════════

    // CHANGED: no @Transactional here. With it, Spring borrowed a DB connection and
    // made several round trips to MySQL before the method body started, even on a
    // Redis cache hit. Now a cache hit never touches MySQL.
    public Page<NotificationResponse> getMyNotifications(String email, Pageable pageable) {
        String cacheKey = String.format("notifications:email:%s:page:%d:size:%d",
                email, pageable.getPageNumber(), pageable.getPageSize());

        // 1. Check Redis Cache (no DB connection involved)
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                CachedPageResponse cachedResponse = objectMapper.readValue(cachedJson, CachedPageResponse.class);
                return new PageImpl<>(cachedResponse.getContent(), pageable, cachedResponse.getTotalElements());
            }
        } catch (Exception e) {
            // If this appears on EVERY request, the cache is not working and every call hits MySQL.
            log.warn("Redis cache read failed for key {}: {}", cacheKey, e.getMessage());
        }

        // 2. Fallback to MySQL, inside one short read-only transaction
        Page<NotificationResponse> responsePage = readOnlyTx.execute(status -> {
            User user = findUser(email);
            return notificationRepository
                    .findByUser_IdOrderByCreatedAtDesc(user.getId(), pageable)
                    .map(this::toResponse);   // map inside the transaction (lazy fields)
        });

        // 3. Populate Redis Cache
        try {
            CachedPageResponse cachePayload = new CachedPageResponse(responsePage.getContent(), responsePage.getTotalElements());
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(cachePayload), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis cache write failed for key {}: {}", cacheKey, e.getMessage());
        }

        return responsePage;
    }

    @Transactional
    public String markOneAsRead(Long notificationId, String email) {
        User user = findUser(email);
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        if (!notif.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not authorised to update this notification.");
        }

        notif.setRead(true);
        notificationRepository.save(notif);
        evictAfterCommit(email); // CHANGED: evict only after the DB commit
        return "Notification marked as read.";
    }

    @Transactional
    public String markAllAsRead(String email) {
        User user = findUser(email);
        notificationRepository.markAllAsRead(user.getId());
        evictAfterCommit(email); // CHANGED: evict only after the DB commit
        return "All notifications marked as read.";
    }

    // CHANGED: cached in Redis (this is the badge the frontend usually polls) and
    // no @Transactional on the cache-hit path. The key matches the eviction pattern.
    public UnreadCountResponse getUnreadCount(String email) {
        String cacheKey = "notifications:email:" + email + ":unread";

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return new UnreadCountResponse(Long.parseLong(cached));
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for key {}: {}", cacheKey, e.getMessage());
        }

        Long count = readOnlyTx.execute(status -> {
            User user = findUser(email);
            return notificationRepository.countByUser_IdAndReadFalse(user.getId());
        });

        try {
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(count), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis cache write failed for key {}: {}", cacheKey, e.getMessage());
        }

        return new UnreadCountResponse(count);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void evictUsers(String... emails) {
        for (String email : emails) {
            if (email != null) {
                evictUserCache(email);
            }
        }
    }

    // Evict after the surrounding DB transaction commits. Evicting inside the
    // transaction lets a concurrent read re-cache the OLD data before the commit.
    private void evictAfterCommit(String email) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictUserCache(email);
                }
            });
        } else {
            evictUserCache(email);
        }
    }

    // CHANGED: SCAN instead of KEYS. KEYS blocks Redis while it walks every key.
    private void evictUserCache(String email) {
        String pattern = "notifications:email:" + escapeGlob(email) + ":*";
        try {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Cache eviction failed for user {}: {}", email, e.getMessage());
        }
    }

    // Emails can contain glob characters such as * ? [ ]; escape them for the SCAN pattern.
    private static String escapeGlob(String s) {
        return s.replaceAll("([\\\\*?\\[\\]])", "\\\\$1");
    }

    private static class CachedPageResponse {
        private List<NotificationResponse> content;
        private long totalElements;

        public CachedPageResponse() {}

        public CachedPageResponse(List<NotificationResponse> content, long totalElements) {
            this.content = content;
            this.totalElements = totalElements;
        }

        public List<NotificationResponse> getContent() { return content; }
        public long getTotalElements() { return totalElements; }

        // Setters added so Jackson deserialization does not depend on field inference.
        public void setContent(List<NotificationResponse> content) { this.content = content; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    }

    private void sendEmailSafely(String recipientType, Runnable emailAction, Long relatedId) {
        try {
            emailAction.run();
        } catch (Exception e) {
            log.error("[Event] Failed to send {} email for id={}: {}", recipientType, relatedId, e.getMessage(), e);
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder().notificationId(n.getId()).title(n.getTitle())
                .message(n.getMessage()).read(n.isRead()).createdAt(n.getCreatedAt()).build();
    }
}