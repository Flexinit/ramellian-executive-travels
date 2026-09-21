package com.ramellian_executive.travels.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class PesapalClient {
    private final RestClient client;
    private final String consumerKey;
    private final String consumerSecret;
    private final String callbackUrl;
    private final String notificationId;

    public PesapalClient(RestClient.Builder builder,
                         @Value("${app.payment.pesapal.base-url:https://pay.pesapal.com/v3}") String baseUrl,
                         @Value("${app.payment.pesapal.consumer-key:}") String consumerKey,
                         @Value("${app.payment.pesapal.consumer-secret:}") String consumerSecret,
                         @Value("${app.payment.pesapal.callback-url:}") String callbackUrl,
                         @Value("${app.payment.pesapal.notification-id:}") String notificationId) {
        this.client = builder.baseUrl(baseUrl).build();
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.callbackUrl = callbackUrl;
        this.notificationId = notificationId;
    }

    public PesapalOrder submitOrder(String merchantReference, double amount,
                                    String email, String phone, String firstName,
                                    String lastName, String description) {
        ensureConfigured();
        JsonNode response = client.post().uri("/api/Auth/RequestToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("consumer_key", consumerKey, "consumer_secret", consumerSecret))
                .retrieve().body(JsonNode.class);
        String token = required(response, "token");
        Map<String, Object> billing = Map.of(
                "email_address", email,
                "phone_number", phone == null ? "" : phone,
                "first_name", firstName == null ? "" : firstName,
                "last_name", lastName == null ? "" : lastName);
        JsonNode order = client.post().uri("/api/Transactions/SubmitOrderRequest")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("id", merchantReference, "currency", "KES",
                        "amount", amount, "description", description,
                        "callback_url", callbackUrl, "notification_id", notificationId,
                        "billing_address", billing))
                .retrieve().body(JsonNode.class);
        return new PesapalOrder(required(order, "order_tracking_id"),
                required(order, "redirect_url"));
    }

    public PesapalTransaction status(String trackingId) {
        ensureConfigured();
        JsonNode response = client.post().uri("/api/Auth/RequestToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("consumer_key", consumerKey, "consumer_secret", consumerSecret))
                .retrieve().body(JsonNode.class);
        String token = required(response, "token");
        JsonNode transaction = client.get()
                .uri(uri -> uri.path("/api/Transactions/GetTransactionStatus")
                        .queryParam("orderTrackingId", trackingId).build())
                .header("Authorization", "Bearer " + token)
                .retrieve().body(JsonNode.class);
        return new PesapalTransaction(
                transaction.path("status_code").asInt(-1),
                transaction.path("payment_status_description").asText(""),
                transaction.path("confirmation_code").asText(""));
    }

    private void ensureConfigured() {
        if (consumerKey.isBlank() || consumerSecret.isBlank() || callbackUrl.isBlank()
                || notificationId.isBlank()) {
            throw new IllegalStateException("Pesapal payment configuration is incomplete");
        }
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalStateException("Pesapal response missing " + field);
        return value;
    }

    public record PesapalOrder(String trackingId, String redirectUrl) {}
    public record PesapalTransaction(int statusCode, String description, String receipt) {}
}
