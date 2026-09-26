package com.torqline.repairorder.order;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.events.AppointmentEvents.AppointmentCheckedIn;
import com.torqline.common.events.InventoryEvents.PartsReservationFailed;
import com.torqline.common.events.RepairOrderEvents.PartQuantity;
import com.torqline.common.events.RepairOrderEvents.PartsReservationRequested;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCancelled;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCompleted;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCreated;
import com.torqline.common.events.Topics;
import com.torqline.common.messaging.OutboxWriter;
import com.torqline.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairOrderServiceTest {

    @Mock RepairOrderRepository orders;
    @Mock OutboxWriter outbox;
    @Mock JdbcTemplate jdbc;
    @InjectMocks RepairOrderService service;

    private static AppointmentCheckedIn checkIn() {
        return new AppointmentCheckedIn(UUID.randomUUID(), "TQ-BLR-WHF", "Asha", "9845000001", VehicleType.CAR,
                "KA01MJ4321", "Hyundai", "Creta", ServiceType.WHEEL_ALIGNMENT, 42_000);
    }

    private static RepairOrder inProgress() {
        RepairOrder ro = RepairOrder.openFrom(checkIn(), "RO-2030-001001");
        ro.assign("Ravi K");
        return ro;
    }

    private Object lastEvent() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).append(eq(Topics.REPAIR_ORDER_EVENTS), eq("RepairOrder"), any(), event.capture());
        return event.getValue();
    }

    @Test
    void checkInOpensNumberedRepairOrderAndAnnouncesIt() {
        when(jdbc.queryForObject("select nextval('ro_number_seq')", Long.class)).thenReturn(1042L);
        when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.openFromCheckIn(checkIn());

        RepairOrderCreated created = (RepairOrderCreated) lastEvent();
        assertThat(created.roNumber()).isEqualTo("RO-" + Year.now().getValue() + "-001042");
        assertThat(created.vehicleNumber()).isEqualTo("KA01MJ4321");
    }

    @Test
    void secondCheckInForSameAppointmentDoesNotOpenAnotherOrder() {
        AppointmentCheckedIn event = checkIn();
        when(orders.existsByAppointmentId(event.appointmentId())).thenReturn(true);

        service.openFromCheckIn(event);

        verify(orders, never()).save(any());
        verify(outbox, never()).append(any(), any(), any(), any());
    }

    @Test
    void partsRequestMergesDuplicateSkusAndNormalisesThem() {
        RepairOrder ro = inProgress();
        when(orders.findById(ro.getId())).thenReturn(Optional.of(ro));

        service.requestParts(ro.getId(), List.of(new PartQuantity(" brake-pad-car-f ", 1),
                new PartQuantity("BRAKE-PAD-CAR-F", 1), new PartQuantity("wiper-blade-pair", 1)));

        PartsReservationRequested request = (PartsReservationRequested) lastEvent();
        assertThat(request.lines()).containsExactly(new PartQuantity("BRAKE-PAD-CAR-F", 2),
                new PartQuantity("WIPER-BLADE-PAIR", 1));
        assertThat(ro.getStatus()).isEqualTo(RepairOrderStatus.PARTS_PENDING);
        assertThat(ro.getPartLines()).hasSize(2);
    }

    @Test
    void completingPublishesTheInvoiceTotal() {
        RepairOrder ro = inProgress();
        when(orders.findById(ro.getId())).thenReturn(Optional.of(ro));

        service.complete(ro.getId());

        // Car wheel alignment: 60 min at Rs 800/h = 800, plus 18% GST.
        assertThat(((RepairOrderCompleted) lastEvent()).totalAmount()).isEqualByComparingTo("944");
    }

    @Test
    void cancellingPublishesCompensationForInventory() {
        RepairOrder ro = inProgress();
        when(orders.findById(ro.getId())).thenReturn(Optional.of(ro));

        service.cancel(ro.getId(), "Customer declined");

        assertThat(lastEvent()).isEqualTo(new RepairOrderCancelled(ro.getId(), "TQ-BLR-WHF", "Customer declined"));
    }

    @Test
    void failedReservationIsRecordedOnTheOrder() {
        RepairOrder ro = inProgress();
        UUID requestId = UUID.randomUUID();
        ro.requestParts(requestId, java.util.Map.of("AC-GAS-R134A", 5));
        when(orders.findById(ro.getId())).thenReturn(Optional.of(ro));

        service.onPartsReservationFailed(new PartsReservationFailed(requestId, ro.getId(), "AC-GAS-R134A needs 5, only 2 available"));

        assertThat(ro.getStatus()).isEqualTo(RepairOrderStatus.IN_PROGRESS);
        assertThat(ro.getNote()).contains("only 2 available");
    }

    @Test
    void unknownOrderIsNotFound() {
        UUID id = UUID.randomUUID();
        when(orders.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(id)).isInstanceOf(ApiException.class).hasMessageContaining("not found");
    }
}
