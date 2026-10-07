package com.docspot.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * ── Why most methods here are NOT @Async anymore ──────────────────────────
 * Every appointment/doctor-lifecycle email is now triggered from
 * NotificationService's @TransactionalEventListener(phase = AFTER_COMMIT)
 * listeners, which are themselves @Async. Being async twice over adds a
 * pointless extra hop through the thread pool. So these methods are
 * synchronous — the retry backoff sleep happens on the listener's already-
 * off-request-thread, never blocking an HTTP response.
 *
 * sendOtpEmail is the one exception: it's called directly and synchronously
 * from AuthService.forgotPassword(), which is NOT behind an event listener,
 * so it keeps @Async to stay non-blocking for that request.
 *
 * ── Why @Retryable needs send() to stop swallowing exceptions ─────────────
 * The old version caught MessagingException/Exception inside send() and
 * just logged it — meaning a transient SMTP hiccup silently ate the email
 * with no retry. Now send() lets MailException (already unchecked, thrown
 * directly by JavaMailSender.send()) propagate naturally, and wraps the
 * checked MessagingException from MimeMessageHelper's constructor into
 * Spring's own unchecked MailPreparationException. Both are unchecked, so
 * no public method here needs a `throws` clause — which also matters
 * because NotificationService.sendEmailSafely wraps these calls in a
 * java.lang.Runnable, whose functional method can't declare checked
 * exceptions at all.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ─── Auth (still directly/synchronously called — keeps @Async) ────────────

    @Async
    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendOtpEmail(String to, String name, String otp) {
        String body = wrap("""
            <h2 style="margin:0 0 16px">Password Reset OTP</h2>
            <p>Hello %s,</p>
            <p>Use the OTP below. Valid for <strong>10 minutes</strong>.</p>
            <div style="font-size:38px;font-weight:700;letter-spacing:12px;color:#2563eb;padding:20px 0;text-align:center">%s</div>
            <p style="color:#6b7280">Do not share this OTP with anyone.</p>
            """.formatted(name, otp));
        send(to, "DocSpot — Your Password Reset OTP", body);
    }

    // ─── Doctor lifecycle (called from NotificationService's AFTER_COMMIT listeners) ──

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendDoctorApplicationReceived(String to, String doctorName) {
        String body = wrap("""
            <h2 style="margin:0 0 16px">Application Received</h2>
            <p>Dear Dr. %s,</p>
            <p>Your DocSpot application is under review. You'll be notified once a decision is made.</p>
            """.formatted(doctorName));
        send(to, "DocSpot — Application Received", body);
    }

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendAdminNewDoctorAlert(String adminEmail, String doctorName, String doctorEmail, String speciality) {
        String body = wrap("""
            <h2 style="margin:0 0 16px">New Doctor Application</h2>
            <p>A new doctor has applied and is awaiting your review.</p>
            %s
            """.formatted(detailTable(row("Name", doctorName), row("Email", doctorEmail), row("Speciality", speciality))));
        send(adminEmail, "DocSpot Admin — New Doctor Application Pending", body);
    }

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendDoctorApproved(String to, String doctorName, String adminNote) {
        String noteHtml = (adminNote != null && !adminNote.isBlank())
                ? "<p><strong>Note from admin:</strong> " + adminNote + "</p>" : "";
        String body = wrap("""
            <h2 style="margin:0 0 16px;color:#16a34a">Application Approved ✓</h2>
            <p>Dear Dr. %s, your DocSpot application has been <strong style="color:#16a34a">approved</strong>.</p>
            %s
            <p>Log in, set your availability, and start accepting appointments.</p>
            """.formatted(doctorName, noteHtml));
        send(to, "DocSpot — Your Application Has Been Approved", body);
    }

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendDoctorRejected(String to, String doctorName, String reason) {
        String body = wrap("""
            <h2 style="margin:0 0 16px">Application Update</h2>
            <p>Dear Dr. %s, we are unable to approve your application at this time.</p>
            <p><strong>Reason:</strong></p>
            <blockquote style="border-left:3px solid #e5e7eb;margin:8px 0;padding:10px 16px;color:#374151;background:#f9fafb">%s</blockquote>
            """.formatted(doctorName, reason));
        send(to, "DocSpot — Update on Your Application", body);
    }

    // ─── Appointment lifecycle (called from NotificationService's AFTER_COMMIT listeners) ──

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendAppointmentConfirmedPatient(String to, String patientName, String doctorName, String date, String timeRange) {
        send(to, "DocSpot — Appointment Confirmed", wrap(appointmentBlock("Appointment Confirmed", null,
                "Hello " + patientName + ",", "Your appointment has been successfully booked.",
                doctorName, date, timeRange, "You can cancel up to <strong>2 hours</strong> before your appointment.")));
    }

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendAppointmentConfirmedDoctor(String to, String doctorName, String patientName, String date, String timeRange) {
        send(to, "DocSpot — New Appointment Booked", wrap(appointmentBlock("New Appointment Booked", null,
                "Hello Dr. " + doctorName + ",", "A patient has booked an appointment with you.",
                patientName, date, timeRange, null)));
    }

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendAppointmentCancelledPatient(String to, String patientName, String doctorName, String date, String timeRange) {
        send(to, "DocSpot — Appointment Cancelled", wrap(appointmentBlock("Appointment Cancelled", "#dc2626",
                "Hello " + patientName + ",", "The following appointment has been cancelled.",
                doctorName, date, timeRange, "If you did not cancel this, please contact support.")));
    }

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendAppointmentCancelledDoctor(String to, String doctorName, String patientName, String date, String timeRange) {
        send(to, "DocSpot — Appointment Cancelled", wrap(appointmentBlock("Appointment Cancelled", "#dc2626",
                "Hello Dr. " + doctorName + ",", "A patient has cancelled their appointment.",
                patientName, date, timeRange, null)));
    }

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendAppointmentReminderPatient(String to, String patientName, String doctorName, String date, String timeRange) {
        send(to, "DocSpot — Appointment Reminder for Tomorrow", wrap(appointmentBlock("Reminder: Appointment Tomorrow", "#d97706",
                "Hello " + patientName + ",", "This is a reminder that you have an appointment <strong>tomorrow</strong>.",
                doctorName, date, timeRange, "Please arrive a few minutes early.")));
    }

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendAppointmentReminderDoctor(String to, String doctorName, String patientName, String date, String timeRange) {
        send(to, "DocSpot — Appointment Reminder for Tomorrow", wrap(appointmentBlock("Reminder: Appointment Tomorrow", "#d97706",
                "Hello Dr. " + doctorName + ",", "You have a patient appointment scheduled <strong>tomorrow</strong>.",
                patientName, date, timeRange, null)));
    }

    // New trigger — previously AppointmentCompletedEvent was published (by
    // AppointmentService.complete() and the midnight auto-complete job) but
    // had no listener at all, so completions were silently un-notified.
    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendAppointmentCompletedPatient(String to, String patientName, String doctorName, String date, String timeRange) {
        send(to, "DocSpot — Appointment Completed", wrap(appointmentBlock("Appointment Completed", "#16a34a",
                "Hello " + patientName + ",", "Your appointment has been marked as completed.",
                doctorName, date, timeRange, "We hope your visit went well. You can view your appointment history anytime.")));
    }

    @Retryable(retryFor = { MailException.class, MessagingException.class }, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendAppointmentCompletedDoctor(String to, String doctorName, String patientName, String date, String timeRange) {
        send(to, "DocSpot — Appointment Completed", wrap(appointmentBlock("Appointment Completed", "#16a34a",
                "Hello Dr. " + doctorName + ",", "An appointment has been marked as completed.",
                patientName, date, timeRange, null)));
    }

    // ─── Core send ────────────────────────────────────────────────────────────

    private void send(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail, "DocSpot");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg); // unchecked MailException flows straight through — retryable as-is
            log.info("Email sent -> {} | {}", to, subject);
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Wrap the checked construction failure as Spring's own unchecked
            // MailException subtype — keeps every public method's signature
            // throws-clause-free (required for the Runnable-based call sites
            // in NotificationService) while still being visible to @Retryable.
            throw new MailPreparationException("Failed to prepare email message to " + to, e);
        }
    }

    // ─── HTML helpers ─────────────────────────────────────────────────────────

    private String wrap(String content) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:540px;margin:0 auto;color:#111827;font-size:15px;line-height:1.6">
              <div style="background:#2563eb;padding:18px 24px;border-radius:8px 8px 0 0">
                <span style="color:#fff;font-size:20px;font-weight:700">DocSpot</span>
              </div>
              <div style="border:1px solid #e5e7eb;border-top:none;padding:28px 24px;border-radius:0 0 8px 8px;background:#fff">
                %s
                <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0 16px">
                <p style="color:#9ca3af;font-size:12px;margin:0">This is an automated message from DocSpot. Do not reply.</p>
              </div>
            </div>""".formatted(content);
    }

    private String appointmentBlock(String heading, String headingColor, String greeting, String intro,
                                    String with, String date, String timeRange, String footer) {
        String color = headingColor != null ? headingColor : "#111827";
        String footerHtml = footer != null ? "<p style='color:#6b7280'>" + footer + "</p>" : "";
        return """
            <h2 style="margin:0 0 16px;color:%s">%s</h2>
            <p>%s</p><p>%s</p>
            %s
            %s""".formatted(color, heading, greeting, intro,
                detailTable(row("Date", date), row("Time", timeRange), row("With", with)), footerHtml);
    }

    private String detailTable(String... rows) {
        return "<table style='border-collapse:collapse;width:100%%;margin:16px 0'>" + String.join("", rows) + "</table>";
    }

    private String row(String label, String value) {
        return """
            <tr style="border-bottom:1px solid #e5e7eb">
              <td style="padding:10px;color:#6b7280;width:110px">%s</td>
              <td style="padding:10px"><strong>%s</strong></td>
            </tr>""".formatted(label, value);
    }
}
