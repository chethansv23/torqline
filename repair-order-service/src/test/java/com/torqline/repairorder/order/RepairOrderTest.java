package com.torqline.repairorder.order;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.events.AppointmentEvents.AppointmentCheckedIn;
import com.torqline.common.events.InventoryEvents.ReservedLine;
import com.torqline.common.web.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairOrderTest {

    private static RepairOrder bikeGeneralService() {
        return RepairOrder.openFrom(new AppointmentCheckedIn(UUID.randomUUID(), "TQ-BLR-WHF", "Asha",
                "9845000001", VehicleType.BIKE, "KA03HB1234", "Honda", "Activa 6G",
                ServiceType.GENERAL_SERVICE, 18_500), "RO-2026-001001");
    }

    @Test
    void labourIsPricedByVehicleTypeAndJobDuration() {
        // Car: 120 min at 800/h. Bike: 60 min at 400/h.
        assertThat(RepairOrder.labourFor(ServiceType.GENERAL_SERVICE, VehicleType.CAR)).isEqualByComparingTo("1600");
        assertThat(RepairOrder.labourFor(ServiceType.GENERAL_SERVICE, VehicleType.BIKE)).isEqualByComparingTo("400");
    }

    @Test
    void fullHappyPathComputesInvoiceWithGst() {
        RepairOrder ro = bikeGeneralService();
        ro.assign("Ravi");
        UUID request = UUID.randomUUID();
        ro.requestParts(request, Map.of("OIL-10W30-1L", 1, "SPARK-PLUG-BIKE", 1));

        ro.partsReserved(request, List.of(
                new ReservedLine("OIL-10W30-1L", "Engine oil", 1, new BigDecimal("450")),
                new ReservedLine("SPARK-PLUG-BIKE", "Spark plug", 1, new BigDecimal("150"))));
        ro.complete();

        assertThat(ro.getStatus()).isEqualTo(RepairOrderStatus.COMPLETED);
        assertThat(ro.getPartsAmount()).isEqualByComparingTo("600");
        assertThat(ro.getTaxAmount()).isEqualByComparingTo("180");   // 18% of (400 + 600)
        assertThat(ro.getTotalAmount()).isEqualByComparingTo("1180");
    }

    @Test
    void cannotCompleteWhileWaitingForParts() {
        RepairOrder ro = bikeGeneralService();
        ro.assign("Ravi");
        ro.requestParts(UUID.randomUUID(), Map.of("CHAIN-KIT", 1));

        assertThatThrownBy(ro::complete).isInstanceOf(ApiException.class)
                .hasMessageContaining("PARTS_PENDING to COMPLETED");
    }

    @Test
    void rejectedPartsReturnOrderToWorkWithoutCharging() {
        RepairOrder ro = bikeGeneralService();
        ro.assign("Ravi");
        UUID request = UUID.randomUUID();
        ro.requestParts(request, Map.of("CHAIN-KIT", 1));

        assertThat(ro.partsRejected(request, "Insufficient stock")).isTrue();
        ro.complete();

        assertThat(ro.getPartLines()).allMatch(l -> l.getStatus() == PartLineStatus.REJECTED);
        assertThat(ro.getPartsAmount()).isEqualByComparingTo("0");
    }

    @Test
    void lateInventoryReplyForCancelledOrderIsIgnored() {
        RepairOrder ro = bikeGeneralService();
        ro.assign("Ravi");
        UUID request = UUID.randomUUID();
        ro.requestParts(request, Map.of("CHAIN-KIT", 1));
        ro.cancel("Customer declined");

        assertThat(ro.partsReserved(request, List.of(new ReservedLine("CHAIN-KIT", "Chain kit", 1, BigDecimal.TEN))))
                .isFalse();
        assertThat(ro.getStatus()).isEqualTo(RepairOrderStatus.CANCELLED);
    }

    @Test
    void terminalStatesAllowNoTransitions() {
        assertThat(RepairOrderStatus.COMPLETED.next()).isEmpty();
        assertThat(RepairOrderStatus.CANCELLED.next()).isEmpty();
        assertThat(RepairOrderStatus.OPEN.canMoveTo(RepairOrderStatus.COMPLETED)).isFalse();
    }
}
