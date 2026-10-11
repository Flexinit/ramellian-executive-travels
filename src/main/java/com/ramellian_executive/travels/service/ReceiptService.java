package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.Payment;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.repository.PaymentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a z");
    private static final float MARGIN = 56f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;

    private final PaymentRepository paymentRepository;
    private final Path receiptDirectory;

    @Value("${app.email.currency:KES}")
    private String currency;

    public ReceiptService(PaymentRepository paymentRepository,
                          @Value("${app.receipt.directory:uploads/receipts}") String receiptDir) {
        this.paymentRepository = paymentRepository;
        this.receiptDirectory = Paths.get(receiptDir).toAbsolutePath().normalize();
    }

    public Path receiptPath(Payment payment) {
        if (payment.getReceiptFileName() == null) return null;
        return receiptDirectory.resolve(payment.getReceiptFileName());
    }

    public Path adminReceiptPath(Payment payment) {
        if (payment.getAdminReceiptFileName() == null) return null;
        return receiptDirectory.resolve(payment.getAdminReceiptFileName());
    }

    public Payment generate(Payment payment) {
        try {
            Files.createDirectories(receiptDirectory);
            String filename = "RCPT-" + shortId(payment.getId().toString()) + ".pdf";
            Path dest = receiptDirectory.resolve(filename);
            buildPdf(payment, dest, false);
            payment.setReceiptFileName(filename);
            return paymentRepository.save(payment);
        } catch (IOException ex) {
            log.error("Failed to generate receipt for payment {}: {}", payment.getId(), ex.getMessage(), ex);
            return payment;
        }
    }

    public Payment generateAdminReceipt(Payment payment) {
        try {
            Files.createDirectories(receiptDirectory);
            String filename = "RCPT-ADMIN-" + shortId(payment.getId().toString()) + ".pdf";
            Path dest = receiptDirectory.resolve(filename);
            buildPdf(payment, dest, true);
            payment.setAdminReceiptFileName(filename);
            return paymentRepository.save(payment);
        } catch (IOException ex) {
            log.error("Failed to generate admin receipt for payment {}: {}", payment.getId(), ex.getMessage(), ex);
            return payment;
        }
    }

    private void buildPdf(Payment payment, Path dest, boolean adminView) throws IOException {
        Booking booking = payment.getBooking();
        Vehicle vehicle = booking.getVehicle();
        com.ramellian_executive.travels.entity.User renter = booking.getUser();

        String renterName = joinName(renter.getFirstName(), renter.getLastName());
        String vehicleTitle = vehicleTitle(vehicle);
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        String receiptNo = (adminView ? "ADMIN-RCPT-" : "RCPT-") + shortId(payment.getId().toString()).toUpperCase();
        String issuedAt = ZonedDateTime.now().format(DATE_FMT);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font oblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // ── Header background ──────────────────────────────────────
                cs.setNonStrokingColor(new Color(15, 23, 42));
                cs.addRect(0, PAGE_HEIGHT - 110, PAGE_WIDTH, 110);
                cs.fill();

                // Company name
                cs.setNonStrokingColor(Color.WHITE);
                cs.beginText();
                cs.setFont(bold, 16);
                cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 48);
                cs.showText("Ramellian Executive Travels");
                cs.setFont(regular, 9);
                cs.newLineAtOffset(0, -16);
                cs.setNonStrokingColor(new Color(148, 163, 184));
                cs.showText("PREMIUM VEHICLE RENTAL & CHAUFFEUR SERVICES");
                cs.endText();

                // RECEIPT label (right-aligned in header)
                cs.setNonStrokingColor(new Color(201, 168, 76));
                cs.beginText();
                cs.setFont(bold, adminView ? 16 : 22);
                cs.newLineAtOffset(PAGE_WIDTH - MARGIN - (adminView ? 140 : 100), PAGE_HEIGHT - 52);
                cs.showText(adminView ? "ADMIN RECEIPT" : "RECEIPT");
                cs.endText();

                // ── Receipt meta block ─────────────────────────────────────
                float y = PAGE_HEIGHT - 145;
                metaRow(cs, regular, bold, MARGIN, y, "Receipt No:", receiptNo);
                metaRow(cs, regular, bold, MARGIN, y - 18, "Date Issued:", issuedAt);
                metaRow(cs, regular, bold, MARGIN, y - 36, "Payment Status:", "");
                cs.setNonStrokingColor(new Color(21, 128, 61));
                cs.beginText();
                cs.setFont(bold, 10);
                cs.newLineAtOffset(MARGIN + 110, y - 36);
                cs.showText("PAID");
                cs.endText();

                // ── Divider ───────────────────────────────────────────────
                y -= 60;
                divider(cs, y);

                // ── Billed To ─────────────────────────────────────────────
                y -= 20;
                sectionLabel(cs, bold, MARGIN, y, "BILLED TO");
                y -= 18;
                textLine(cs, bold, 11, new Color(15, 23, 42), MARGIN, y, renterName);
                y -= 16;
                textLine(cs, regular, 10, new Color(71, 85, 105), MARGIN, y, renter.getEmailAddress());
                if (renter.getPhoneNumber() != null && !renter.getPhoneNumber().isBlank()) {
                    y -= 14;
                    textLine(cs, regular, 10, new Color(71, 85, 105), MARGIN, y, renter.getPhoneNumber());
                }

                // ── Divider ───────────────────────────────────────────────
                y -= 24;
                divider(cs, y);

                // ── Booking Details ───────────────────────────────────────
                y -= 20;
                sectionLabel(cs, bold, MARGIN, y, "BOOKING DETAILS");
                y -= 18;
                detailRow(cs, regular, bold, MARGIN, y, "Vehicle", vehicleTitle);
                y -= 16;
                detailRow(cs, regular, bold, MARGIN, y, "Pick-up", booking.getStartTime().format(DATE_FMT));
                y -= 16;
                detailRow(cs, regular, bold, MARGIN, y, "Drop-off", booking.getEndTime().format(DATE_FMT));
                y -= 16;
                detailRow(cs, regular, bold, MARGIN, y, "Duration", days + (days == 1 ? " day" : " days"));
                y -= 16;
                detailRow(cs, regular, bold, MARGIN, y, "Mode",
                        booking.getBookingMode().name().replace("_", " "));
                if (booking.getPickupLocation() != null && !booking.getPickupLocation().isBlank()) {
                    y -= 16;
                    detailRow(cs, regular, bold, MARGIN, y, "Pick-up Location", booking.getPickupLocation());
                }

                // ── Divider ───────────────────────────────────────────────
                y -= 24;
                divider(cs, y);

                // ── Charges ───────────────────────────────────────────────
                y -= 20;
                sectionLabel(cs, bold, MARGIN, y, adminView ? "FULL FINANCIAL BREAKDOWN" : "CHARGES");
                y -= 18;

                double deliveryFee = payment.getDeliveryFee() != null ? payment.getDeliveryFee() : 0.0;
                double driverCharge = payment.getDriverCharge() != null ? payment.getDriverCharge() : 0.0;
                double txFee = payment.getTransactionFee() != null ? payment.getTransactionFee() : 0.0;
                double platformFee = payment.getPlatformFee() != null ? payment.getPlatformFee() : 0.0;
                double base = payment.getAmount() - txFee - deliveryFee - driverCharge;
                double total = payment.getAmount();

                chargeRow(cs, regular, bold, MARGIN, y,
                        String.format("Rental (%d day%s @ %s %,.2f/day)", days, days == 1 ? "" : "s", currency,
                                vehicle.getPricePerDay() != null ? vehicle.getPricePerDay() : 0.0),
                        String.format("%s %,.2f", currency, base));
                y -= 16;
                if (deliveryFee > 0.0) {
                    chargeRow(cs, regular, bold, MARGIN, y,
                            "Car delivery fee",
                            String.format("%s %,.2f", currency, deliveryFee));
                    y -= 16;
                }
                if (driverCharge > 0.0) {
                    chargeRow(cs, regular, bold, MARGIN, y,
                            "Chauffeur charge",
                            String.format("%s %,.2f", currency, driverCharge));
                    y -= 16;
                }
                chargeRow(cs, regular, bold, MARGIN, y,
                        String.format("Pesapal transaction fee (%.1f%%)",
                                txFee > 0 && base > 0 ? txFee / base * 100 : 0.0),
                        String.format("%s %,.2f", currency, txFee));
                if (adminView) {
                    y -= 16;
                    chargeRow(cs, regular, bold, MARGIN, y,
                            String.format("Platform commission (%.1f%%)",
                                    platformFee > 0 && base > 0 ? platformFee / base * 100 : 0.0),
                            String.format("%s %,.2f", currency, platformFee));
                }

                // Total line
                y -= 10;
                cs.setNonStrokingColor(new Color(226, 232, 240));
                cs.addRect(MARGIN, y, CONTENT_WIDTH, 0.5f);
                cs.fill();
                y -= 20;

                // Total background highlight
                cs.setNonStrokingColor(new Color(240, 253, 244));
                cs.addRect(MARGIN, y - 6, CONTENT_WIDTH, 30);
                cs.fill();

                textLine(cs, bold, 13, new Color(21, 128, 61), MARGIN + 4, y + 10, "TOTAL PAID");
                amountRight(cs, bold, 14, new Color(21, 128, 61),
                        PAGE_WIDTH - MARGIN, y + 10, String.format("%s %,.2f", currency, total));

                if (adminView) {
                    y -= 28;
                    divider(cs, y + 6);
                    sectionLabel(cs, bold, MARGIN, y - 4, "INTERNAL SETTLEMENT");
                    y -= 22;
                    double ownerPayout = payment.getOwnerPayout() != null ? payment.getOwnerPayout() : 0.0;
                    chargeRow(cs, regular, bold, MARGIN, y,
                            "Owner payout (base - commission)",
                            String.format("%s %,.2f", currency, ownerPayout));
                    y -= 16;
                    chargeRow(cs, regular, bold, MARGIN, y,
                            "Ramellian revenue (commission + tx fee)",
                            String.format("%s %,.2f", currency, platformFee + txFee));
                    if (deliveryFee > 0.0) {
                        y -= 16;
                        chargeRow(cs, regular, bold, MARGIN, y,
                                "Delivery fee (retained by Ramellian)",
                                String.format("%s %,.2f", currency, deliveryFee));
                    }
                    if (driverCharge > 0.0) {
                        y -= 16;
                        chargeRow(cs, regular, bold, MARGIN, y,
                                "Chauffeur charge (retained by Ramellian)",
                                String.format("%s %,.2f", currency, driverCharge));
                    }
                }

                // Payment method & Pesapal references
                y -= 30;
                if (payment.getPaymentMethod() != null) {
                    detailRow(cs, regular, bold, MARGIN, y, "Payment Method", payment.getPaymentMethod());
                    y -= 16;
                }
                if (payment.getProviderOrderTrackingId() != null && !payment.getProviderOrderTrackingId().isBlank()) {
                    detailRow(cs, regular, bold, MARGIN, y, "Pesapal Ref", payment.getProviderOrderTrackingId());
                    y -= 16;
                }
                if (payment.getProviderReceipt() != null && !payment.getProviderReceipt().isBlank()) {
                    detailRow(cs, regular, bold, MARGIN, y, "Confirmation Code", payment.getProviderReceipt());
                    y -= 16;
                }
                if (payment.getProviderMerchantReference() != null && !payment.getProviderMerchantReference().isBlank()) {
                    detailRow(cs, regular, bold, MARGIN, y, "Merchant Ref", payment.getProviderMerchantReference());
                    y -= 16;
                }

                // ── Footer ────────────────────────────────────────────────
                y = 80;
                cs.setNonStrokingColor(new Color(241, 245, 249));
                cs.addRect(0, 0, PAGE_WIDTH, y + 10);
                cs.fill();

                divider(cs, y + 10);

                cs.setNonStrokingColor(new Color(148, 163, 184));
                cs.beginText();
                cs.setFont(oblique, 10);
                cs.newLineAtOffset(PAGE_WIDTH / 2f - 130, y - 8);
                cs.showText("Thank you for choosing Ramellian Executive Travels.");
                cs.endText();

                cs.setNonStrokingColor(new Color(203, 213, 225));
                cs.beginText();
                cs.setFont(regular, 8);
                cs.newLineAtOffset(PAGE_WIDTH / 2f - 80, y - 26);
                cs.showText("This is an official payment receipt. Please retain for your records.");
                cs.endText();
            }

            doc.save(dest.toFile());
        }
    }

    // ── Drawing helpers ──────────────────────────────────────────────────────

    private void metaRow(PDPageContentStream cs, PDType1Font regular, PDType1Font bold,
                         float x, float y, String label, String value) throws IOException {
        cs.setNonStrokingColor(new Color(100, 116, 139));
        cs.beginText();
        cs.setFont(regular, 9);
        cs.newLineAtOffset(x, y);
        cs.showText(label);
        cs.endText();

        if (value != null && !value.isBlank()) {
            cs.setNonStrokingColor(new Color(15, 23, 42));
            cs.beginText();
            cs.setFont(bold, 10);
            cs.newLineAtOffset(x + 110, y);
            cs.showText(value);
            cs.endText();
        }
    }

    private void sectionLabel(PDPageContentStream cs, PDType1Font bold,
                               float x, float y, String text) throws IOException {
        cs.setNonStrokingColor(new Color(100, 116, 139));
        cs.beginText();
        cs.setFont(bold, 8);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void detailRow(PDPageContentStream cs, PDType1Font regular, PDType1Font bold,
                            float x, float y, String label, String value) throws IOException {
        cs.setNonStrokingColor(new Color(100, 116, 139));
        cs.beginText();
        cs.setFont(regular, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(label + ":");
        cs.endText();

        cs.setNonStrokingColor(new Color(15, 23, 42));
        cs.beginText();
        cs.setFont(bold, 10);
        cs.newLineAtOffset(x + 130, y);
        cs.showText(value);
        cs.endText();
    }

    private void chargeRow(PDPageContentStream cs, PDType1Font regular, PDType1Font bold,
                            float x, float y, String label, String amount) throws IOException {
        cs.setNonStrokingColor(new Color(71, 85, 105));
        cs.beginText();
        cs.setFont(regular, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(label);
        cs.endText();

        amountRight(cs, bold, 10, new Color(15, 23, 42), PAGE_WIDTH - MARGIN, y, amount);
    }

    private void amountRight(PDPageContentStream cs, PDType1Font font, float size,
                              Color color, float rightEdge, float y, String text) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000f * size;
        cs.setNonStrokingColor(color);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(rightEdge - textWidth, y);
        cs.showText(text);
        cs.endText();
    }

    private void textLine(PDPageContentStream cs, PDType1Font font, float size,
                           Color color, float x, float y, String text) throws IOException {
        cs.setNonStrokingColor(color);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text != null ? text : "");
        cs.endText();
    }

    private void divider(PDPageContentStream cs, float y) throws IOException {
        cs.setStrokingColor(new Color(226, 232, 240));
        cs.moveTo(MARGIN, y);
        cs.lineTo(PAGE_WIDTH - MARGIN, y);
        cs.stroke();
    }

    private static String shortId(String uuid) {
        return uuid.replace("-", "").substring(0, 12);
    }

    private static String joinName(String first, String last) {
        String name = java.util.stream.Stream.of(first, last)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
        return name.isBlank() ? "Valued Customer" : name;
    }

    private static String vehicleTitle(Vehicle v) {
        if (v == null) return "Vehicle";
        String t = v.getModel() != null ? v.getModel() : "Vehicle";
        if (v.getRegistrationNumber() != null && !v.getRegistrationNumber().isBlank())
            t += " (" + v.getRegistrationNumber() + ")";
        return t;
    }
}
