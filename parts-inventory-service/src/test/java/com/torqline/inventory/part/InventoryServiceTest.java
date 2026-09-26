package com.torqline.inventory.part;

import com.torqline.common.events.InventoryEvents.PartLowStock;
import com.torqline.common.events.InventoryEvents.PartsReservationFailed;
import com.torqline.common.events.InventoryEvents.PartsReserved;
import com.torqline.common.events.RepairOrderEvents.PartQuantity;
import com.torqline.common.events.RepairOrderEvents.PartsReservationRequested;
import com.torqline.common.events.Topics;
import com.torqline.common.messaging.OutboxWriter;
import com.torqline.inventory.reservation.Reservation;
import com.torqline.inventory.reservation.ReservationLine;
import com.torqline.inventory.reservation.ReservationRepository;
import com.torqline.inventory.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final String DEALER = "TQ-BLR-IND";

    @Mock PartRepository parts;
    @Mock ReservationRepository reservations;
    @Mock OutboxWriter outbox;
    @InjectMocks InventoryService inventory;

    private static Part part(String sku, int onHand, int reorderLevel, String price) {
        Part part = BeanUtils.instantiateClass(Part.class);
        ReflectionTestUtils.setField(part, "dealerId", DEALER);
        ReflectionTestUtils.setField(part, "sku", sku);
        ReflectionTestUtils.setField(part, "name", sku.toLowerCase());
        ReflectionTestUtils.setField(part, "onHand", onHand);
        ReflectionTestUtils.setField(part, "reorderLevel", reorderLevel);
        ReflectionTestUtils.setField(part, "unitPrice", new BigDecimal(price));
        return part;
    }

    private static PartsReservationRequested request(PartQuantity... lines) {
        return new PartsReservationRequested(UUID.randomUUID(), UUID.randomUUID(), DEALER, List.of(lines));
    }

    @Test
    void reservesEveryLineAndReportsPrices() {
        Part oil = part("OIL-10W30-1L", 8, 2, "450");
        Part plug = part("SPARK-PLUG-BIKE", 8, 2, "150");
        when(parts.lockForUpdate(eq(DEALER), anyCollection())).thenReturn(List.of(oil, plug));
        var request = request(new PartQuantity("OIL-10W30-1L", 1), new PartQuantity("SPARK-PLUG-BIKE", 2));

        inventory.reserve(request);

        assertThat(oil.getReserved()).isEqualTo(1);
        assertThat(plug.getReserved()).isEqualTo(2);
        verify(reservations).save(any(Reservation.class));
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).append(eq(Topics.INVENTORY_EVENTS), eq("Reservation"), eq(request.repairOrderId()), event.capture());
        PartsReserved reserved = (PartsReserved) event.getValue();
        assertThat(reserved.lines()).extracting(l -> l.sku() + "@" + l.unitPrice())
                .containsExactlyInAnyOrder("OIL-10W30-1L@450", "SPARK-PLUG-BIKE@150");
    }

    @Test
    void anyShortageRejectsTheWholeRequestAndReservesNothing() {
        Part oil = part("OIL-10W30-1L", 8, 2, "450");
        Part chain = part("CHAIN-KIT", 1, 2, "2200");
        when(parts.lockForUpdate(eq(DEALER), anyCollection())).thenReturn(List.of(chain, oil));

        inventory.reserve(request(new PartQuantity("OIL-10W30-1L", 1), new PartQuantity("CHAIN-KIT", 2)));

        assertThat(oil.getReserved()).isZero();
        assertThat(chain.getReserved()).isZero();
        verify(reservations, never()).save(any());
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).append(eq(Topics.INVENTORY_EVENTS), eq("Reservation"), any(), event.capture());
        assertThat(((PartsReservationFailed) event.getValue()).reason()).isEqualTo("CHAIN-KIT needs 2, only 1 available");
    }

    @Test
    void unknownSkuIsReportedAsNotStocked() {
        when(parts.lockForUpdate(eq(DEALER), anyCollection())).thenReturn(List.of());

        inventory.reserve(request(new PartQuantity("TURBO-KIT", 1)));

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).append(any(), any(), any(), event.capture());
        assertThat(((PartsReservationFailed) event.getValue()).reason()).isEqualTo("TURBO-KIT is not stocked at " + DEALER);
    }

    @Test
    void duplicateLinesForTheSameSkuAreAddedUp() {
        Part chain = part("CHAIN-KIT", 3, 1, "2200");
        when(parts.lockForUpdate(eq(DEALER), anyCollection())).thenReturn(List.of(chain));

        inventory.reserve(request(new PartQuantity("CHAIN-KIT", 2), new PartQuantity("CHAIN-KIT", 2)));

        assertThat(chain.getReserved()).isZero(); // 4 requested in total, only 3 on hand
    }

    @Test
    void redeliveredRequestIsIgnored() {
        var request = request(new PartQuantity("CHAIN-KIT", 1));
        when(reservations.existsByRequestId(request.requestId())).thenReturn(true);

        inventory.reserve(request);

        verifyNoInteractions(parts, outbox);
    }

    @Test
    void completingConsumesStockAndRaisesLowStockAlert() {
        UUID ro = UUID.randomUUID();
        Part plug = part("SPARK-PLUG-BIKE", 3, 2, "150");
        plug.reserve(1);
        Reservation reservation = new Reservation(UUID.randomUUID(), ro, DEALER,
                List.of(new ReservationLine("SPARK-PLUG-BIKE", 1, new BigDecimal("150"))));
        when(reservations.findByRepairOrderIdAndStatus(ro, ReservationStatus.RESERVED)).thenReturn(List.of(reservation));
        when(parts.lockForUpdate(eq(DEALER), anyCollection())).thenReturn(List.of(plug));

        inventory.consumeFor(ro);

        assertThat(plug.getOnHand()).isEqualTo(2);
        assertThat(plug.getReserved()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONSUMED);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).append(eq(Topics.INVENTORY_EVENTS), eq("Part"), eq(DEALER + ":SPARK-PLUG-BIKE"), event.capture());
        assertThat(event.getValue()).isEqualTo(new PartLowStock(DEALER, "SPARK-PLUG-BIKE", "spark-plug-bike", 2, 2));
    }

    @Test
    void cancellingReleasesStockWithoutAlerts() {
        UUID ro = UUID.randomUUID();
        Part chain = part("CHAIN-KIT", 1, 2, "2200");
        chain.reserve(1);
        Reservation reservation = new Reservation(UUID.randomUUID(), ro, DEALER,
                List.of(new ReservationLine("CHAIN-KIT", 1, new BigDecimal("2200"))));
        when(reservations.findByRepairOrderIdAndStatus(ro, ReservationStatus.RESERVED)).thenReturn(List.of(reservation));
        when(parts.lockForUpdate(eq(DEALER), anyCollection())).thenReturn(List.of(chain));

        inventory.releaseFor(ro);

        assertThat(chain.getOnHand()).isEqualTo(1);
        assertThat(chain.available()).isEqualTo(1);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verifyNoInteractions(outbox);
    }
}
