package com.torqline.notification;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.events.AppointmentEvents.AppointmentBooked;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCompleted;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplatesTest {

    @Test
    void bookingConfirmationShowsLocalTimeAndVehicle() {
        var event = new AppointmentBooked(UUID.fromString("0a1b2c3d-0000-0000-0000-000000000000"), "TQ-BLR-IND",
                "Asha", "9845000001", VehicleType.BIKE, "KA03HB1234", ServiceType.CHAIN_SPROCKET,
                Instant.parse("2030-01-07T04:30:00Z"), Instant.parse("2030-01-07T05:30:00Z"));

        assertThat(MessageTemplates.booked(event))
                .isEqualTo("Hi Asha, your bike KA03HB1234 for chain sprocket is confirmed for Mon 7 Jan, 10:00 AM. Ref 0A1B2C3D.");
    }

    @Test
    void completionMessageIncludesInvoiceTotal() {
        var event = new RepairOrderCompleted(UUID.randomUUID(), "RO-2030-001001", "TQ-BLR-IND", "Vikram",
                "9845000002", "KA01MJ4321", new BigDecimal("4130.00"));

        assertThat(MessageTemplates.roCompleted(event)).contains("RO-2030-001001").contains("Rs 4130.00");
    }
}
