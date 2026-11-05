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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
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

    @Value("${app.admin.email}")
    private String adminEmail;

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    @Async
    @EventListener
    public void handlePasswordReset(PasswordResetRequestedEvent event) {
        emailService.sendOtpEmail(event.email(), event.name(), event.otp());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TRIGGERS (Doctor & Appointment Events)
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorRegistered(DoctorRegisteredEvent event) {
        log.info("[Event] DoctorRegistered → doctor: {}", event.getDoctorEmail());
        notificationPersistenceService.persistDoctorRegisteredNotification(event);
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
        sendEmailSafely("doctor", () -> emailService.sendDoctorApproved(
                event.getDoctorEmail(), event.getDoctorName(), event.getAdminNote()), event.getDoctorUserId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorRejected(DoctorRejectedEvent event) {
        log.info("[Event] DoctorRejected → doctor: {}", event.getDoctorEmail());
        notificationPersistenceService.persistDoctorRejectedNotification(event);
        sendEmailSafely("doctor", () -> emailService.sendDoctorRejected(
                event.getDoctorEmail(), event.getDoctorName(), event.getReason()), event.getDoctorUserId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentBooked(AppointmentBookedEvent event) {
        log.info("[Event] AppointmentBooked → id: {}", event.getAppointmentId());
        notificationPersistenceService.persistBookedNotifications(event);
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
        String date = event.getAppointmentDate().toString();
        sendEmailSafely("patient", () -> emailService.sendAppointmentReminderPatient(
                event.getPatientEmail(), event.getPatientName(), event.getDoctorName(), date, event.getTimeRange()), event.getAppointmentId());
        sendEmailSafely("doctor", () -> emailService.sendAppointmentReminderDoctor(
                event.getDoctorEmail(), event.getDoctorName(), event.getPatientName(), date, event.getTimeRange()), event.getAppointmentId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Inbox APIs with Redis Caching
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(String email, Pageable pageable) {
        String cacheKey = String.format("notifications:email:%s:page:%d:size:%d",
                email, pageable.getPageNumber(), pageable.getPageSize());

        // 1. Check Redis Cache
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                CachedPageResponse cachedResponse = objectMapper.readValue(cachedJson, CachedPageResponse.class);
                return new PageImpl<>(cachedResponse.getContent(), pageable, cachedResponse.getTotalElements());
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for key {}: {}", cacheKey, e.getMessage());
        }

        // 2. Fallback to MySQL Database
        User user = findUser(email);
        Page<NotificationResponse> responsePage = notificationRepository
                .findByUser_IdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toResponse);

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
        evictUserCache(email); // Invalidate cache on update
        return "Notification marked as read.";
    }

    @Transactional
    public String markAllAsRead(String email) {
        User user = findUser(email);
        notificationRepository.markAllAsRead(user.getId());
        evictUserCache(email); // Invalidate cache on update
        return "All notifications marked as read.";
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(String email) {
        User user = findUser(email);
        long count = notificationRepository.countByUser_IdAndReadFalse(user.getId());
        return new UnreadCountResponse(count);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void evictUserCache(String email) {
        try {
            var keys = redisTemplate.keys("notifications:email:" + email + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Cache eviction failed for user {}: {}", email, e.getMessage());
        }
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