package com.ramellian_executive.travels.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Component
public class PesapalClient {
    private static final Logger log = LoggerFactory.getLogger(PesapalClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;
    private final String disbursementBaseUrl;
    private final String consumerKey;
    private final String consumerSecret;
    private final String callbackUrl;
    private final String notificationId;
    private final boolean disbursementMock;

    public PesapalClient(
            @Value("${app.payment.pesapal.base-url:https://pay.pesapal.com/v3}") String baseUrl,
            @Value("${app.payment.pesapal.consumer-key:}") String consumerKey,
            @Value("${app.payment.pesapal.consumer-secret:}") String consumerSecret,
            @Value("${app.payment.pesapal.callback-url:}") String callbackUrl,
            @Value("${app.payment.pesapal.notification-id:}") String notificationId,
            @Value("${app.payment.pesapal.disbursement-mock:false}") boolean disbursementMock,
            @Value("${app.payment.pesapal.disbursement-base-url:https://pay.pesapal.com/v3}") String disbursementBaseUrl) {
        this.baseUrl = baseUrl;
        this.disbursementBaseUrl = disbursementBaseUrl;
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.callbackUrl = callbackUrl;
        this.notificationId = notificationId;
        this.disbursementMock = disbursementMock;
    }

    public PesapalOrder submitOrder(String merchantReference, double amount,
                                    String email, String phone, String firstName,
                                    String lastName, String description, String bookingId) {
        ensureConfigured();
        String token = fetchToken();
        String resolvedCallbackUrl = callbackUrl + (callbackUrl.contains("?") ? "&" : "?")
                + "bookingId=" + bookingId;
        Map<String, Object> body = Map.of(
                "id", merchantReference, "currency", "KES",
                "amount", amount, "description", description,
                "callback_url", resolvedCallbackUrl, "notification_id", notificationId,
                "billing_address", Map.of(
                        "email_address", email,
                        "phone_number", phone == null ? "" : phone,
                        "first_name", firstName == null ? "" : firstName,
                        "last_name", lastName == null ? "" : lastName));
        Map<String, Object> order = post(baseUrl + "/api/Transactions/SubmitOrderRequest", token, body);
        return new PesapalOrder(required(order, "order_tracking_id"),
                required(order, "redirect_url"));
    }

    public PesapalDisbursementBatch disburse(String uniqueId, String msisdn, String name,
                                              double amount, String narration) {
        if (disbursementMock) {
            String mockBatch = "MOCK-" + uniqueId;
            log.warn("Disbursement mock enabled — skipping Pesapal API call. Fake batch_number: {}", mockBatch);
            return new PesapalDisbursementBatch(mockBatch);
        }
        ensureConfigured();
        String token = fetchToken();
        Map<String, Object> body = Map.of(
                "transactions", List.of(Map.of(
                        "unique_id", uniqueId,
                        "msisdn", msisdn,
                        "name", name,
                        "amount", amount,
                        "narration", narration)));
        Map<String, Object> resp = post(disbursementBaseUrl + "/api/Disbursements/RequestDisbursement", token, body);
        return new PesapalDisbursementBatch(required(resp, "batch_number"));
    }

    public PesapalDisbursementStatus getDisbursementStatus(String batchNumber) {
        ensureConfigured();
        String token = fetchToken();
        Map<String, Object> resp = get(
                disbursementBaseUrl + "/api/Disbursements/GetDisbursementStatus?batchNumber=" + batchNumber, token);
        String status = String.valueOf(resp.getOrDefault("status", "UNKNOWN")).trim().toUpperCase();
        return new PesapalDisbursementStatus(batchNumber, status);
    }

    public PesapalRefundResult refund(String orderTrackingId, double amount, String reason) {
        ensureConfigured();
        String token = fetchToken();
        Map<String, Object> body = Map.of(
                "order_tracking_id", orderTrackingId,
                "amount", amount,
                "remarks", reason == null ? "" : reason);
        Map<String, Object> resp = post(baseUrl + "/api/Transactions/RefundRequest", token, body);
        String refundTrackingId = String.valueOf(resp.getOrDefault("refund_tracking_id",
                resp.getOrDefault("order_tracking_id", ""))).trim();
        String status = String.valueOf(resp.getOrDefault("status", "")).trim();
        return new PesapalRefundResult(refundTrackingId.isBlank() ? orderTrackingId : refundTrackingId, status);
    }

    public PesapalTransaction status(String trackingId) {
        ensureConfigured();
        String token = fetchToken();
        Map<String, Object> transaction = get(
                baseUrl + "/api/Transactions/GetTransactionStatus?orderTrackingId=" + trackingId, token);
        int statusCode = transaction.get("status_code") instanceof Number n ? n.intValue() : -1;
        String desc = String.valueOf(transaction.getOrDefault("payment_status_description", ""));
        String receipt = String.valueOf(transaction.getOrDefault("confirmation_code", ""));
        return new PesapalTransaction(statusCode, desc, receipt);
    }

    private String fetchToken() {
        Map<String, Object> resp = post(baseUrl + "/api/Auth/RequestToken", null,
                Map.of("consumer_key", consumerKey, "consumer_secret", consumerSecret));
        return required(resp, "token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, String token, Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (token != null) req.header("Authorization", "Bearer " + token);
            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            log.info("Pesapal POST {} → status={} body={}", url, resp.statusCode(), resp.body());
            return MAPPER.readValue(resp.body(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Pesapal POST failed: " + url + " — " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String url, String token) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return MAPPER.readValue(resp.body(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Pesapal GET failed: " + url + " — " + e.getMessage(), e);
        }
    }

    private void ensureConfigured() {
        if (consumerKey.isBlank() || consumerSecret.isBlank() || callbackUrl.isBlank()
                || notificationId.isBlank()) {
            throw new IllegalStateException("Pesapal payment configuration is incomplete");
        }
    }

    private String required(Map<String, Object> map, String field) {
        Object value = map == null ? null : map.get(field);
        String str = value == null ? "" : value.toString().trim();
        if (str.isBlank()) throw new IllegalStateException(
                "Pesapal response missing " + field + ". Full response: " + map);
        return str;
    }

    public record PesapalOrder(String trackingId, String iframeUrl) {}
    public record PesapalTransaction(int statusCode, String description, String receipt) {}
    public record PesapalDisbursementBatch(String batchNumber) {}
    public record PesapalDisbursementStatus(String batchNumber, String status) {}
    public record PesapalRefundResult(String refundTrackingId, String status) {}
}
