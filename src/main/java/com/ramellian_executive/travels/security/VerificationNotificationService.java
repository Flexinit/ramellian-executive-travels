package com.ramellian_executive.travels.security;

import com.ramellian_executive.travels.entity.Booking;
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

        String html = templates.paymentReceipt(payment);
        html = injectCtaUrl(html, frontendUrl + "/bookings/" + payment.getBooking().getId());

        Path receiptPath = receiptService.receiptPath(payment);
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
