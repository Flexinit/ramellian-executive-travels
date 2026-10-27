package com.ramellian_executive.travels.service.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramellian_executive.travels.entity.BotSession;
import com.ramellian_executive.travels.model.BookingQuoteRequest;
import com.ramellian_executive.travels.model.BookingRequest;
import com.ramellian_executive.travels.model.BookingResponse;
import com.ramellian_executive.travels.model.ContractResponse;
import com.ramellian_executive.travels.model.VehicleResponse;
import com.ramellian_executive.travels.model.ContractAcceptanceRequest;
import com.ramellian_executive.travels.repository.vehicle.VehiclePhotoRepository;
import com.ramellian_executive.travels.service.BookingService;
import com.ramellian_executive.travels.service.PaymentService;
import com.ramellian_executive.travels.service.PesapalPaymentService;
import com.ramellian_executive.travels.service.vehicle.VehicleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ramellian_executive.travels.service.bot.GroqClient.*;

@Component
public class BotTools {

    private static final Logger log = LoggerFactory.getLogger(BotTools.class);

    private final VehicleService vehicleService;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final PesapalPaymentService pesapalPaymentService;
    private final VehiclePhotoRepository photoRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.server.url:http://localhost:8080}")
    private String serverUrl;

    @Value("${app.email.currency:KES}")
    private String currency;

    public BotTools(VehicleService vehicleService, BookingService bookingService,
                    PaymentService paymentService, PesapalPaymentService pesapalPaymentService,
                    VehiclePhotoRepository photoRepository, ObjectMapper objectMapper) {
        this.vehicleService = vehicleService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.pesapalPaymentService = pesapalPaymentService;
        this.photoRepository = photoRepository;
        this.objectMapper = objectMapper;
    }

    // ── Tool definitions (sent to Groq) ───────────────────────────────────────

    public List<Map<String, Object>> definitions() {
        return List.of(
                tool("search_vehicles",
                        "Search for available vehicles. Call this when the user wants to find a car.",
                        params(Map.of(
                                "location", strProp("Pick-up city or area, e.g. 'Nairobi CBD', 'Westlands'"),
                                "start_date", strProp("ISO 8601 datetime for pick-up, e.g. 2026-10-06T07:00:00+03:00"),
                                "end_date", strProp("ISO 8601 datetime for drop-off"),
                                "mode", enumProp("Rental mode", List.of("SELF_DRIVE", "CHAUFFEURED"))
                        ), List.of("start_date", "end_date"))),

                tool("create_quote",
                        "Get a price quote for a specific vehicle and dates before confirming the booking.",
                        params(Map.of(
                                "vehicle_id", strProp("UUID of the vehicle"),
                                "start_date", strProp("ISO 8601 pick-up datetime"),
                                "end_date", strProp("ISO 8601 drop-off datetime")
                        ), List.of("vehicle_id", "start_date", "end_date"))),

                tool("confirm_booking",
                        "Create a confirmed booking for the selected vehicle. Call only after the user has agreed to the quote.",
                        params(Map.of(
                                "vehicle_id", strProp("UUID of the vehicle"),
                                "start_date", strProp("ISO 8601 pick-up datetime"),
                                "end_date", strProp("ISO 8601 drop-off datetime"),
                                "mode", enumProp("Rental mode", List.of("SELF_DRIVE", "CHAUFFEURED")),
                                "pickup_location", strProp("Pick-up address or landmark"),
                                "dropoff_location", strProp("Drop-off address or landmark")
                        ), List.of("vehicle_id", "start_date", "end_date", "mode"))),

                tool("draft_and_accept_contract",
                        "Generates the rental contract for a booking and records the renter's acceptance. " +
                        "Call this automatically after confirm_booking so the customer can proceed to payment.",
                        params(Map.of(
                                "booking_id", strProp("UUID of the booking")
                        ), List.of("booking_id"))),

                tool("initiate_payment",
                        "Start the Pesapal payment for a booking. Returns the payment URL to share with the customer.",
                        params(Map.of(
                                "booking_id", strProp("UUID of the booking")
                        ), List.of("booking_id"))),

                tool("get_payment_status",
                        "Check the payment and booking status.",
                        params(Map.of(
                                "booking_id", strProp("UUID of the booking")
                        ), List.of("booking_id")))
        );
    }

    // ── Tool execution ─────────────────────────────────────────────────────────

    public BotToolResult execute(String toolName, String argsJson, String userEmail, BotSession session) {
        try {
            JsonNode args = objectMapper.readTree(argsJson);
            return switch (toolName) {
                case "search_vehicles"          -> searchVehicles(args);
                case "create_quote"             -> createQuote(args);
                case "confirm_booking"          -> confirmBooking(args, userEmail);
                case "draft_and_accept_contract" -> draftAndAcceptContract(args, userEmail);
                case "initiate_payment"         -> initiatePayment(args, userEmail);
                case "get_payment_status"       -> getPaymentStatus(args, userEmail);
                default -> BotToolResult.text("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.error("Tool {} failed: {}", toolName, e.getMessage(), e);
            return BotToolResult.text("Error: " + e.getMessage());
        }
    }

    // ── Tool implementations ───────────────────────────────────────────────────

    private BotToolResult searchVehicles(JsonNode args) {
        String location = text(args, "location");
        ZonedDateTime start = parseDate(args, "start_date");
        ZonedDateTime end = parseDate(args, "end_date");
        String mode = text(args, "mode");
        Boolean selfDrive = "SELF_DRIVE".equals(mode) ? true : null;
        Boolean chauffeured = "CHAUFFEURED".equals(mode) ? true : null;

        List<VehicleResponse> vehicles = vehicleService.search(
                null, location, null, null, null, null, null,
                selfDrive, chauffeured, null, null, start, end, "price_asc", 0, 20)
                .getContent();

        if (vehicles.isEmpty()) {
            return BotToolResult.text("No vehicles available for the requested dates and location.");
        }

        List<BotMedia> media = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        int idx = 1;
        for (VehicleResponse v : vehicles.stream().limit(5).toList()) {
            var photos = photoRepository.findByVehicleId(v.id());
            if (!photos.isEmpty()) {
                String imageUrl = serverUrl + photos.get(0).getUrl();
                String caption = buildVehicleCaption(idx, v);
                media.add(BotMedia.image(imageUrl, caption));
            } else {
                summary.append(buildVehicleCaption(idx, v)).append("\n\n");
            }
            idx++;
        }

        String resultText;
        try {
            resultText = objectMapper.writeValueAsString(
                    vehicles.stream().limit(5).map(v -> Map.of(
                            "id", v.id().toString(),
                            "model", v.model() != null ? v.model() : "",
                            "price_per_day", v.pricePerDay() != null ? v.pricePerDay() : 0,
                            "location", v.location() != null ? v.location() : "",
                            "self_drive", v.selfDrive(),
                            "chauffeured", v.chauffeured(),
                            "capacity", v.capacity(),
                            "ac", v.hasWorkingAC(),
                            "registration", v.registrationNumber() != null ? v.registrationNumber() : ""
                    )).toList()
            );
        } catch (Exception e) {
            resultText = "Found " + Math.min(vehicles.size(), 5) + " vehicles.";
        }

        return BotToolResult.withMedia(resultText, media);
    }

    private BotToolResult createQuote(JsonNode args) {
        UUID vehicleId = UUID.fromString(args.get("vehicle_id").asText());
        ZonedDateTime start = parseDate(args, "start_date");
        ZonedDateTime end = parseDate(args, "end_date");

        var quote = bookingService.quote(new BookingQuoteRequest(vehicleId, start, end, null));
        String text = String.format(
                "Quote: %d day%s | Rate: %s %,.2f/day | Base: %s %,.2f | Fee: %s %,.2f | *Total: %s %,.2f*",
                quote.numberOfDays(), quote.numberOfDays() == 1 ? "" : "s",
                currency, quote.pricePerDay().doubleValue(),
                currency, quote.baseAmount().doubleValue(),
                currency, quote.transactionFee().doubleValue(),
                currency, quote.totalAmount().doubleValue());
        return BotToolResult.text(text);
    }

    private BotToolResult confirmBooking(JsonNode args, String userEmail) {
        UUID vehicleId = UUID.fromString(args.get("vehicle_id").asText());
        ZonedDateTime start = parseDate(args, "start_date");
        ZonedDateTime end = parseDate(args, "end_date");
        String mode = text(args, "mode");
        String pickup = text(args, "pickup_location");
        String dropoff = text(args, "dropoff_location");

        BookingRequest request = new BookingRequest(
                vehicleId, start, end,
                mode != null ? mode : "SELF_DRIVE",
                null, null, null,
                pickup, dropoff, null, null);

        BookingResponse booking = bookingService.create(request, auth(userEmail));
        return BotToolResult.withBooking(
                "Booking created. ID: " + booking.id(),
                booking.id());
    }

    private BotToolResult draftAndAcceptContract(JsonNode args, String userEmail) {
        UUID bookingId = UUID.fromString(args.get("booking_id").asText());
        Authentication a = auth(userEmail);
        ContractResponse contract = paymentService.draft(bookingId, a);
        paymentService.accept(bookingId, new ContractAcceptanceRequest(true), a);
        String link = frontendUrl + "/contracts/" + contract.id() + "/sign";
        return BotToolResult.withContract(
                "Contract ready. Signing link: " + link,
                contract.id());
    }

    private BotToolResult initiatePayment(JsonNode args, String userEmail) {
        UUID bookingId = UUID.fromString(args.get("booking_id").asText());
        var checkout = pesapalPaymentService.checkout(bookingId, auth(userEmail));
        return BotToolResult.text(
                "Pay here: " + checkout.iframeUrl() + "\n(Link expires in 30 minutes)");
    }

    private BotToolResult getPaymentStatus(JsonNode args, String userEmail) {
        UUID bookingId = UUID.fromString(args.get("booking_id").asText());
        var status = pesapalPaymentService.status(bookingId, auth(userEmail));
        return BotToolResult.text(String.format(
                "Booking %s | Payment: %s | Amount: %s %,.2f",
                bookingId, status.status(), currency, status.amount() != null ? status.amount() : 0.0));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Authentication auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
    }

    private static String text(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private static ZonedDateTime parseDate(JsonNode node, String field) {
        String val = text(node, field);
        if (val == null) return null;
        try {
            return ZonedDateTime.parse(val);
        } catch (DateTimeParseException e) {
            return ZonedDateTime.parse(val + "+03:00");
        }
    }

    private String buildVehicleCaption(int idx, VehicleResponse v) {
        return String.format("%d. %s\n   %s %,.0f/day | %s | %d seats%s",
                idx,
                v.model() != null ? v.model() : "Vehicle",
                currency,
                v.pricePerDay() != null ? v.pricePerDay() : 0,
                v.chauffeured() ? "Chauffeur-driven" : "Self-drive",
                v.capacity(),
                v.hasWorkingAC() ? " | AC" : "");
    }
}
