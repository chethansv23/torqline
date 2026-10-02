package com.torqline.repairorder.dto;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.repairorder.order.RepairOrder;
import com.torqline.repairorder.order.RepairOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepairOrderView(UUID id, String roNumber, UUID appointmentId, String dealerId,
                              RepairOrderStatus status, String customerName, String customerPhone,
                              VehicleType vehicleType, String vehicleNumber, String vehicleMake,
                              String vehicleModel, ServiceType serviceType, Integer odometerKm,
                              String technician, String note, List<PartLineView> parts,
                              BigDecimal labourAmount, BigDecimal partsAmount, BigDecimal taxAmount,
                              BigDecimal totalAmount, Instant openedAt, Instant closedAt) {

    public static RepairOrderView of(RepairOrder ro) {
        return new RepairOrderView(ro.getId(), ro.getRoNumber(), ro.getAppointmentId(), ro.getDealerId(),
                ro.getStatus(), ro.getCustomerName(), ro.getCustomerPhone(), ro.getVehicleType(),
                ro.getVehicleNumber(), ro.getVehicleMake(), ro.getVehicleModel(), ro.getServiceType(),
                ro.getOdometerKm(), ro.getTechnician(), ro.getNote(),
                ro.getPartLines().stream().map(l -> new PartLineView(l.getSku(), l.getName(), l.getQuantity(),
                        l.getUnitPrice(), l.getStatus())).toList(),
                ro.getLabourAmount(), ro.getPartsAmount(), ro.getTaxAmount(), ro.getTotalAmount(),
                ro.getOpenedAt(), ro.getClosedAt());
    }
}
