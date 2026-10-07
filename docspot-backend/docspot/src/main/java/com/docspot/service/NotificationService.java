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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Orchestrates every notification trigger — the single consumer of every
 * Spring event published by AuthService, AdminService, and AppointmentService.
 *
 * ── Why @TransactionalEventListener(AFTER_COMMIT), not @EventListener ─────
 * The publishers all call eventPublisher.publishEvent(...) from INSIDE a
 * @Transactional method, right after saving. With plain @EventListener,
 * Spring invokes the listener synchronously as part of that same publish
 * call — before the surrounding transaction has actually committed. Two
 * ways that goes wrong:
 *   1. The listener (via @Async) could start running on another thread
 *      while the original transaction is still mid-flight, or worse,
 *      while it's about to roll back — meaning we'd email a patient about
 *      a booking that database-wise never happened.
 *   2. Even without the race, firing before commit means if the original
 *      transaction DOES roll back after the event fires (e.g. the
 *      DataIntegrityViolationException race-guard in AppointmentService.book()
 *      catches a collision after publishing — actually no, we publish only
 *      after a successful save there, but the general risk applies to any
 *      future code path that publishes before its final commit point).
 *
 * AFTER_COMMIT closes both gaps — Spring guarantees this listener only
 * fires once the transaction that published the event has successfully
 * committed. If it rolls back instead, the listener never runs at all.
 *
 * ── Why persistence is split into NotificationPersistenceService ──────────
 * By AFTER_COMMIT time there's no ambient transaction left — see that
 * class's Javadoc for the full reasoning.
 *
 * ── Why every email call is wrapped in sendEmailSafely ─────────────────────
 * Two independent recipients (patient + doctor) per appointment event. If
 * the patient's email throws (even after @Retryable's 3 attempts are
 * exhausted), we still want the doctor's email attempted — one failure
 * shouldn't cascade into skipping the other party entirely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationPersistenceService notificationPersistenceService;

    @Value("${app.admin.email}")
    private String adminEmail;

    // ═══════════════════════════════════════════════════════════════════════
    //  TRIGGER 1 — Doctor registers
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorRegistered(DoctorRegisteredEvent event) {
        log.info("[Event] DoctorRegistered → doctor: {}", event.getDoctorEmail());

        boolean persisted = notificationPersistenceService.persistDoctorRegisteredNotification(event);
        if (!persisted) {
            log.warn("[Event] In-app notification persistence failed for doctor registration id={} — emails still proceed.",
                    event.getDoctorUserId());
        }

        sendEmailSafely("admin", () -> emailService.sendAdminNewDoctorAlert(
                adminEmail, event.getDoctorName(), event.getDoctorEmail(), event.getSpeciality()), event.getDoctorUserId());

        sendEmailSafely("doctor", () -> emailService.sendDoctorApplicationReceived(
                event.getDoctorEmail(), event.getDoctorName()), event.getDoctorUserId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TRIGGER 2 — Doctor approved
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorApproved(DoctorApprovedEvent event) {
        log.info("[Event] DoctorApproved → doctor: {}", event.getDoctorEmail());

        boolean persisted = notificationPersistenceService.persistDoctorApprovedNotification(event);
        if (!persisted) {
            log.warn("[Event] In-app notification persistence failed for doctor approval id={} — email still proceeds.",
                    event.getDoctorUserId());
        }

        sendEmailSafely("doctor", () -> emailService.sendDoctorApproved(
                event.getDoctorEmail(), event.getDoctorName(), event.getAdminNote()), event.getDoctorUserId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TRIGGER 3 — Doctor rejected
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorRejected(DoctorRejectedEvent event) {
        log.info("[Event] DoctorRejected → doctor: {}", event.getDoctorEmail());

        boolean persisted = notificationPersistenceService.persistDoctorRejectedNotification(event);
        if (!persisted) {
            log.warn("[Event] In-app notification persistence failed for doctor rejection id={} — email still proceeds.",
                    event.getDoctorUserId());
        }

        sendEmailSafely("doctor", () -> emailService.sendDoctorRejected(
                event.getDoctorEmail(), event.getDoctorName(), event.getReason()), event.getDoctorUserId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TRIGGER 4 — Appointment booked
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentBooked(AppointmentBookedEvent event) {
        log.info("[Event] AppointmentBooked → id: {}, patient: {}, doctor: {}",
                event.getAppointmentId(), event.getPatientEmail(), event.getDoctorEmail());

        boolean persisted = notificationPersistenceService.persistBookedNotifications(event);
        if (!persisted) {
            log.warn("[Event] In-app notification persistence failed for booking id={} — emails still proceed.",
                    event.getAppointmentId());
        }

        String date = event.getAppointmentDate().toString();

        sendEmailSafely("patient", () -> emailService.sendAppointmentConfirmedPatient(
                        event.getPatientEmail(), event.getPatientName(), event.getDoctorName(), date, event.getTimeRange()),
                event.getAppointmentId());

        sendEmailSafely("doctor", () -> emailService.sendAppointmentConfirmedDoctor(
                        event.getDoctorEmail(), event.getDoctorName(), event.getPatientName(), date, event.getTimeRange()),
                event.getAppointmentId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TRIGGER 5 — Appointment cancelled
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        log.info("[Event] AppointmentCancelled → id: {}", event.getAppointmentId());

        boolean persisted = notificationPersistenceService.persistCancelledNotifications(event);
        if (!persisted) {
            log.warn("[Event] In-app notification persistence failed for cancellation id={} — emails still proceed.",
                    event.getAppointmentId());
        }

        String date = event.getAppointmentDate().toString();

        sendEmailSafely("patient", () -> emailService.sendAppointmentCancelledPatient(
                        event.getPatientEmail(), event.getPatientName(), event.getDoctorName(), date, event.getTimeRange()),
                event.getAppointmentId());

        sendEmailSafely("doctor", () -> emailService.sendAppointmentCancelledDoctor(
                        event.getDoctorEmail(), event.getDoctorName(), event.getPatientName(), date, event.getTimeRange()),
                event.getAppointmentId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TRIGGER 6 — Appointment completed
    //  (previously published by AppointmentService with NO listener at all —
    //  completions were silently un-notified until this was added)
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCompleted(AppointmentCompletedEvent event) {
        log.info("[Event] AppointmentCompleted → id: {}, patient: {}, doctor: {}",
                event.getAppointmentId(), event.getPatientEmail(), event.getDoctorEmail());

        boolean persisted = notificationPersistenceService.persistCompletedNotifications(event);
        if (!persisted) {
            log.warn("[Event] In-app notification persistence failed for completion id={} — emails still proceed.",
                    event.getAppointmentId());
        }

        String date = event.getAppointmentDate().toString();

        sendEmailSafely("patient", () -> emailService.sendAppointmentCompletedPatient(
                        event.getPatientEmail(), event.getPatientName(), event.getDoctorName(), date, event.getTimeRange()),
                event.getAppointmentId());

        sendEmailSafely("doctor", () -> emailService.sendAppointmentCompletedDoctor(
                        event.getDoctorEmail(), event.getDoctorName(), event.getPatientName(), date, event.getTimeRange()),
                event.getAppointmentId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TRIGGER 7 — Appointment reminder (fired by AppointmentService's 9 AM job)
    // ═══════════════════════════════════════════════════════════════════════

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentReminder(AppointmentReminderEvent event) {
        log.info("[Event] AppointmentReminder → id: {}", event.getAppointmentId());

        boolean persisted = notificationPersistenceService.persistReminderNotifications(event);
        if (!persisted) {
            log.warn("[Event] In-app notification persistence failed for reminder id={} — emails still proceed.",
                    event.getAppointmentId());
        }

        String date = event.getAppointmentDate().toString();

        sendEmailSafely("patient", () -> emailService.sendAppointmentReminderPatient(
                        event.getPatientEmail(), event.getPatientName(), event.getDoctorName(), date, event.getTimeRange()),
                event.getAppointmentId());

        sendEmailSafely("doctor", () -> emailService.sendAppointmentReminderDoctor(
                        event.getDoctorEmail(), event.getDoctorName(), event.getPatientName(), date, event.getTimeRange()),
                event.getAppointmentId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Inbox APIs — unaffected by the AFTER_COMMIT change. These read/update
    //  already-committed notification rows at request time; there's no
    //  publish-before-commit race to worry about here.
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(String email, Pageable pageable) {
        User user = findUser(email);
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId(), pageable).map(this::toResponse);
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
        return "Notification marked as read.";
    }

    @Transactional
    public String markAllAsRead(String email) {
        User user = findUser(email);
        notificationRepository.markAllAsRead(user.getId());
        return "All notifications marked as read.";
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(String email) {
        User user = findUser(email);
        long count = notificationRepository.countByUser_IdAndReadFalse(user.getId());
        return new UnreadCountResponse(count);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Catches WHATEVER ultimately escapes EmailService — including after
     * @Retryable's attempts are exhausted (MailException) — logs it, and
     * lets execution continue. This is the final safety net: by the time
     * we're here, the DB transaction already committed successfully, so an
     * email failure is purely a delivery problem, never a data-consistency
     * one. There's nothing to roll back and no HTTP caller waiting for a
     * response — just log and move on to the next recipient/trigger.
     */
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
