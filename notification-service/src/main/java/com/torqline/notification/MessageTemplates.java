package com.torqline.notification;

import com.torqline.common.events.AppointmentEvents.AppointmentBooked;
import com.torqline.common.events.AppointmentEvents.AppointmentCancelled;
import com.torqline.common.events.AppointmentEvents.AppointmentCheckedIn;
import com.torqline.common.events.InventoryEvents.PartLowStock;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCompleted;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCreated;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Customer-facing copy. Times are shown in IST because all seeded dealers are in Bengaluru. */
final class MessageTemplates {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private MessageTemplates() {
    }

    static String booked(AppointmentBooked e) {
        return "Hi %s, your %s %s for %s is confirmed for %s. Ref %s."
                .formatted(e.customerName(), e.vehicleType().name().toLowerCase(), e.vehicleNumber(),
                        human(e.serviceType().name()), when(e.slotStart()), shortRef(e.appointmentId().toString()));
    }

    static String cancelled(AppointmentCancelled e) {
        return "Hi %s, your service appointment on %s has been cancelled (%s)."
                .formatted(e.customerName(), when(e.slotStart()), e.reason());
    }

    static String checkedIn(AppointmentCheckedIn e) {
        return "Hi %s, we have received %s. We'll message you when the job card is open."
                .formatted(e.customerName(), e.vehicleNumber());
    }

    static String roCreated(RepairOrderCreated e) {
        return "Job card %s opened for %s. Track progress with this number."
                .formatted(e.roNumber(), e.vehicleNumber());
    }

    static String roCompleted(RepairOrderCompleted e) {
        return "Good news %s! %s is ready for pickup. Invoice %s total Rs %s (incl. GST)."
                .formatted(e.customerName(), e.vehicleNumber(), e.roNumber(), e.totalAmount().toPlainString());
    }

    static String lowStock(PartLowStock e) {
        return "Low stock at %s: %s (%s) has %d available, reorder level %d."
                .formatted(e.dealerId(), e.name(), e.sku(), e.available(), e.reorderLevel());
    }

    private static String when(Instant instant) {
        return WHEN.format(instant);
    }

    private static String human(String enumName) {
        return enumName.replace('_', ' ').toLowerCase();
    }

    private static String shortRef(String id) {
        return id.substring(0, 8).toUpperCase();
    }
}
