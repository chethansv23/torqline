package com.torqline.notification;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.events.AppointmentEvents.AppointmentBooked;
import com.torqline.common.events.InventoryEvents.PartLowStock;
import com.torqline.common.events.InventoryEvents.PartsReserved;
import com.torqline.common.messaging.InboundEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notifications;
    @Mock NotificationSender sender;
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, sender, mapper);
    }

    private InboundEvent inbound(Object event) {
        return new InboundEvent(UUID.randomUUID(), event.getClass().getSimpleName(), mapper.writeValueAsString(event));
    }

    @Test
    void bookingSendsSmsToCustomerAndLogsIt() {
        var booked = new AppointmentBooked(UUID.randomUUID(), "TQ-BLR-IND", "Asha", "9845000001", VehicleType.BIKE,
                "KA03HB1234", ServiceType.OIL_CHANGE, Instant.parse("2030-01-07T04:30:00Z"), Instant.parse("2030-01-07T05:00:00Z"));
        InboundEvent event = inbound(booked);

        service.handle(event);

        verify(sender).send(eq(Channel.SMS), eq("9845000001"), contains("confirmed for Mon 7 Jan, 10:00 AM"));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getValue().getEventType()).isEqualTo("AppointmentBooked");
    }

    @Test
    void lowStockEmailsTheDealersServiceManager() {
        service.handle(inbound(new PartLowStock("TQ-BLR-WHF", "CHAIN-KIT", "Chain kit", 1, 2)));

        verify(sender).send(eq(Channel.EMAIL), eq("manager+TQ-BLR-WHF@torqline.dev"), contains("Chain kit (CHAIN-KIT) has 1 available"));
    }

    @Test
    void internalEventsDoNotNotifyAnyone() {
        service.handle(inbound(new PartsReserved(UUID.randomUUID(), UUID.randomUUID(), List.of())));

        verifyNoInteractions(sender, notifications);
    }
}
