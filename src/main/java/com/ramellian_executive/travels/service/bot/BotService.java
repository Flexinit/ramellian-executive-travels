package com.ramellian_executive.travels.service.bot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramellian_executive.travels.entity.BotSession;
import com.ramellian_executive.travels.entity.Payment;
import com.ramellian_executive.travels.event.PaymentConfirmedEvent;
import com.ramellian_executive.travels.repository.BotSessionRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.service.ReceiptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);
    private static final int MAX_TOOL_LOOPS = 6;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a z");

    private final GroqClient groqClient;
    private final BotTools botTools;
    private final BotSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ReceiptService receiptService;
    private final BaileysBotClient baileysClient;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.email.currency:KES}")
    private String currency;

    public BotService(GroqClient groqClient, BotTools botTools,
                      BotSessionRepository sessionRepository,
                      UserRepository userRepository, ReceiptService receiptService,
                      BaileysBotClient baileysClient, ObjectMapper objectMapper) {
        this.groqClient = groqClient;
        this.botTools = botTools;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.receiptService = receiptService;
        this.baileysClient = baileysClient;
        this.objectMapper = objectMapper;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public record BotResponse(String text, List<BotMedia> media) {}

    @Transactional
    public BotResponse processMessage(String channelId, String channel, String incomingText) {
        BotSession session = loadOrCreate(channelId, channel);

        if (session.getUserEmail() == null) {
            if ("WHATSAPP".equals(channel)) {
                tryLinkByPhone(session, channelId);
            } else if ("INAPP".equals(channel)) {
                // channelId is the authenticated user's email
                userRepository.findByEmailAddressIgnoreCase(channelId).ifPresent(u -> {
                    session.setUserEmail(u.getEmailAddress());
                    session.setUserId(u.getId());
                });
            }
        }

        if (session.getUserEmail() == null) {
            return handleUnlinked(session, incomingText);
        }

        return runLlmLoop(session, incomingText);
    }

    // ── Event listener: send receipt + signing link to WhatsApp after payment ─

    @Async("notificationTaskExecutor")
    @EventListener
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        Payment payment = event.payment();
        if (payment.getBooking() == null || payment.getBooking().getUser() == null) return;

        String phone = payment.getBooking().getUser().getPhoneNumber();
        if (phone == null || phone.isBlank()) return;

        // Remove country code prefix formatting for Baileys (expects digits only, no +)
        String rawPhone = phone.replaceAll("[^0-9]", "");
        if (rawPhone.startsWith("0")) rawPhone = "254" + rawPhone.substring(1);

        // Send receipt PDF
        var receiptPath = receiptService.receiptPath(payment);
        if (receiptPath != null && receiptPath.toFile().exists()) {
            baileysClient.sendDocument(rawPhone, receiptPath.toString(),
                    "Receipt-" + payment.getBooking().getVehicle().getModel() + ".pdf");
        }

        // Send confirmation + signing link
        String signingLink = frontendUrl + "/contracts/" + session(rawPhone, payment) + "/sign";
        baileysClient.sendText(rawPhone,
                "✅ Payment confirmed! Your receipt is attached.\n\n" +
                "Please sign your rental agreement to complete the process:\n" +
                signingLink);
    }

    // ── LLM loop ───────────────────────────────────────────────────────────────

    private BotResponse runLlmLoop(BotSession session, String userText) {
        List<Map<String, Object>> messages = loadMessages(session);
        messages.add(GroqClient.userMsg(userText));

        List<BotMedia> allMedia = new ArrayList<>();
        String finalText = null;
        int loops = 0;

        while (loops++ < MAX_TOOL_LOOPS) {
            List<Map<String, Object>> withSystem = new ArrayList<>();
            withSystem.add(GroqClient.systemMsg(systemPrompt()));
            withSystem.addAll(messages);

            GroqClient.GroqChoice choice;
            try {
                choice = groqClient.complete(withSystem, botTools.definitions());
            } catch (Exception e) {
                log.error("Groq error: {}", e.getMessage());
                return new BotResponse("I'm having trouble right now. Please try again in a moment.", List.of());
            }

            var msg = choice.message();

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                messages.add(GroqClient.assistantToolCallMsg(msg.toolCalls()));

                for (var tc : msg.toolCalls()) {
                    BotToolResult result = botTools.execute(
                            tc.function().name(), tc.function().arguments(),
                            session.getUserEmail(), session);

                    // Apply session updates from tool result
                    if (result.bookingId() != null) session.setPendingBookingId(result.bookingId());
                    if (result.contractId() != null) session.setPendingContractId(result.contractId());

                    allMedia.addAll(result.media());
                    messages.add(GroqClient.toolResultMsg(tc.id(), result.text()));
                }
            } else {
                finalText = msg.content();
                messages.add(GroqClient.assistantMsg(finalText));
                break;
            }
        }

        if (finalText == null) finalText = "Done! Let me know if you need anything else.";

        // Persist updated message history (keep last 20 to avoid unbounded growth)
        List<Map<String, Object>> toStore = messages.size() > 20
                ? messages.subList(messages.size() - 20, messages.size())
                : messages;
        saveMessages(session, toStore);
        session.setUpdatedAt(ZonedDateTime.now());
        session.setExpiresAt(ZonedDateTime.now().plusHours(24));
        sessionRepository.save(session);

        return new BotResponse(finalText, allMedia);
    }

    // ── Unlinked user (no account found) ──────────────────────────────────────

    private BotResponse handleUnlinked(BotSession session, String text) {
        // Try to match the text as an email to link the account
        if (text.contains("@")) {
            var user = userRepository.findByEmailAddressIgnoreCase(text.trim());
            if (user.isPresent()) {
                session.setUserEmail(user.get().getEmailAddress());
                session.setUserId(user.get().getId());
                sessionRepository.save(session);
                return new BotResponse(
                        "Welcome back, " + user.get().getFirstName() + "! 👋\n" +
                        "I'm your Ramellian booking assistant. How can I help you today?\n\n" +
                        "You can say things like:\n" +
                        "• \"I need an SUV in Nairobi from tomorrow to Sunday\"\n" +
                        "• \"Show me chauffeured vehicles to JKIA\"", List.of());
            }
            return new BotResponse(
                    "I couldn't find an account with that email. Please check and try again, " +
                    "or visit " + frontendUrl + " to create an account.", List.of());
        }

        sessionRepository.save(session);
        return new BotResponse(
                "Hello! 👋 I'm your Ramellian Executive Travels booking assistant.\n\n" +
                "Please reply with your registered email address to get started.", List.of());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private BotSession loadOrCreate(String channelId, String channel) {
        return sessionRepository.findByChannelIdAndChannel(channelId, channel)
                .orElseGet(() -> {
                    var s = new BotSession();
                    s.setChannelId(channelId);
                    s.setChannel(channel);
                    return sessionRepository.save(s);
                });
    }

    private void tryLinkByPhone(BotSession session, String phone) {
        // Normalize: Baileys gives digits-only like 254758372954
        String normalized = "+" + phone.replaceAll("[^0-9]", "");
        userRepository.findByPhoneNumber(normalized).ifPresent(u -> {
            session.setUserEmail(u.getEmailAddress());
            session.setUserId(u.getId());
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadMessages(BotSession session) {
        try {
            return objectMapper.readValue(
                    session.getMessagesJson() != null ? session.getMessagesJson() : "[]",
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveMessages(BotSession session, List<Map<String, Object>> messages) {
        try {
            session.setMessagesJson(objectMapper.writeValueAsString(messages));
        } catch (Exception e) {
            log.error("Failed to serialize messages: {}", e.getMessage());
        }
    }

    private String systemPrompt() {
        return """
                You are Ramellian Assistant, the official AI booking agent for Ramellian Executive Travels \
                — a premium vehicle rental and chauffeur service operating in Kenya.

                Today: %s (East Africa Time). Currency: %s.

                Your job: help customers search vehicles, get quotes, confirm bookings, handle contracts, \
                and initiate payments. Be professional, warm, and concise — especially on WhatsApp where \
                messages should be short and clear.

                Booking flow (always follow this order):
                1. Collect: dates, location, mode (SELF_DRIVE or CHAUFFEURED)
                2. Call search_vehicles
                3. Customer picks a vehicle → call create_quote
                4. Customer confirms quote → call confirm_booking
                5. Automatically call draft_and_accept_contract with the booking_id
                6. Call initiate_payment to get and share the Pesapal payment link
                7. Receipt and signing link are sent automatically after payment

                Use tool results to craft natural responses. Never fabricate vehicle IDs or prices. \
                If you don't have required information, ask the customer for it first.
                """.formatted(ZonedDateTime.now().format(DATE_FMT), currency);
    }

    private String session(String rawPhone, Payment payment) {
        // Find contractId from session if available, otherwise return booking id as fallback path
        return sessionRepository.findByChannelIdAndChannel(rawPhone, "WHATSAPP")
                .map(s -> s.getPendingContractId() != null
                        ? s.getPendingContractId().toString()
                        : payment.getBooking().getId().toString())
                .orElse(payment.getBooking().getId().toString());
    }
}
