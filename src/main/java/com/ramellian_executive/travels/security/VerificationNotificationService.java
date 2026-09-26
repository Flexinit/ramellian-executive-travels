package com.ramellian_executive.travels.security;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.DocumentReviewStatus;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserDocument;
import com.ramellian_executive.travels.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class VerificationNotificationService {

    private final JavaMailSender mailSender;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.verification.from-email}")
    private String fromEmail;
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

    @Async("notificationTaskExecutor")
    public void sendEmail(String recipient, String code, String purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail.trim());
        }
        message.setTo(recipient);
        message.setSubject("Ramellian " + purpose + " code");
        message.setText("Your verification code is " + code + ". It expires soon and can only be used once.");
        mailSender.send(message);
    }

    @Async("notificationTaskExecutor")
    public void sendBookingNotification(Booking booking) {
        if (booking == null || booking.getVehicle() == null) {
            return;
        }
        Vehicle vehicle = booking.getVehicle();
        User owner = vehicle.getOwner();
        if (owner == null || owner.getEmailAddress() == null || owner.getEmailAddress().isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail.trim());
        }
        message.setTo(owner.getEmailAddress().trim());
        String vehicleTitle = (vehicle.getModel() != null ? vehicle.getModel() : "Vehicle")
                + (vehicle.getRegistrationNumber() != null && !vehicle.getRegistrationNumber().isBlank()
                ? " (" + vehicle.getRegistrationNumber() + ")" : "");
        message.setSubject("New Booking Notification for " + vehicleTitle);

        String ownerName = owner.getFirstName() != null && !owner.getFirstName().isBlank() ? owner.getFirstName() : "Vehicle Owner";
        String renterName = "A customer";
        if (booking.getUser() != null) {
            String fName = booking.getUser().getFirstName() != null ? booking.getUser().getFirstName() : "";
            String lName = booking.getUser().getLastName() != null ? booking.getUser().getLastName() : "";
            String fullName = (fName + " " + lName).trim();
            if (!fullName.isBlank()) {
                renterName = fullName;
            } else if (booking.getUser().getEmailAddress() != null) {
                renterName = booking.getUser().getEmailAddress();
            }
        }

        StringBuilder text = new StringBuilder();
        text.append("Hello ").append(ownerName).append(",\n\n");
        text.append("Your vehicle ").append(vehicleTitle).append(" has been booked by ").append(renterName).append(".\n\n");
        text.append("Booking Details:\n");
        if (booking.getId() != null) {
            text.append("- Booking ID: ").append(booking.getId()).append("\n");
        }
        if (booking.getStartTime() != null) {
            text.append("- Start Time: ").append(booking.getStartTime()).append("\n");
        }
        if (booking.getEndTime() != null) {
            text.append("- End Time: ").append(booking.getEndTime()).append("\n");
        }
        if (booking.getBookingMode() != null) {
            text.append("- Booking Mode: ").append(booking.getBookingMode()).append("\n");
        }
        if (booking.getTripType() != null) {
            text.append("- Trip Type: ").append(booking.getTripType()).append("\n");
        }
        if (booking.getRenterNotes() != null && !booking.getRenterNotes().isBlank()) {
            text.append("- Renter Notes: ").append(booking.getRenterNotes()).append("\n");
        }
        text.append("\nPlease log in to your account to review the booking and take necessary action.\n\n");
        text.append("Best regards,\nRamellian Executive Travels Team");

        message.setText(text.toString());
        mailSender.send(message);
    }

    @Async("notificationTaskExecutor")
    public void sendDocumentReviewNotification(UserDocument document) {
        if (document == null || document.getUser() == null) {
            return;
        }
        User user = document.getUser();
        if (user.getEmailAddress() == null || user.getEmailAddress().isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail.trim());
        }
        message.setTo(user.getEmailAddress().trim());
        String statusStr = document.getReviewStatus() == DocumentReviewStatus.APPROVED ? "Approved" : "Rejected";
        String docType = document.getDocumentType() != null ? document.getDocumentType().name() : "Document";
        message.setSubject("Your Document has been " + statusStr + " - Ramellian Executive Travels");

        String recipientName = user.getFirstName() != null && !user.getFirstName().isBlank()
                ? user.getFirstName()
                : "Valued Customer";

        StringBuilder text = new StringBuilder();
        text.append("Hello ").append(recipientName).append(",\n\n");
        text.append("Your submitted document of type '").append(docType).append("' has been reviewed by our administration team.\n\n");
        text.append("Status: ").append(statusStr).append("\n");
        if (document.getReviewNotes() != null && !document.getReviewNotes().isBlank()) {
            text.append("Review Notes: ").append(document.getReviewNotes()).append("\n");
        }
        if (document.getReviewStatus() == DocumentReviewStatus.REJECTED) {
            text.append("\nPlease review any notes provided and upload a valid replacement document through your account portal.\n");
        }
        text.append("\nBest regards,\nRamellian Executive Travels Team");

        message.setText(text.toString());
        mailSender.send(message);
    }

    @Async("notificationTaskExecutor")
    public void sendVehicleReviewNotification(Vehicle vehicle) {
        if (vehicle == null || vehicle.getOwner() == null) {
            return;
        }
        User owner = vehicle.getOwner();
        if (owner.getEmailAddress() == null || owner.getEmailAddress().isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail.trim());
        }
        message.setTo(owner.getEmailAddress().trim());
        String statusStr = vehicle.getApprovalStatus() != null ? vehicle.getApprovalStatus().name() : "REVIEWED";
        String displayStatus = statusStr.equalsIgnoreCase("APPROVED") ? "Approved" : "Rejected";
        String vehicleTitle = (vehicle.getModel() != null ? vehicle.getModel() : "Vehicle")
                + (vehicle.getRegistrationNumber() != null && !vehicle.getRegistrationNumber().isBlank()
                ? " (" + vehicle.getRegistrationNumber() + ")" : "");
        message.setSubject("Your Vehicle " + vehicleTitle + " has been " + displayStatus + " - Ramellian Executive Travels");

        String recipientName = owner.getFirstName() != null && !owner.getFirstName().isBlank()
                ? owner.getFirstName()
                : "Vehicle Owner";

        StringBuilder text = new StringBuilder();
        text.append("Hello ").append(recipientName).append(",\n\n");
        text.append("Your vehicle ").append(vehicleTitle).append(" has been reviewed by our administration team.\n\n");
        text.append("Approval Status: ").append(displayStatus).append("\n");
        if (vehicle.getApprovalNotes() != null && !vehicle.getApprovalNotes().isBlank()) {
            text.append("Review Notes: ").append(vehicle.getApprovalNotes()).append("\n");
        }
        if ("APPROVED".equalsIgnoreCase(statusStr)) {
            text.append("\nYour vehicle is now approved and eligible for customer listings and bookings.\n");
        } else {
            text.append("\nPlease review any admin notes and update your vehicle details or submit any necessary documents.\n");
        }
        text.append("\nBest regards,\nRamellian Executive Travels Team");

        message.setText(text.toString());
        mailSender.send(message);
    }

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
                                "components", java.util.List.of(Map.of(
                                        "type", "body",
                                        "parameters", java.util.List.of(Map.of(
                                                "type", "text", "text", code)))))))
                .retrieve()
                .toBodilessEntity();
    }
}
