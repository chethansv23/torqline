package com.torqline.common.messaging;

import com.torqline.common.constants.EventHeaders;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCancelled;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundEventTest {

    @Test
    void decodesEnvelopeHeadersAndPayload() {
        UUID eventId = UUID.randomUUID();
        UUID ro = UUID.randomUUID();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("torqline.repair-order.events", 0, 7L, ro.toString(),
                "{\"repairOrderId\":\"" + ro + "\",\"dealerId\":\"TQ-BLR-IND\",\"reason\":\"Customer declined\"}");
        record.headers().add(EventHeaders.EVENT_ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.EVENT_TYPE, "RepairOrderCancelled".getBytes(StandardCharsets.UTF_8));

        InboundEvent event = InboundEvent.from(record);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.is(RepairOrderCancelled.class)).isTrue();
        assertThat(event.as(JsonMapper.builder().build(), RepairOrderCancelled.class))
                .isEqualTo(new RepairOrderCancelled(ro, "TQ-BLR-IND", "Customer declined"));
    }

    @Test
    void recordWithoutEnvelopeIsRejected() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("t", 0, 1L, "k", "{}");

        assertThatThrownBy(() -> InboundEvent.from(record))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventId");
    }
}
