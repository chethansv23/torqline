package com.torqline.repairorder.messaging;

import com.torqline.common.constants.Topics;
import com.torqline.common.events.AppointmentEvents.AppointmentCheckedIn;
import com.torqline.common.events.InventoryEvents.PartsReservationFailed;
import com.torqline.common.events.InventoryEvents.PartsReserved;
import com.torqline.common.messaging.IdempotencyGuard;
import com.torqline.common.messaging.InboundEvent;
import com.torqline.repairorder.order.RepairOrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RepairOrderEventListener {

    private final RepairOrderService service;
    private final IdempotencyGuard guard;
    private final ObjectMapper mapper;

    public RepairOrderEventListener(RepairOrderService service, IdempotencyGuard guard, ObjectMapper mapper) {
        this.service = service;
        this.guard = guard;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Topics.APPOINTMENT_EVENTS)
    public void onAppointmentEvent(ConsumerRecord<String, String> record) {
        InboundEvent event = InboundEvent.from(record);
        if (event.is(AppointmentCheckedIn.class)) {
            guard.processOnce(event.eventId(), () -> service.openFromCheckIn(event.as(mapper, AppointmentCheckedIn.class)));
        }
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS)
    public void onInventoryEvent(ConsumerRecord<String, String> record) {
        InboundEvent event = InboundEvent.from(record);
        if (event.is(PartsReserved.class)) {
            guard.processOnce(event.eventId(), () -> service.onPartsReserved(event.as(mapper, PartsReserved.class)));
        } else if (event.is(PartsReservationFailed.class)) {
            guard.processOnce(event.eventId(),
                    () -> service.onPartsReservationFailed(event.as(mapper, PartsReservationFailed.class)));
        }
    }
}
