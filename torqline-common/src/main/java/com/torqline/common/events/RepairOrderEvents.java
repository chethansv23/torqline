package com.torqline.common.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Events published by repair-order-service on {@link Topics#REPAIR_ORDER_EVENTS}, keyed by repair-order id. */
public final class RepairOrderEvents {

    public record RepairOrderCreated(
            UUID repairOrderId, String roNumber, UUID appointmentId, String dealerId,
            String customerName, String customerPhone, String vehicleNumber) {
    }

    public record PartQuantity(String sku, int quantity) {
    }

    /** Saga step 1: ask inventory to hold stock for a repair order. */
    public record PartsReservationRequested(
            UUID requestId, UUID repairOrderId, String dealerId, List<PartQuantity> lines) {
    }

    /** Tells inventory to convert the repair order's reservations into consumed stock. */
    public record RepairOrderCompleted(
            UUID repairOrderId, String roNumber, String dealerId, String customerName,
            String customerPhone, String vehicleNumber, BigDecimal totalAmount) {
    }

    /** Compensation: tells inventory to release anything reserved for the repair order. */
    public record RepairOrderCancelled(UUID repairOrderId, String dealerId, String reason) {
    }

    private RepairOrderEvents() {
    }
}
