package com.docspot.service;

import com.docspot.entity.Notification;
import com.docspot.event.*;
import com.docspot.repository.NotificationRepository;
import com.docspot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Owns every in-app notification DB write. Split out from NotificationService
 * deliberately — here's why.
 *
 * NotificationService's listeners are @TransactionalEventListener(phase =
 * AFTER_COMMIT), meaning by the time they run, the ORIGINAL transaction
 * (the one that booked the appointment, approved the doctor, etc.) has
 * already committed and closed. There's no ambient transaction left to
 * piggyback on — any DB write from here needs a fresh one of its own. That's
 * what every method below opens via its own @Transactional.
 *
 * If that write fails partway (e.g. saving the doctor's notification
 * succeeds but the patient's throws), we don't want to let the exception
 * escape upward — this runs on an async listener thread with no HTTP
 * request to return an error to, so an uncaught exception here would just
 * be a stack trace in a log with nothing useful to show for it, and could
 * interfere with the executor thread. Instead: catch it, mark the
 * transaction rollback-only (so the partial write is undone cleanly rather
 * than left half-committed), log it, and return false so the caller
 * (NotificationService) can decide what to do — currently just a warning
 * log, since email sending should still proceed regardless of whether the
 * in-app notification row was saved.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPersistenceService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // ─── Doctor lifecycle ───────────────────────────────────────────────────

    @Transactional
    public boolean persistDoctorRegisteredNotification(DoctorRegisteredEvent event) {
        try {
            // Only the doctor gets an in-app row — admin has no notification
            // inbox (NotificationController is DOCTOR/PATIENT only).
            persist(event.getDoctorUserId(), "Application Received",
                    "Your DocSpot application has been received and is under review by our admin team. "
                            + "You will be notified once a decision is made.");
            return true;
        } catch (Exception e) {
            return handleFailure("persistDoctorRegisteredNotification", event.getDoctorUserId(), e);
        }
    }

    @Transactional
    public boolean persistDoctorApprovedNotification(DoctorApprovedEvent event) {
        try {
            String noteText = (event.getAdminNote() != null && !event.getAdminNote().isBlank())
                    ? " Admin note: " + event.getAdminNote() : "";
            persist(event.getDoctorUserId(), "Application Approved ✓",
                    "Congratulations, Dr. " + event.getDoctorName() + "! Your DocSpot application has been "
                            + "approved. You can now log in and set your availability." + noteText);
            return true;
        } catch (Exception e) {
            return handleFailure("persistDoctorApprovedNotification", event.getDoctorUserId(), e);
        }
    }

    @Transactional
    public boolean persistDoctorRejectedNotification(DoctorRejectedEvent event) {
        try {
            persist(event.getDoctorUserId(), "Application Update",
                    "Your DocSpot application could not be approved at this time. Reason: " + event.getReason()
                            + " — Please contact support if you have questions.");
            return true;
        } catch (Exception e) {
            return handleFailure("persistDoctorRejectedNotification", event.getDoctorUserId(), e);
        }
    }

    // ─── Appointment lifecycle ────────────────────────────────────────────────
    // Each of these persists BOTH the patient's and the doctor's notification
    // in the SAME transaction — they succeed or roll back together as a unit,
    // rather than risking one party notified and the other silently not.

    @Transactional
    public boolean persistBookedNotifications(AppointmentBookedEvent event) {
        try {
            persist(event.getPatientUserId(), "Appointment Confirmed",
                    "Your appointment with Dr. " + event.getDoctorName() + " on " + event.getAppointmentDate()
                            + " at " + event.getTimeRange() + " is confirmed.");
            persist(event.getDoctorUserId(), "New Appointment Booked",
                    "A new appointment has been booked by " + event.getPatientName() + " on "
                            + event.getAppointmentDate() + " at " + event.getTimeRange() + ".");
            return true;
        } catch (Exception e) {
            return handleFailure("persistBookedNotifications", event.getAppointmentId(), e);
        }
    }

    @Transactional
    public boolean persistCancelledNotifications(AppointmentCancelledEvent event) {
        try {
            persist(event.getPatientUserId(), "Appointment Cancelled",
                    "Your appointment with Dr. " + event.getDoctorName() + " on " + event.getAppointmentDate()
                            + " at " + event.getTimeRange() + " has been cancelled.");
            persist(event.getDoctorUserId(), "Appointment Cancelled",
                    "The appointment with " + event.getPatientName() + " on " + event.getAppointmentDate()
                            + " at " + event.getTimeRange() + " has been cancelled.");
            return true;
        } catch (Exception e) {
            return handleFailure("persistCancelledNotifications", event.getAppointmentId(), e);
        }
    }

    @Transactional
    public boolean persistCompletedNotifications(AppointmentCompletedEvent event) {
        try {
            persist(event.getPatientUserId(), "Appointment Completed",
                    "Your appointment with Dr. " + event.getDoctorName() + " on " + event.getAppointmentDate()
                            + " at " + event.getTimeRange() + " is completed.");
            persist(event.getDoctorUserId(), "Appointment Completed",
                    "Your appointment with " + event.getPatientName() + " on " + event.getAppointmentDate()
                            + " at " + event.getTimeRange() + " has been marked completed.");
            return true;
        } catch (Exception e) {
            return handleFailure("persistCompletedNotifications", event.getAppointmentId(), e);
        }
    }

    @Transactional
    public boolean persistReminderNotifications(AppointmentReminderEvent event) {
        try {
            persist(event.getPatientUserId(), "Appointment Tomorrow",
                    "Reminder: Your appointment with Dr. " + event.getDoctorName() + " is tomorrow, "
                            + event.getAppointmentDate() + " at " + event.getTimeRange() + ". Please arrive on time.");
            persist(event.getDoctorUserId(), "Appointment Tomorrow",
                    "Reminder: You have an appointment with " + event.getPatientName() + " tomorrow, "
                            + event.getAppointmentDate() + " at " + event.getTimeRange() + ".");
            return true;
        } catch (Exception e) {
            return handleFailure("persistReminderNotifications", event.getAppointmentId(), e);
        }
    }

    // ─── Shared helpers ───────────────────────────────────────────────────────

    /**
     * Silently skips if the user can't be found — this can legitimately
     * happen (e.g. a soft-deleted account) and shouldn't be treated as a
     * hard failure worth rolling back the whole notification transaction
     * over; the other party's notification (if any) in the same call still
     * gets saved.
     */
    private void persist(Long userId, String title, String message) {
        if (userId == null) return;
        userRepository.findById(userId).ifPresent(user -> {
            Notification notification = Notification.builder()
                    .user(user).title(title).message(message).read(false).build();
            notificationRepository.save(notification);
            log.debug("In-app notification saved -> userId: {}, title: {}", userId, title);
        });
    }

    /**
     * Logs the failure, marks the CURRENT transaction rollback-only (so any
     * partial writes already made in this method are undone cleanly instead
     * of being committed half-done), and returns false WITHOUT rethrowing —
     * deliberately, since there's nothing upstream on an async listener
     * thread that would meaningfully handle a rethrown exception, and we
     * don't want @Transactional's default behavior of leaving the decision
     * to an exception type; setRollbackOnly() makes the rollback explicit
     * and intentional regardless of exception type.
     */
    private boolean handleFailure(String methodName, Long contextId, Exception e) {
        log.error("[NotificationPersistence] {} failed for id={}: {}", methodName, contextId, e.getMessage(), e);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return false;
    }
}
