package com.torqline.notification;

import com.torqline.common.events.AppointmentEvents.AppointmentBooked;
import com.torqline.common.events.AppointmentEvents.AppointmentCancelled;
import com.torqline.common.events.AppointmentEvents.AppointmentCheckedIn;
import com.torqline.common.events.InventoryEvents.PartLowStock;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCompleted;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCreated;
import com.torqline.common.messaging.InboundEvent;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Service
public class NotificationService {

    /** Service-manager inbox that receives stock alerts, e.g. manager+TQ-BLR-IND@torqline.dev */
    private static final String MANAGER_EMAIL = "manager+%s@torqline.dev";

    private record Outgoing(Channel channel, String recipient, String message) {
    }

    private final NotificationRepository notifications;
    private final NotificationSender sender;
    private final ObjectMapper mapper;

    public NotificationService(NotificationRepository notifications, NotificationSender sender, ObjectMapper mapper) {
        this.notifications = notifications;
        this.sender = sender;
        this.mapper = mapper;
    }

    /** Called inside the idempotency guard's transaction, so each event produces at most one record. */
    public void handle(InboundEvent event) {
        render(event).ifPresent(out -> {
            sender.send(out.channel(), out.recipient(), out.message());
            notifications.save(new Notification(event.eventId(), event.eventType(), out.channel(),
                    out.recipient(), out.message()));
        });
    }

    private Optional<Outgoing> render(InboundEvent event) {
        if (event.is(AppointmentBooked.class)) {
            var e = event.as(mapper, AppointmentBooked.class);
            return sms(e.customerPhone(), MessageTemplates.booked(e));
        }
        if (event.is(AppointmentCancelled.class)) {
            var e = event.as(mapper, AppointmentCancelled.class);
            return sms(e.customerPhone(), MessageTemplates.cancelled(e));
        }
        if (event.is(AppointmentCheckedIn.class)) {
            var e = event.as(mapper, AppointmentCheckedIn.class);
            return sms(e.customerPhone(), MessageTemplates.checkedIn(e));
        }
        if (event.is(RepairOrderCreated.class)) {
            var e = event.as(mapper, RepairOrderCreated.class);
            return sms(e.customerPhone(), MessageTemplates.roCreated(e));
        }
        if (event.is(RepairOrderCompleted.class)) {
            var e = event.as(mapper, RepairOrderCompleted.class);
            return sms(e.customerPhone(), MessageTemplates.roCompleted(e));
        }
        if (event.is(PartLowStock.class)) {
            var e = event.as(mapper, PartLowStock.class);
            return Optional.of(new Outgoing(Channel.EMAIL, MANAGER_EMAIL.formatted(e.dealerId()),
                    MessageTemplates.lowStock(e)));
        }
        return Optional.empty();
    }

    private static Optional<Outgoing> sms(String phone, String message) {
        return Optional.of(new Outgoing(Channel.SMS, phone, message));
    }
}
