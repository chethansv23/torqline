package com.torqline.inventory.messaging;

import com.torqline.common.events.RepairOrderEvents.PartsReservationRequested;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCancelled;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCompleted;
import com.torqline.common.events.Topics;
import com.torqline.common.messaging.IdempotencyGuard;
import com.torqline.common.messaging.InboundEvent;
import com.torqline.inventory.part.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Repair-order events are keyed by repair-order id, so a request, completion and cancellation for
 * the same order land on one partition and are handled in the order they happened.
 */
@Component
public class InventoryEventListener {

    private final InventoryService inventory;
    private final IdempotencyGuard guard;
    private final ObjectMapper mapper;

    public InventoryEventListener(InventoryService inventory, IdempotencyGuard guard, ObjectMapper mapper) {
        this.inventory = inventory;
        this.guard = guard;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Topics.REPAIR_ORDER_EVENTS)
    public void onRepairOrderEvent(ConsumerRecord<String, String> record) {
        InboundEvent event = InboundEvent.from(record);
        if (event.is(PartsReservationRequested.class)) {
            guard.processOnce(event.eventId(), () -> inventory.reserve(event.as(mapper, PartsReservationRequested.class)));
        } else if (event.is(RepairOrderCompleted.class)) {
            guard.processOnce(event.eventId(),
                    () -> inventory.consumeFor(event.as(mapper, RepairOrderCompleted.class).repairOrderId()));
        } else if (event.is(RepairOrderCancelled.class)) {
            guard.processOnce(event.eventId(),
                    () -> inventory.releaseFor(event.as(mapper, RepairOrderCancelled.class).repairOrderId()));
        }
    }
}
