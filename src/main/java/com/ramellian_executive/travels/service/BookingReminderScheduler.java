package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingReminderScheduler.class);

    private final BookingRepository bookingRepository;
    private final VerificationNotificationService emailNotificationService;
    private final NotificationService appNotificationService;
    private final PlatformSettingsService platformSettingsService;

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    @Transactional
    public void sendPickupReminders() {
        // Read all settings in one cache hit
        var settings = platformSettingsService.get();
        if (!settings.pickupReminderEnabled()) return;

        int leadMinutes = settings.pickupReminderLeadMinutes();
        boolean notifyRenter = settings.pickupReminderRenterEnabled();
        boolean notifyOwner = settings.pickupReminderOwnerEnabled();
        if (!notifyRenter && !notifyOwner) return;

        ZonedDateTime now = ZonedDateTime.now();
        // ±7 min window around the configured lead time — safely caught within the 15-min run interval
        ZonedDateTime windowStart = now.plusMinutes(leadMinutes - 7);
        ZonedDateTime windowEnd = now.plusMinutes(leadMinutes + 7);

        List<Booking> due = bookingRepository.findConfirmedBookingsDueSoon(windowStart, windowEnd);
        if (due.isEmpty()) return;

        log.info("Sending pickup reminders for {} booking(s) (lead={}min, renter={}, owner={})",
                due.size(), leadMinutes, notifyRenter, notifyOwner);

        for (Booking booking : due) {
            try {
                if (notifyRenter && booking.getUser() != null) {
                    emailNotificationService.sendPickupReminder(booking);
                    String vehicleTitle = vehicleTitle(booking);
                    appNotificationService.createNotification(
                            booking.getUser(),
                            "Pickup Reminder",
                            "Your booking for " + vehicleTitle + " is due for pickup in ~" + leadMinutes + " minutes.",
                            "BOOKING_PICKUP_REMINDER",
                            booking.getId(),
                            "BOOKING"
                    );
                }

                if (notifyOwner && booking.getVehicle() != null && booking.getVehicle().getOwner() != null) {
                    emailNotificationService.sendPickupReminderToOwner(booking);
                    String vehicleTitle = vehicleTitle(booking);
                    appNotificationService.createNotification(
                            booking.getVehicle().getOwner(),
                            "Upcoming Pickup — " + vehicleTitle,
                            "A renter is due to pick up " + vehicleTitle + " in ~" + leadMinutes + " minutes. Please be ready.",
                            "BOOKING_PICKUP_REMINDER_OWNER",
                            booking.getId(),
                            "BOOKING"
                    );
                }

                booking.setPickupReminderSentAt(ZonedDateTime.now());
                bookingRepository.save(booking);
            } catch (Exception e) {
                log.error("Failed to send pickup reminder for booking {}", booking.getId(), e);
            }
        }
    }

    private static String vehicleTitle(Booking booking) {
        return booking.getVehicle() != null && booking.getVehicle().getModel() != null
                ? booking.getVehicle().getModel() : "your vehicle";
    }
}
