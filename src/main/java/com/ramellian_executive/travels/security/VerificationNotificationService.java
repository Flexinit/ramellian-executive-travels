package com.ramellian_executive.travels.security;

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
