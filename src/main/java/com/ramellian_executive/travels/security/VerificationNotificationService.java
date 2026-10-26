package com.ramellian_executive.travels.security;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.BookingContract;
import com.ramellian_executive.travels.entity.Disbursement;
import com.ramellian_executive.travels.entity.Payment;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserDocument;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.entity.VehiclePhoto;
import com.ramellian_executive.travels.repository.vehicle.VehiclePhotoRepository;
import com.ramellian_executive.travels.service.EmailTemplateService;
import com.ramellian_executive.travels.service.ReceiptService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VerificationNotificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationNotificationService.class);

    private final JavaMailSender mailSender;
    private final RestClient.Builder restClientBuilder;
    private final VehiclePhotoRepository vehiclePhotoRepository;
    private final EmailTemplateService templates;
    private final ReceiptService receiptService;

    @Value("${app.verification.from-email}")
    private String fromEmail;
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;
    @Value("${app.server.url:http://localhost:8080}")
    private String serverUrl;
    @Value("${app.whatsapp.enabled:false}")
    private boolean whatsappEnabled;
    @Value("${app.whatsapp.base-url}")
    private String whatsappBaseUrl;
    @Value("${app.whatsapp.phone-number-id}")
    private String whatsappPhoneNumberId;
    @Value("${app.whatsapp.access-token}")
    private String whatsappAccessToken;
    @Value("${app.whatsapp.template-name}")
    private String whatsappTemplateName;

    // ─── Verification code ────────────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendEmail(String recipient, String code, String purpose) {
        String name = EmailTemplateService.firstName(null);
        String html = templates.verificationCode(name, code, purpose);
        send(recipient, "Ramellian " + purpose + " code", html);
    }

    @Async("notificationTaskExecutor")
    public void sendPasswordResetEmail(String recipient, String code) {
        String html = templates.verificationCode(EmailTemplateService.firstName(null), code, "password reset");
        send(recipient, "Ramellian password reset code", html);
    }

    // ─── Booking — owner (new booking request) ───────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendBookingCreatedToOwner(Booking booking, double commissionRatePct) {
        if (booking == null || booking.getVehicle() == null) return;
        User owner = booking.getVehicle().getOwner();
        if (owner == null || blank(owner.getEmailAddress())) return;
        User renter = booking.getUser();
        if (renter == null) return;

        String vehicleTitle = EmailTemplateService.vehicleTitle(booking.getVehicle());
        String ownerName = owner.getFirstName() != null ? owner.getFirstName() : "Owner";
        String renterName = (renter.getFirstName() != null ? renter.getFirstName() : "")
                + (renter.getLastName() != null ? " " + renter.getLastName() : "");
        if (renterName.isBlank()) renterName = renter.getEmailAddress();

        String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                + "<h2 style='color:#1a1a2e'>New Booking Request — Action Required</h2>"
                + "<p>Hi " + ownerName + ",</p>"
                + "<p><strong>" + renterName.trim() + "</strong> has requested to book your vehicle "
                + "<strong>" + vehicleTitle + "</strong>.</p>"
                + "<p>Please log in to review the request and <strong>accept or decline</strong> the booking.</p>"
                + "<p style='margin:24px 0'>"
                + "<a href='{{CTA_URL}}' style='background:#1a1a2e;color:#fff;padding:12px 24px;"
                + "border-radius:6px;text-decoration:none;font-weight:bold'>Review Booking</a>"
                + "</p>"
                + "<p style='color:#666;font-size:12px'>Ramellian Executive Travels</p>"
                + "</div>";

        html = injectCtaUrl(html, frontendUrl + "/owner/bookings");
        send(owner.getEmailAddress(), "New Booking Request — " + vehicleTitle, html);
    }

    // ─── Booking — renter ─────────────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendBookingCreatedToRenter(Booking booking, List<String> warnings) {
        if (booking == null || booking.getUser() == null) return;
        User renter = booking.getUser();
        if (blank(renter.getEmailAddress())) return;

        String html = templates.bookingCreatedRenter(booking, vehiclePhotoUrl(booking.getVehicle()), warnings);
        html = injectCtaUrl(html, frontendUrl + "/bookings/" + booking.getId());
        send(renter.getEmailAddress(),
             "Booking Confirmed — " + EmailTemplateService.vehicleTitle(booking.getVehicle()), html);
    }

    @Async("notificationTaskExecutor")
    public void sendPickupReminder(Booking booking) {
        if (booking == null || booking.getUser() == null) return;
        User renter = booking.getUser();
        if (blank(renter.getEmailAddress())) return;

        String vehicleTitle = EmailTemplateService.vehicleTitle(booking.getVehicle());
        String pickupTime = booking.getStartTime() != null
                ? booking.getStartTime().toLocalDateTime().toString().replace("T", " at ").substring(0, 19)
                : "your scheduled time";
        String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                + "<h2 style='color:#1a1a2e'>Pickup Reminder — " + vehicleTitle + "</h2>"
                + "<p>Hi " + (renter.getFirstName() != null ? renter.getFirstName() : "there") + ",</p>"
                + "<p>This is a friendly reminder that your booking for <strong>" + vehicleTitle
                + "</strong> is due for pickup at <strong>" + pickupTime + "</strong>.</p>"
                + "<p>Please proceed to collect the vehicle so pickup can be recorded. "
                + "If you need to reach the owner, check your booking details.</p>"
                + "<p><a href='" + frontendUrl + "/bookings/" + booking.getId()
                + "' style='background:#1a1a2e;color:#fff;padding:10px 20px;border-radius:4px;"
                + "text-decoration:none;display:inline-block'>View Booking</a></p>"
                + "<p style='color:#666;font-size:12px'>Ramellian Executive Travels</p>"
                + "</div>";
        send(renter.getEmailAddress(), "Pickup Reminder — " + vehicleTitle, html);
    }

    @Async("notificationTaskExecutor")
    public void sendPickupReminderToOwner(Booking booking) {
        if (booking == null || booking.getVehicle() == null) return;
        User owner = booking.getVehicle().getOwner();
        if (owner == null || blank(owner.getEmailAddress())) return;

        String vehicleTitle = EmailTemplateService.vehicleTitle(booking.getVehicle());
        String renterName = booking.getUser() != null && booking.getUser().getFirstName() != null
                ? booking.getUser().getFirstName() : "A renter";
        String pickupTime = booking.getStartTime() != null
                ? booking.getStartTime().toLocalDateTime().toString().replace("T", " at ").substring(0, 19)
                : "the scheduled time";
        String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                + "<h2 style='color:#1a1a2e'>Pickup Reminder — " + vehicleTitle + "</h2>"
                + "<p>Hi " + (owner.getFirstName() != null ? owner.getFirstName() : "there") + ",</p>"
                + "<p><strong>" + renterName + "</strong> is scheduled to pick up <strong>" + vehicleTitle
                + "</strong> at <strong>" + pickupTime + "</strong>.</p>"
                + "<p>Please ensure the vehicle is ready and available for handover. "
                + "Once the renter collects the vehicle, record the pickup to activate the booking.</p>"
                + "<p><a href='" + frontendUrl + "/owner/bookings/" + booking.getId()
                + "' style='background:#1a1a2e;color:#fff;padding:10px 20px;border-radius:4px;"
                + "text-decoration:none;display:inline-block'>Record Pickup</a></p>"
                + "<p style='color:#666;font-size:12px'>Ramellian Executive Travels</p>"
                + "</div>";
        send(owner.getEmailAddress(), "Upcoming Pickup — " + vehicleTitle, html);
    }

    // ─── Booking — owner ──────────────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendBookingNotification(Booking booking, double commissionRatePct) {
        if (booking == null || booking.getVehicle() == null) return;
        User owner = booking.getVehicle().getOwner();
        if (owner == null || blank(owner.getEmailAddress())) return;

        String html = templates.bookingCreatedOwner(booking, vehiclePhotoUrl(booking.getVehicle()), commissionRatePct);
        html = injectCtaUrl(html, frontendUrl + "/owner/bookings");
        send(owner.getEmailAddress(),
             "New Booking on " + EmailTemplateService.vehicleTitle(booking.getVehicle()), html);
    }

    // ─── Booking — admin / super-admin ────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendBookingCreatedToAdmin(Booking booking, User admin) {
        if (booking == null || admin == null || blank(admin.getEmailAddress())) return;

        String html = templates.bookingCreatedAdmin(booking, vehiclePhotoUrl(booking.getVehicle()));
        html = injectCtaUrl(html, frontendUrl + "/admin/bookings/" + booking.getId());
        send(admin.getEmailAddress(),
             "New Booking — " + EmailTemplateService.vehicleTitle(booking.getVehicle()), html);
    }

    // ─── Document review ──────────────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendDocumentReviewNotification(UserDocument document) {
        if (document == null || document.getUser() == null) return;
        User user = document.getUser();
        if (blank(user.getEmailAddress())) return;

        String docType = document.getDocumentType() != null
                ? document.getDocumentType().name().replace("_", " ") : "Document";
        boolean approved = document.getReviewStatus() != null
                && document.getReviewStatus().name().equals("APPROVED");

        String html = templates.documentReview(document);
        html = injectCtaUrl(html, frontendUrl + "/profile/documents");
        send(user.getEmailAddress(),
             "Your " + docType + " has been " + (approved ? "Approved" : "Rejected") + " — Ramellian Executive Travels",
             html);
    }

    // ─── Vehicle review ───────────────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendVehicleReviewNotification(Vehicle vehicle) {
        if (vehicle == null || vehicle.getOwner() == null) return;
        User owner = vehicle.getOwner();
        if (blank(owner.getEmailAddress())) return;

        boolean approved = vehicle.getApprovalStatus() != null
                && "APPROVED".equalsIgnoreCase(vehicle.getApprovalStatus().name());

        String html = templates.vehicleReview(vehicle);
        html = injectCtaUrl(html, frontendUrl + "/owner/vehicles");
        send(owner.getEmailAddress(),
             "Your Vehicle " + EmailTemplateService.vehicleTitle(vehicle)
                     + " has been " + (approved ? "Approved" : "Rejected") + " — Ramellian Executive Travels",
             html);
    }

    // ─── WhatsApp ─────────────────────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendWhatsApp(String phoneNumber, String code) {
        if (!whatsappEnabled) {
            throw new IllegalStateException("WhatsApp delivery is not configured");
        }
        restClientBuilder.baseUrl(whatsappBaseUrl).build()
                .post()
                .uri("/v21.0/{phoneNumberId}/messages", whatsappPhoneNumberId)
                .header("Authorization", "Bearer " + whatsappAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "messaging_product", "whatsapp",
                        "to", phoneNumber,
                        "type", "template",
                        "template", Map.of(
                                "name", whatsappTemplateName,
                                "language", Map.of("code", "en_US"),
                                "components", List.of(Map.of(
                                        "type", "body",
                                        "parameters", List.of(Map.of(
                                                "type", "text", "text", code)))))))
                .retrieve()
                .toBodilessEntity();
    }

    // ─── Receipt ──────────────────────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendPaymentReceipt(Payment payment) {
        if (payment == null || payment.getBooking() == null) return;
        User renter = payment.getBooking().getUser();
        if (renter == null || blank(renter.getEmailAddress())) return;

        Path receiptPath = receiptService.receiptPath(payment);
        boolean hasPdf = receiptPath != null && receiptPath.toFile().exists();
        if (!hasPdf) log.warn("Receipt PDF not found for payment {} — attaching without file", payment.getId());

        String html = templates.paymentReceipt(payment, hasPdf);
        html = injectCtaUrl(html, frontendUrl + "/bookings/" + payment.getBooking().getId());

        String vehicleTitle = EmailTemplateService.vehicleTitle(payment.getBooking().getVehicle());
        String subject = "Payment Receipt — " + vehicleTitle;

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            if (!blank(fromEmail)) helper.setFrom(fromEmail.trim(), "Ramellian Executive Travels");
            helper.setTo(renter.getEmailAddress().trim());
            helper.setSubject(subject);
            helper.setText(html, true);
            if (receiptPath != null && receiptPath.toFile().exists()) {
                helper.addAttachment("Receipt-" + vehicleTitle.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf",
                        receiptPath.toFile());
            }
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send receipt email to {}: {}", renter.getEmailAddress(), e.getMessage(), e);
        }
    }

    // ─── Contract — signed copies ────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendFullySignedContractCopies(BookingContract contract, java.nio.file.Path contractDir) {
        if (contract == null || contract.getBooking() == null) return;
        String vehicleTitle = EmailTemplateService.vehicleTitle(contract.getBooking().getVehicle());
        String fileName = "Contract-" + vehicleTitle.replaceAll("[^a-zA-Z0-9]", "_") + "-FullySigned.pdf";

        User renter = contract.getBooking().getUser();
        if (renter != null && !blank(renter.getEmailAddress())) {
            String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                    + "<h2 style='color:#1a1a2e'>Fully Executed Contract — " + vehicleTitle + "</h2>"
                    + "<p>Hi " + (renter.getFirstName() != null ? renter.getFirstName() : "there") + ",</p>"
                    + "<p>Both parties have signed the rental agreement for <strong>" + vehicleTitle + "</strong>. "
                    + "The fully executed contract is attached.</p>"
                    + "<p><a href='" + frontendUrl + "/bookings/" + contract.getBooking().getId()
                    + "' style='background:#1a1a2e;color:#fff;padding:10px 20px;border-radius:4px;"
                    + "text-decoration:none;display:inline-block'>View Booking</a></p>"
                    + "<p style='color:#666;font-size:12px'>Ramellian Executive Travels</p>"
                    + "</div>";
            sendWithAttachment(renter.getEmailAddress(), "Fully Executed Contract — " + vehicleTitle,
                    html, contract.getSignedFileName(), contractDir, fileName);
        }

        User owner = contract.getBooking().getVehicle() != null
                ? contract.getBooking().getVehicle().getOwner() : null;
        if (owner != null && !blank(owner.getEmailAddress())) {
            String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                    + "<h2 style='color:#1a1a2e'>Fully Executed Contract — " + vehicleTitle + "</h2>"
                    + "<p>Hi " + (owner.getFirstName() != null ? owner.getFirstName() : "there") + ",</p>"
                    + "<p>Both parties have signed the rental agreement for <strong>" + vehicleTitle + "</strong>. "
                    + "The fully executed contract is attached for your records.</p>"
                    + "<p><a href='" + frontendUrl + "/owner/bookings/" + contract.getBooking().getId()
                    + "' style='background:#1a1a2e;color:#fff;padding:10px 20px;border-radius:4px;"
                    + "text-decoration:none;display:inline-block'>View Booking</a></p>"
                    + "<p style='color:#666;font-size:12px'>Ramellian Executive Travels</p>"
                    + "</div>";
            sendWithAttachment(owner.getEmailAddress(), "Fully Executed Contract — " + vehicleTitle,
                    html, contract.getSignedFileName(), contractDir, fileName);
        }
    }

    private void sendWithAttachment(String to, String subject, String html,
                                    String fileName, java.nio.file.Path dir, String attachmentName) {
        try {
            java.io.File file = fileName != null ? dir.resolve(fileName).toFile() : null;
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            if (!blank(fromEmail)) helper.setFrom(fromEmail.trim(), "Ramellian Executive Travels");
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(html, true);
            if (file != null && file.exists()) {
                helper.addAttachment(attachmentName, file);
            }
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send contract copy email to {}: {}", to, e.getMessage(), e);
        }
    }

    // ─── Contract — owner sign request ───────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendOwnerContractSignRequest(BookingContract contract) {
        if (contract == null || contract.getBooking() == null) return;
        User owner = contract.getBooking().getVehicle().getOwner();
        if (owner == null || blank(owner.getEmailAddress())) return;

        String vehicleTitle = EmailTemplateService.vehicleTitle(contract.getBooking().getVehicle());
        String ownerName = owner.getFirstName() != null ? owner.getFirstName() : "Owner";
        String renterName = contract.getBooking().getUser() != null
                && contract.getBooking().getUser().getFirstName() != null
                ? contract.getBooking().getUser().getFirstName() : "The renter";

        String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                + "<h2 style='color:#1a1a2e'>Action Required: Sign Rental Contract</h2>"
                + "<p>Hi " + ownerName + ",</p>"
                + "<p>" + renterName + " has signed the rental contract for <strong>"
                + vehicleTitle + "</strong>.</p>"
                + "<p>Please log in, review the contract, and add your signature to confirm the booking.</p>"
                + "<p style='margin:24px 0'>"
                + "<a href='{{CTA_URL}}' style='background:#1a1a2e;color:#fff;padding:12px 24px;"
                + "border-radius:6px;text-decoration:none;font-weight:bold'>Review &amp; Sign Contract</a>"
                + "</p>"
                + "<p style='color:#666;font-size:12px'>Ramellian Executive Travels</p>"
                + "</div>";

        html = injectCtaUrl(html, frontendUrl + "/owner/bookings/" + contract.getBooking().getId() + "/contract");
        send(owner.getEmailAddress(), "Sign Required: Contract for " + vehicleTitle, html);
    }

    // ─── Google account — set password ───────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendSetPasswordEmail(String email, String code) {
        if (blank(email)) return;
        String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                + "<h2 style='color:#1a1a2e'>Set a Password for Your Account</h2>"
                + "<p>Your account was originally created with Google Sign-In. You can now set a password so you can also log in with your email and password.</p>"
                + "<p>Use the code below to set your password. It expires shortly.</p>"
                + "<div style='font-size:32px;font-weight:bold;letter-spacing:8px;margin:24px 0;color:#1a1a2e'>" + code + "</div>"
                + "<p>If you didn't request this, you can safely ignore this email — your account remains accessible via Google Sign-In.</p>"
                + "<p style='color:#666;font-size:12px'>Ramellian Executive Travels</p>"
                + "</div>";
        send(email, "Set Your Password — Ramellian Executive Travels", html);
    }

    // ─── Contact form ────────────────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendContactFormToSupport(com.ramellian_executive.travels.model.ContactRequest request,
                                         String supportEmail) {
        String destination = (supportEmail != null && !supportEmail.isBlank()) ? supportEmail : fromEmail;
        if (blank(destination)) return;

        String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                + "<h2 style='color:#1a1a2e'>New Contact Form Message</h2>"
                + "<table style='border-collapse:collapse;width:100%'>"
                + row("Name", request.name())
                + row("Email", request.email())
                + row("Phone", request.phone() != null && !request.phone().isBlank() ? request.phone() : "—")
                + row("Subject", request.subject())
                + "</table>"
                + "<h3 style='margin-top:24px'>Message</h3>"
                + "<div style='background:#f5f5f5;padding:16px;border-radius:4px;white-space:pre-wrap'>"
                + escapeHtml(request.message())
                + "</div>"
                + "<p style='color:#666;font-size:12px;margin-top:24px'>Ramellian Executive Travels — Contact Form</p>"
                + "</div>";
        send(destination, "Contact Form: " + request.subject(), html);
    }

    @Async("notificationTaskExecutor")
    public void sendContactAutoReply(com.ramellian_executive.travels.model.ContactRequest request) {
        if (blank(request.email())) return;

        String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                + "<h2 style='color:#1a1a2e'>We've received your message</h2>"
                + "<p>Hi " + escapeHtml(request.name()) + ",</p>"
                + "<p>Thank you for reaching out. We've received your message and will get back to you as soon as possible, typically within 24 hours.</p>"
                + "<h3>Your message</h3>"
                + "<table style='border-collapse:collapse;width:100%'>"
                + row("Subject", request.subject())
                + "</table>"
                + "<div style='background:#f5f5f5;padding:16px;border-radius:4px;margin-top:12px;white-space:pre-wrap'>"
                + escapeHtml(request.message())
                + "</div>"
                + "<p style='margin-top:24px'>In the meantime, you can explore available vehicles on our platform.</p>"
                + "<p style='color:#666;font-size:12px;margin-top:24px'>Ramellian Executive Travels</p>"
                + "</div>";
        send(request.email(), "We received your message — " + request.subject(), html);
    }

    private static String row(String label, String value) {
        return "<tr><td style='padding:8px;border:1px solid #ddd;font-weight:bold;width:120px'>" + label + "</td>"
                + "<td style='padding:8px;border:1px solid #ddd'>" + escapeHtml(value) + "</td></tr>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ─── Disbursement ─────────────────────────────────────────────────────────

    @Async("notificationTaskExecutor")
    public void sendDisbursementPaid(Disbursement disbursement) {
        if (disbursement == null || blank(disbursement.getOwnerEmail())) return;

        Booking booking = disbursement.getPayment() != null ? disbursement.getPayment().getBooking() : null;
        String vehicleTitle = booking != null && booking.getVehicle() != null
                ? EmailTemplateService.vehicleTitle(booking.getVehicle()) : "your vehicle";
        String ownerName = disbursement.getOwnerName() != null ? disbursement.getOwnerName().trim() : "Owner";
        String amount = String.format("KES %.2f", disbursement.getAmount());
        String ref = disbursement.getReference() != null ? disbursement.getReference() : "—";

        String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                + "<h2 style='color:#1a1a2e'>Payout Processed</h2>"
                + "<p>Hi " + ownerName + ",</p>"
                + "<p>Your payout of <strong>" + amount + "</strong> for the rental of "
                + "<strong>" + vehicleTitle + "</strong> has been processed.</p>"
                + "<table style='border-collapse:collapse;width:100%'>"
                + "<tr><td style='padding:8px;border:1px solid #ddd'>Amount</td>"
                + "<td style='padding:8px;border:1px solid #ddd'><strong>" + amount + "</strong></td></tr>"
                + "<tr><td style='padding:8px;border:1px solid #ddd'>Reference</td>"
                + "<td style='padding:8px;border:1px solid #ddd'>" + ref + "</td></tr>"
                + (disbursement.getNotes() != null && !disbursement.getNotes().isBlank()
                        ? "<tr><td style='padding:8px;border:1px solid #ddd'>Notes</td>"
                          + "<td style='padding:8px;border:1px solid #ddd'>" + disbursement.getNotes() + "</td></tr>"
                        : "")
                + "</table>"
                + "<p style='color:#666;font-size:12px;margin-top:24px'>Ramellian Executive Travels</p>"
                + "</div>";

        send(disbursement.getOwnerEmail(), "Payout Processed — " + vehicleTitle, html);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private void send(String to, String subject, String html) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            if (!blank(fromEmail)) helper.setFrom(fromEmail.trim(), "Ramellian Executive Travels");
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }

    /** Resolves the {{CTA_URL}} placeholder injected by the layout. */
    private static String injectCtaUrl(String html, String url) {
        return html.replace("{{CTA_URL}}", url);
    }

    private String vehiclePhotoUrl(Vehicle vehicle) {
        if (vehicle == null || vehicle.getId() == null) return null;
        List<VehiclePhoto> photos = vehiclePhotoRepository.findByVehicleId(vehicle.getId());
        if (photos.isEmpty()) return null;
        return serverUrl + photos.get(0).getUrl();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
