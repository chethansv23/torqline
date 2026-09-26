package com.torqline.notification;

import com.torqline.common.events.Topics;
import com.torqline.common.messaging.IdempotencyGuard;
import com.torqline.common.messaging.InboundEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

    private final NotificationService service;
    private final IdempotencyGuard guard;

    public NotificationListener(NotificationService service, IdempotencyGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @KafkaListener(topics = {Topics.APPOINTMENT_EVENTS, Topics.REPAIR_ORDER_EVENTS, Topics.INVENTORY_EVENTS})
    public void on(ConsumerRecord<String, String> record) {
        InboundEvent event = InboundEvent.from(record);
        guard.processOnce(event.eventId(), () -> service.handle(event));
    }
}
