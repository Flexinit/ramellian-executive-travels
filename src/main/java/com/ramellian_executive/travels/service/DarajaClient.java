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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class DarajaClient {

    private static final Logger log = LoggerFactory.getLogger(DarajaClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;
    private final String consumerKey;
    private final String consumerSecret;
    private final String shortcode;
    private final String passkey;
    private final String initiatorName;
    private final String securityCredential;
    private final String stkCallbackUrl;
    private final String b2cResultUrl;
    private final String b2cQueueTimeoutUrl;

    public DarajaClient(
            @Value("${app.daraja.base-url:https://sandbox.safaricom.co.ke}") String baseUrl,
            @Value("${app.daraja.consumer-key:}") String consumerKey,
            @Value("${app.daraja.consumer-secret:}") String consumerSecret,
            @Value("${app.daraja.shortcode:174379}") String shortcode,
            @Value("${app.daraja.passkey:}") String passkey,
            @Value("${app.daraja.initiator-name:}") String initiatorName,
            @Value("${app.daraja.security-credential:}") String securityCredential,
            @Value("${app.daraja.stk-callback-url:}") String stkCallbackUrl,
            @Value("${app.daraja.b2c-result-url:}") String b2cResultUrl,
            @Value("${app.daraja.b2c-queue-timeout-url:}") String b2cQueueTimeoutUrl) {
        this.baseUrl = baseUrl;
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.shortcode = shortcode;
        this.passkey = passkey;
        this.initiatorName = initiatorName;
        this.securityCredential = securityCredential;
        this.stkCallbackUrl = stkCallbackUrl;
        this.b2cResultUrl = b2cResultUrl;
        this.b2cQueueTimeoutUrl = b2cQueueTimeoutUrl;
    }

    public boolean isConfigured() {
        return !consumerKey.isBlank() && !consumerSecret.isBlank() && !passkey.isBlank();
    }

    /** Initiates an STK Push prompt on the customer's phone. Returns the CheckoutRequestID. */
    public StkPushResult stkPush(String phone, double amount, String accountRef, String description) {
        ensurePayinConfigured();
        String token = fetchToken();
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);
        String password = Base64.getEncoder().encodeToString(
                (shortcode + passkey + timestamp).getBytes());

        String normalizedPhone = normalizePhone(phone);
        Map<String, Object> body = Map.ofEntries(
                Map.entry("BusinessShortCode", shortcode),
                Map.entry("Password", password),
                Map.entry("Timestamp", timestamp),
                Map.entry("TransactionType", "CustomerPayBillOnline"),
                Map.entry("Amount", (long) Math.ceil(amount)),
                Map.entry("PartyA", normalizedPhone),
                Map.entry("PartyB", shortcode),
                Map.entry("PhoneNumber", normalizedPhone),
                Map.entry("CallBackURL", stkCallbackUrl),
                Map.entry("AccountReference", accountRef),
                Map.entry("TransactionDesc", description == null ? "Payment" : description));

        Map<String, Object> resp = post(baseUrl + "/mpesa/stkpush/v1/processrequest", token, body);
        String responseCode = String.valueOf(resp.getOrDefault("ResponseCode", "1"));
        if (!"0".equals(responseCode)) {
            throw new IllegalStateException("STK Push failed: " + resp);
        }
        return new StkPushResult(
                String.valueOf(resp.get("CheckoutRequestID")),
                String.valueOf(resp.get("MerchantRequestID")));
    }

    /** Queries STK Push transaction status. */
    public StkQueryResult queryStkStatus(String checkoutRequestId) {
        ensurePayinConfigured();
        String token = fetchToken();
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);
        String password = Base64.getEncoder().encodeToString(
                (shortcode + passkey + timestamp).getBytes());

        Map<String, Object> body = Map.of(
                "BusinessShortCode", shortcode,
                "Password", password,
                "Timestamp", timestamp,
                "CheckoutRequestID", checkoutRequestId);

        Map<String, Object> resp = post(baseUrl + "/mpesa/stkpushquery/v1/query", token, body);
        String resultCode = String.valueOf(resp.getOrDefault("ResultCode", "-1"));
        String resultDesc = String.valueOf(resp.getOrDefault("ResultDesc", ""));
        return new StkQueryResult(resultCode, resultDesc);
    }

    /** Sends M-Pesa B2C payout to owner's phone. Returns OriginatorConversationID. */
    public B2cResult b2c(String originatorId, String phone, String name, double amount, String narration) {
        ensureB2cConfigured();
        String token = fetchToken();
        Map<String, Object> body = Map.of(
                "OriginatorConversationID", originatorId,
                "InitiatorName", initiatorName,
                "SecurityCredential", securityCredential,
                "CommandID", "BusinessPayment",
                "Amount", (long) Math.floor(amount),
                "PartyA", shortcode,
                "PartyB", normalizePhone(phone),
                "Remarks", narration == null ? "Payout" : narration,
                "QueueTimeOutURL", b2cQueueTimeoutUrl,
                "ResultURL", b2cResultUrl);

        Map<String, Object> resp = post(baseUrl + "/mpesa/b2c/v3/paymentrequest", token, body);
        String responseCode = String.valueOf(resp.getOrDefault("ResponseCode", "1"));
        if (!"0".equals(responseCode)) {
            throw new IllegalStateException("Daraja B2C failed: " + resp);
        }
        return new B2cResult(
                String.valueOf(resp.getOrDefault("OriginatorConversationID", originatorId)),
                String.valueOf(resp.getOrDefault("ConversationID", "")));
    }

    private String fetchToken() {
        try {
            String credentials = Base64.getEncoder().encodeToString(
                    (consumerKey + ":" + consumerSecret).getBytes());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/oauth/v1/generate?grant_type=client_credentials"))
                    .header("Authorization", "Basic " + credentials)
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = MAPPER.readValue(resp.body(), Map.class);
            String token = String.valueOf(body.get("access_token"));
            if (token.isBlank()) throw new IllegalStateException("Empty token in Daraja response: " + resp.body());
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("Daraja OAuth failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, String token, Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Daraja POST {} → status={} body={}", url, resp.statusCode(), resp.body());
            return MAPPER.readValue(resp.body(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Daraja POST failed: " + url + " — " + e.getMessage(), e);
        }
    }

    /** Extracts a value by name from Daraja's ResultParameters list. */
    @SuppressWarnings("unchecked")
    public static String extractResultParam(List<Map<String, Object>> params, String key) {
        if (params == null) return null;
        return params.stream()
                .filter(p -> key.equals(p.get("Key")))
                .map(p -> p.get("Value"))
                .filter(v -> v != null)
                .map(Object::toString)
                .findFirst().orElse(null);
    }

    private void ensurePayinConfigured() {
        if (consumerKey.isBlank() || consumerSecret.isBlank() || passkey.isBlank()
                || stkCallbackUrl.isBlank()) {
            throw new IllegalStateException("Daraja STK Push configuration is incomplete");
        }
    }

    private void ensureB2cConfigured() {
        if (consumerKey.isBlank() || consumerSecret.isBlank()
                || initiatorName.isBlank() || securityCredential.isBlank()
                || b2cResultUrl.isBlank() || b2cQueueTimeoutUrl.isBlank()) {
            throw new IllegalStateException("Daraja B2C configuration is incomplete");
        }
    }

    /** Normalizes phone to 254XXXXXXXXX format. */
    public static String normalizePhone(String phone) {
        if (phone == null) throw new IllegalArgumentException("Phone number is required");
        phone = phone.trim().replaceAll("\\s+", "");
        if (phone.startsWith("+")) phone = phone.substring(1);
        if (phone.startsWith("0")) phone = "254" + phone.substring(1);
        return phone;
    }

    public record StkPushResult(String checkoutRequestId, String merchantRequestId) {}
    public record StkQueryResult(String resultCode, String resultDesc) {}
    public record B2cResult(String originatorConversationId, String conversationId) {}
}
