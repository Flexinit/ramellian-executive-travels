package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.DocumentReviewStatus;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserDocument;
import com.ramellian_executive.travels.entity.Vehicle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class EmailTemplateService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d yyyy • h:mm a z");

    @Value("${app.email.currency:KES}")
    private String currency;

    // ─── Public render methods ────────────────────────────────────────────────

    public String verificationCode(String recipientName, String code, String purpose) {
        String body = """
                <p class="greeting">Hi %s,</p>
                <p class="text">Use the one-time code below to complete your %s.</p>
                <div class="otp-box">
                  <span class="otp-code">%s</span>
                </div>
                <p class="hint">This code expires soon and can only be used once.</p>
                <p class="hint">If you did not request this, you can safely ignore this email.</p>
                """.formatted(recipientName, purpose.toLowerCase(), code);

        return layout(purpose + " Code", body, null, null);
    }

    public String bookingCreatedRenter(Booking booking, String vehicleImageUrl, java.util.List<String> warnings) {
        String vehicleTitle = vehicleTitle(booking.getVehicle());
        String warningsBlock = warningsBlock(warnings);
        String body = """
                <p class="greeting">Hi %s,</p>
                <p class="text">
                  Your booking for <strong>%s</strong> has been confirmed.
                  Your receipt is below.
                </p>
                %s
                %s
                %s
                <p class="hint">Questions? Simply reply to this email — we're happy to help.</p>
                """.formatted(
                firstName(booking.getUser()),
                vehicleTitle,
                imageBlock(vehicleImageUrl, vehicleTitle),
                bookingTable(booking),
                warningsBlock);

        return layout("Booking Confirmed", body, "View My Booking", null);
    }

    public String bookingCreatedOwner(Booking booking, String vehicleImageUrl, double commissionRatePct) {
        String vehicleTitle = vehicleTitle(booking.getVehicle());
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double base = booking.getVehicle() != null && booking.getVehicle().getPricePerDay() != null
                ? booking.getVehicle().getPricePerDay() * days : 0.0;
        double fee = base * (commissionRatePct / 100.0);
        double ownerPayout = base - fee;

        String earningsPanel = """
                <table width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 20px;">
                  <tr>
                    <td style="background:#f0fdf4;border:1px solid #86efac;border-radius:10px;padding:18px 20px;">
                      <p style="margin:0 0 12px;font-weight:700;color:#15803d;font-size:13px;
                                text-transform:uppercase;letter-spacing:0.5px;">Your Earnings</p>
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr>
                          <td style="color:#475569;font-size:13px;padding:4px 0;">Rental amount (%d day%s)</td>
                          <td style="color:#0f172a;font-weight:600;font-size:14px;text-align:right;">%s %,.2f</td>
                        </tr>
                        <tr>
                          <td style="color:#475569;font-size:13px;padding:4px 0;">Platform fee (%.0f%%)</td>
                          <td style="color:#ef4444;font-size:13px;text-align:right;">− %s %,.2f</td>
                        </tr>
                        <tr>
                          <td colspan="2" style="border-top:1px solid #bbf7d0;padding-top:10px;margin-top:6px;"></td>
                        </tr>
                        <tr>
                          <td style="color:#15803d;font-weight:700;font-size:15px;padding-top:4px;">Your payout</td>
                          <td style="color:#15803d;font-weight:700;font-size:18px;text-align:right;">%s %,.2f</td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                """.formatted(days, days == 1 ? "" : "s", currency, base, commissionRatePct, currency, fee, currency, ownerPayout);

        String body = """
                <p class="greeting">Hi %s,</p>
                <p class="text">
                  Great news — a customer has placed a booking on your vehicle
                  <strong>%s</strong>. Our team will review and process it shortly.
                  You will receive further updates as the booking progresses.
                </p>
                %s
                %s
                %s
                """.formatted(
                firstName(booking.getVehicle() != null ? booking.getVehicle().getOwner() : null),
                vehicleTitle,
                imageBlock(vehicleImageUrl, vehicleTitle),
                earningsPanel,
                bookingTable(booking));

        return layout("New Booking on Your Vehicle", body, "View My Bookings", null);
    }

    public String bookingCreatedAdmin(Booking booking, String vehicleImageUrl) {
        String vehicleTitle = vehicleTitle(booking.getVehicle());
        String renterName = fullName(booking.getUser());
        String renterEmail = booking.getRenterContactEmail() != null ? booking.getRenterContactEmail() : "—";
        String renterPhone = booking.getRenterContactPhone() != null ? booking.getRenterContactPhone() : "—";

        String contactPanel = """
                <table class="contact-panel" width="100%%" cellpadding="0" cellspacing="0">
                  <tr>
                    <td class="contact-panel-inner">
                      <p class="contact-title">Renter Contact Details</p>
                      <p class="contact-row"><span class="contact-label">Name</span>%s</p>
                      <p class="contact-row"><span class="contact-label">Email</span><a href="mailto:%s" class="link">%s</a></p>
                      <p class="contact-row last"><span class="contact-label">Phone</span>%s</p>
                    </td>
                  </tr>
                </table>
                """.formatted(renterName, renterEmail, renterEmail, renterPhone);

        String body = """
                <p class="greeting">Hi there,</p>
                <p class="text">
                  A new booking has been placed on <strong>%s</strong>.
                  Review the details below and take any necessary action.
                </p>
                %s
                %s
                %s
                """.formatted(vehicleTitle, imageBlock(vehicleImageUrl, vehicleTitle),
                bookingTable(booking), contactPanel);

        return layout("New Booking Alert", body, "Review Booking", null);
    }

    public String paymentReceipt(com.ramellian_executive.travels.entity.Payment payment) {
        com.ramellian_executive.travels.entity.Booking booking = payment.getBooking();
        com.ramellian_executive.travels.entity.Vehicle vehicle = booking.getVehicle();
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double txFee = payment.getTransactionFee() != null ? payment.getTransactionFee() : 0.0;
        double base = payment.getAmount() - txFee;
        String receiptNo = payment.getReceiptFileName() != null
                ? "RCPT-" + payment.getReceiptFileName().replace("RCPT-", "").replace(".pdf", "").toUpperCase()
                : payment.getId().toString().substring(0, 8).toUpperCase();
        String vehicleTitle = vehicleTitle(vehicle);

        String chargesTable = """
                <table width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 20px;">
                  <tr>
                    <td style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:18px 20px;">
                      <p style="margin:0 0 12px;font-weight:700;color:#64748b;font-size:11px;
                                text-transform:uppercase;letter-spacing:0.5px;">Payment Breakdown</p>
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr>
                          <td style="color:#475569;font-size:13px;padding:4px 0;">
                            Rental (%d day%s @ %s %,.2f/day)</td>
                          <td style="color:#0f172a;font-weight:600;font-size:14px;text-align:right;">
                            %s %,.2f</td>
                        </tr>
                        <tr>
                          <td style="color:#475569;font-size:13px;padding:4px 0;">
                            Transaction fee (1.5%%)</td>
                          <td style="color:#475569;font-size:13px;text-align:right;">
                            %s %,.2f</td>
                        </tr>
                        <tr>
                          <td colspan="2" style="border-top:1px solid #e2e8f0;padding-top:10px;"></td>
                        </tr>
                        <tr>
                          <td style="color:#15803d;font-weight:700;font-size:15px;padding-top:4px;">
                            Total Paid</td>
                          <td style="color:#15803d;font-weight:700;font-size:18px;text-align:right;">
                            %s %,.2f</td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                """.formatted(
                days, days == 1 ? "" : "s", currency,
                vehicle.getPricePerDay() != null ? vehicle.getPricePerDay() : 0.0,
                currency, base, currency, txFee, currency, payment.getAmount());

        String body = """
                <p class="greeting">Hi %s,</p>
                <p class="text">
                  Your payment for <strong>%s</strong> has been confirmed.
                  Your receipt is attached to this email as a PDF.
                </p>
                <table width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 16px;">
                  <tr>
                    <td style="background:#f0fdf4;border:1px solid #86efac;border-radius:8px;
                                padding:12px 16px;">
                      <span style="color:#15803d;font-weight:700;font-size:13px;">✓ PAID</span>
                      &nbsp;&nbsp;
                      <span style="color:#475569;font-size:12px;">Receipt No: <strong>%s</strong></span>
                    </td>
                  </tr>
                </table>
                %s
                <p class="hint">Your booking is now confirmed. Please review your booking details in the app.</p>
                """.formatted(
                firstName(booking.getUser()), vehicleTitle, receiptNo, chargesTable);

        return layout("Payment Receipt — " + vehicleTitle, body, "View My Booking", null);
    }

    public String documentReview(UserDocument document) {
        boolean approved = document.getReviewStatus() == DocumentReviewStatus.APPROVED;
        String statusLabel = approved ? "Approved" : "Rejected";
        String docType = document.getDocumentType() != null
                ? document.getDocumentType().name().replace("_", " ") : "Document";

        String badge = approved ? badge("approved", "✓  Approved") : badge("rejected", "✗  Rejected");
        String notes = notesBlock(document.getReviewNotes());
        String action = approved
                ? "<p class=\"text\">Your document is verified. You can now proceed with bookings on the platform.</p>"
                : "<p class=\"text\">Please review the notes above and upload a valid replacement document from your profile.</p>";

        String body = """
                <p class="greeting">Hi %s,</p>
                <p class="text">Your submitted <strong>%s</strong> has been reviewed by our team.</p>
                <div class="badge-row">Status &nbsp; %s</div>
                %s
                %s
                """.formatted(firstName(document.getUser()), docType, badge, notes, action);

        return layout("Document " + statusLabel, body, "View My Documents", null);
    }

    public String vehicleReview(Vehicle vehicle) {
        boolean approved = vehicle.getApprovalStatus() != null
                && "APPROVED".equalsIgnoreCase(vehicle.getApprovalStatus().name());
        String displayStatus = approved ? "Approved" : "Rejected";
        String vehicleTitle = vehicleTitle(vehicle);

        String badge = approved ? badge("approved", "✓  Approved") : badge("rejected", "✗  Rejected");
        String notes = notesBlock(vehicle.getApprovalNotes());
        String action = approved
                ? "<p class=\"text\">Your vehicle is now live and eligible for customer bookings on the platform.</p>"
                : "<p class=\"text\">Please review the notes and update your vehicle details or submit any required documents.</p>";

        String body = """
                <p class="greeting">Hi %s,</p>
                <p class="text">Your vehicle <strong>%s</strong> has been reviewed by our administration team.</p>
                <div class="badge-row">Approval Status &nbsp; %s</div>
                %s
                %s
                """.formatted(firstName(vehicle.getOwner()), vehicleTitle, badge, notes, action);

        return layout("Vehicle " + displayStatus, body, "View My Vehicles", null);
    }

    // ─── Master layout ────────────────────────────────────────────────────────

    /**
     * ctaPath is a logical path key resolved to a URL by the caller (VerificationNotificationService).
     * Passing null omits the CTA button.
     */
    String layout(String heading, String bodyContent, String ctaLabel, String ctaUrl) {
        // URL is resolved later by the sender via injectCtaUrl(); use placeholder when ctaLabel given.
        String resolvedUrl = ctaUrl != null ? ctaUrl : "{{CTA_URL}}";
        String cta = (ctaLabel != null)
                ? """
                  <div class="cta-wrapper">
                    <a href="%s" class="cta-btn">%s</a>
                  </div>
                  """.formatted(resolvedUrl, ctaLabel)
                : "";

        String headerContent = """
                  <p class="brand-name">Ramellian Executive Travels</p>
                  <p class="brand-tag">Premium Vehicle Rental &amp; Chauffeur Services</p>
                  """;

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>%s</title>
                  <style>
                    /* ── Reset ── */
                    body,table,td,a{-webkit-text-size-adjust:100%%;-ms-text-size-adjust:100%%}
                    table,td{mso-table-lspace:0pt;mso-table-rspace:0pt}
                    img{-ms-interpolation-mode:bicubic;border:0;outline:none;text-decoration:none}
                    body{margin:0;padding:0;background-color:#f1f5f9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif}

                    /* ── Wrapper ── */
                    .email-wrapper{background:#f1f5f9;padding:40px 16px}
                    .email-card{max-width:600px;margin:0 auto;background:#ffffff;border-radius:14px;
                      box-shadow:0 4px 24px rgba(0,0,0,0.07);overflow:hidden}

                    /* ── Header ── */
                    .email-header{background:#0f172a;padding:28px 36px}
                    .brand-name{margin:0;color:#f8fafc;font-size:19px;font-weight:700;letter-spacing:0.4px}
                    .brand-tag{margin:5px 0 0;color:#94a3b8;font-size:12px;letter-spacing:0.3px}

                    /* ── Body ── */
                    .email-body{padding:36px 36px 28px}
                    .email-heading{margin:0 0 22px;color:#0f172a;font-size:22px;font-weight:700;line-height:1.3}
                    .greeting{margin:0 0 14px;color:#0f172a;font-size:15px;font-weight:600}
                    .text{margin:0 0 16px;color:#475569;font-size:14px;line-height:1.7}
                    .hint{margin:0 0 8px;color:#94a3b8;font-size:12px;line-height:1.6}
                    .link{color:#2563eb;text-decoration:none}

                    /* ── OTP ── */
                    .otp-box{background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;
                      padding:28px 0;text-align:center;margin:20px 0}
                    .otp-code{font-size:36px;font-weight:700;letter-spacing:12px;color:#0f172a;font-family:monospace}

                    /* ── Booking table ── */
                    .booking-table{width:100%%;border-collapse:collapse;border:1px solid #e2e8f0;
                      border-radius:10px;overflow:hidden;margin:0 0 20px;font-size:13px}
                    .booking-table thead td{background:#f8fafc;padding:12px 16px;
                      font-weight:700;color:#64748b;text-transform:uppercase;letter-spacing:0.5px;
                      border-bottom:1px solid #e2e8f0;font-size:11px}
                    .booking-table tbody tr td{padding:11px 16px;border-bottom:1px solid #f1f5f9;
                      vertical-align:top;line-height:1.5}
                    .booking-table tbody tr:last-child td{border-bottom:none}
                    .td-label{color:#64748b;width:38%%;font-size:13px}
                    .td-value{color:#0f172a;font-weight:500;font-size:14px}
                    .status-pending{color:#d97706;font-weight:700}

                    /* ── Contact panel ── */
                    .contact-panel{margin:0 0 20px;border-radius:10px;overflow:hidden}
                    .contact-panel-inner{background:#fffbeb;border:1px solid #fde68a;
                      border-radius:10px;padding:16px 20px}
                    .contact-title{margin:0 0 12px;font-weight:700;color:#92400e;font-size:13px;
                      text-transform:uppercase;letter-spacing:0.5px}
                    .contact-row{margin:0 0 8px;color:#475569;font-size:14px;line-height:1.5}
                    .contact-row.last{margin:0}
                    .contact-label{display:inline-block;min-width:56px;font-weight:600;
                      color:#64748b;font-size:13px;margin-right:10px}

                    /* ── Badge ── */
                    .badge-row{margin:0 0 16px;color:#475569;font-size:14px}
                    .badge{display:inline-block;padding:4px 14px;border-radius:20px;
                      font-size:12px;font-weight:700;letter-spacing:0.3px}
                    .badge.approved{background:#dcfce7;color:#15803d}
                    .badge.rejected{background:#fee2e2;color:#b91c1c}

                    /* ── Warnings block ── */
                    .warnings-block{background:#fffbeb;border:1px solid #fde68a;border-radius:10px;
                      padding:14px 18px;margin:0 0 20px}
                    .warnings-title{margin:0 0 8px;font-weight:700;color:#92400e;font-size:12px;
                      text-transform:uppercase;letter-spacing:0.4px}
                    .warnings-item{margin:0 0 6px;color:#78350f;font-size:13px;line-height:1.6;
                      padding-left:14px;position:relative}
                    .warnings-item:last-child{margin:0}

                    /* ── Notes block ── */
                    .notes-block{background:#f8fafc;border-left:4px solid #cbd5e1;
                      padding:12px 16px;margin:0 0 16px;border-radius:0 8px 8px 0}
                    .notes-label{margin:0 0 4px;font-weight:700;color:#475569;font-size:12px;
                      text-transform:uppercase;letter-spacing:0.3px}
                    .notes-text{margin:0;color:#64748b;font-size:14px;line-height:1.6}

                    /* ── CTA ── */
                    .cta-wrapper{text-align:center;margin:28px 0 8px}
                    .cta-btn{display:inline-block;background:#0f172a;color:#ffffff;
                      font-size:14px;font-weight:600;text-decoration:none;
                      padding:14px 40px;border-radius:8px;letter-spacing:0.3px}

                    /* ── Footer ── */
                    .email-footer{background:#f8fafc;border-top:1px solid #e2e8f0;padding:20px 36px}
                    .footer-text{margin:0 0 4px;color:#94a3b8;font-size:11px;text-align:center;line-height:1.6}
                    .footer-sub{margin:0;color:#cbd5e1;font-size:10px;text-align:center}
                  </style>
                </head>
                <body>
                <div class="email-wrapper">
                  <div class="email-card">

                    <!-- Header -->
                    <div class="email-header">
                      %s
                    </div>

                    <!-- Body -->
                    <div class="email-body">
                      <h2 class="email-heading">%s</h2>
                      %s
                      %s
                    </div>

                    <!-- Footer -->
                    <div class="email-footer">
                      <p class="footer-text">© %d Ramellian Executive Travels. All rights reserved.</p>
                      <p class="footer-sub">You received this email because you have an account on our platform.</p>
                    </div>

                  </div>
                </div>
                </body>
                </html>
                """.formatted(
                heading,
                headerContent,
                heading,
                bodyContent,
                cta,
                java.time.Year.now().getValue());
    }

    // ─── Reusable components ─────────────────────────────────────────────────

    String warningsBlock(java.util.List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return "";
        StringBuilder items = new StringBuilder();
        for (String w : warnings) {
            items.append("<p class=\"warnings-item\">⚠️ ").append(w).append("</p>");
        }
        return """
                <div class="warnings-block">
                  <p class="warnings-title">Action Required Before Pickup</p>
                  %s
                </div>
                """.formatted(items);
    }

    String imageBlock(String imageUrl, String alt) {
        return "";
    }

    String bookingTable(Booking booking) {
        StringBuilder rows = new StringBuilder();
        row(rows, "Booking ID", booking.getId() != null ? booking.getId().toString() : "—");
        row(rows, "Vehicle",    vehicleTitle(booking.getVehicle()));
        if (booking.getStartTime() != null) row(rows, "Pick-up",   fmt(booking.getStartTime()));
        if (booking.getEndTime()   != null) row(rows, "Drop-off",  fmt(booking.getEndTime()));
        if (booking.getBookingMode() != null)
            row(rows, "Mode", booking.getBookingMode().name().replace("_", " "));
        if (booking.getTripType() != null)
            row(rows, "Trip Type", booking.getTripType().name().replace("_", " "));
        if (!blank(booking.getPickupLocation()))
            row(rows, "Pick-up Location",  booking.getPickupLocation());
        if (!blank(booking.getDropoffLocation()))
            row(rows, "Drop-off Location", booking.getDropoffLocation());
        if (!blank(booking.getRenterNotes()))
            row(rows, "Notes", booking.getRenterNotes());
        rows.append("""
                <tr>
                  <td class="td-label">Status</td>
                  <td class="td-value"><span class="status-pending">%s</span></td>
                </tr>
                """.formatted(booking.getStatus().name()));

        return """
                <table class="booking-table" cellpadding="0" cellspacing="0">
                  <thead>
                    <tr><td colspan="2">Booking Details</td></tr>
                  </thead>
                  <tbody>
                    %s
                  </tbody>
                </table>
                """.formatted(rows);
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append("""
                <tr>
                  <td class="td-label">%s</td>
                  <td class="td-value">%s</td>
                </tr>
                """.formatted(label, value));
    }

    private static String badge(String type, String text) {
        return "<span class=\"badge " + type + "\">" + text + "</span>";
    }

    private static String notesBlock(String notes) {
        if (blank(notes)) return "";
        return """
                <div class="notes-block">
                  <p class="notes-label">Review Notes</p>
                  <p class="notes-text">%s</p>
                </div>
                """.formatted(notes);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    public static String vehicleTitle(Vehicle vehicle) {
        if (vehicle == null) return "Vehicle";
        String title = vehicle.getModel() != null ? vehicle.getModel() : "Vehicle";
        if (!blank(vehicle.getRegistrationNumber()))
            title += " (" + vehicle.getRegistrationNumber() + ")";
        return title;
    }

    public static String firstName(User user) {
        return (user != null && !blank(user.getFirstName())) ? user.getFirstName() : "there";
    }

    static String fullName(User user) {
        if (user == null) return "—";
        String name = Stream.of(user.getFirstName(), user.getLastName())
                .filter(s -> !blank(s))
                .collect(Collectors.joining(" "));
        return blank(name) ? (user.getEmailAddress() != null ? user.getEmailAddress() : "—") : name;
    }

    static String fmt(ZonedDateTime dt) {
        return dt != null ? dt.format(DATE_FMT) : "—";
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
